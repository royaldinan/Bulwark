package id.bulwark.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import id.bulwark.MainActivity
import id.bulwark.R
import id.bulwark.data.AppDatabase
import id.bulwark.data.BulwarkRepository
import id.bulwark.data.ProtectionState
import id.bulwark.data.model.DomainDecision
import id.bulwark.data.model.LinkAction
import id.bulwark.net.MediationApi
import id.bulwark.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * DNS-only local connection filter.
 *
 * PRD "Keputusan teknis" 2: the tunnel only routes the virtual DNS address. Blocked domains are
 * answered NXDOMAIN; every other query is forwarded to the real resolver through a `protect()`ed
 * socket. There is no full TCP forwarding on purpose.
 *
 * Reliability rules for this path:
 * - A verdict lookup must never stall DNS: wait at most [VERDICT_TIMEOUT_MILLIS], then treat as
 *   unknown and forward. The lookup keeps running in the background to fill the cache.
 * - Forwarding uses a short timeout, one retry with a backup upstream, and always answers SERVFAIL
 *   when it fails (never silently drops the query).
 * - Block side effects (link_event + overlay) are deduped per domain via [BlockGate]; a temporary
 *   in-memory allowlist ([DomainAllowList]) backs "Lanjutkan dengan risiko sendiri".
 */
class BulwarkVpnService : VpnService() {

    @Volatile
    private var running = false

    private var tunnel: ParcelFileDescriptor? = null
    private var worker: Thread? = null

    private lateinit var repository: BulwarkRepository
    private lateinit var overlay: OverlayController
    private lateinit var processor: DnsQueryProcessor

    private val allowList = DomainAllowList(ALLOWLIST_TTL_MILLIS)
    private val blockGate = BlockGate(BLOCK_COOLDOWN_MILLIS)
    private val inFlightVerdicts = ConcurrentHashMap<String, CompletableFuture<DomainDecision>>()
    private val verdictExecutor = Executors.newFixedThreadPool(VERDICT_THREADS)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        repository = BulwarkRepository(AppDatabase.get(this), gateway = MediationApi())
        overlay = OverlayController(this)
        processor = DnsQueryProcessor(
            resolveVerdict = { domain -> resolveVerdict(domain) },
            forward = { query -> forwardDns(query) },
            allowList = allowList,
            blockGate = blockGate,
            onBlock = { decision -> handleBlock(decision) },
            now = System::currentTimeMillis,
            log = { message -> Log.i(DNS_TAG, message) },
        )
        Log.i(
            DNS_TAG,
            "service created upstreams=$UPSTREAM_DNS_SERVERS verdictTimeoutMs=$VERDICT_TIMEOUT_MILLIS " +
                "forwardTimeoutMs=$FORWARD_TIMEOUT_MILLIS blockCooldownMs=$BLOCK_COOLDOWN_MILLIS " +
                "allowlistTtlMs=$ALLOWLIST_TTL_MILLIS",
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startTunnel()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        stopTunnel()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel()
        serviceScope.cancel()
        verdictExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun startTunnel() {
        if (running) return

        val builder = Builder()
            .setSession("Bulwark")
            .addAddress(TUN_ADDRESS, TUN_PREFIX)
            .addDnsServer(VIRTUAL_DNS)
            .addRoute(VIRTUAL_DNS, 32)
            .setMtu(TUN_MTU)
            .setBlocking(true)

        // Keep Bulwark's own API/registry traffic out of the tunnel (PRD "Keputusan teknis" 3).
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "own package not found for tunnel exclusion", e)
        }

        val descriptor = try {
            builder.establish()
        } catch (e: Exception) {
            Log.w(TAG, "establish failed", e)
            null
        }
        if (descriptor == null) {
            Log.w(DNS_TAG, "tunnel establish returned null")
            stopSelf()
            return
        }

        tunnel = descriptor
        running = true
        ProtectionState.set(true)
        startForegroundNotification()
        Log.i(DNS_TAG, "tunnel established dns=$VIRTUAL_DNS route=$VIRTUAL_DNS/32")
        worker = Thread({ runTunnel(descriptor) }, "bulwark-tunnel").also { it.start() }
    }

    private fun stopTunnel() {
        running = false
        ProtectionState.set(false)
        overlay.dismiss("service_stop")
        blockGate.onServiceStopped()
        allowList.clear()
        tunnel?.let { runCatching { it.close() } }
        tunnel = null
        worker?.interrupt()
        worker = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        Log.i(DNS_TAG, "tunnel stopped")
    }

    private fun runTunnel(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(PACKET_BUFFER_SIZE)
        try {
            while (running) {
                val length = input.read(buffer)
                if (length <= 0) continue
                runCatching { handlePacket(buffer, length, output) }
                    .onFailure { Log.w(DNS_TAG, "packet handling error: ${it.javaClass.simpleName}: ${it.message}") }
            }
        } catch (e: Exception) {
            if (running) Log.w(DNS_TAG, "tunnel loop stopped: ${e.message}")
        } finally {
            runCatching { output.close() }
            runCatching { input.close() }
        }
    }

    private fun handlePacket(buffer: ByteArray, length: Int, output: FileOutputStream) {
        val started = SystemClock.elapsedRealtime()
        val packet = Ipv4Udp.parse(buffer, length) ?: return
        if (packet.destPort != DNS_PORT) return

        val query = DnsParser.parseQuery(packet.payload)
        if (query == null) {
            Log.w(DNS_TAG, "malformed DNS payload dropped bytes=$length")
            return
        }
        val type = queryTypeName(query.qType)
        Log.i(DNS_TAG, "query domain=${query.domain} type=$type")

        val response = processor.process(packet.payload)
        if (response != null) {
            runCatching { output.write(Ipv4Udp.buildResponse(packet, response)) }
                .onFailure { Log.w(DNS_TAG, "write failed domain=${query.domain}: ${it.message}") }
        }
        Log.i(DNS_TAG, "done domain=${query.domain} type=$type durationMs=${SystemClock.elapsedRealtime() - started}")
    }

    /**
     * Block side effects, driven by the single [BlockGate] decision:
     * the event (counter) is written only after the overlay is really on screen, so one gate
     * decision always means exactly one overlay + one event + one counter increment.
     */
    private fun handleBlock(decision: DomainDecision) {
        Log.i(DNS_TAG, "overlay requested domain=${decision.domain}")
        overlay.show(
            domain = decision.domain,
            onShown = {
                serviceScope.launch {
                    runCatching { repository.recordLinkEvent(decision, LinkAction.BLOCKED) }
                        .onSuccess {
                            Log.i(
                                DNS_TAG,
                                "event saved domain=${decision.domain} action=blocked source=${decision.source}",
                            )
                        }
                        .onFailure {
                            Log.w(DNS_TAG, "event save failed domain=${decision.domain}: ${it.message}")
                        }
                }
            },
            onStaySafe = {
                Log.i(DNS_TAG, "action=stay_safe domain=${decision.domain} -> beranda")
                openBeranda()
            },
            onProceed = {
                allowList.allow(decision.domainHash, System.currentTimeMillis())
                Log.i(
                    DNS_TAG,
                    "allowlist added domain=${decision.domain} ttl=${ALLOWLIST_TTL_MILLIS / 1000}s",
                )
            },
            onClosed = { reason ->
                when (reason) {
                    "permission", "add_failed" -> blockGate.cancelWarning(decision.domainHash)
                    else -> blockGate.onWarningClosed(decision.domainHash, System.currentTimeMillis())
                }
            },
        )
    }

    /** "Kembali ke aman" / Back: close the warning and show the Bulwark home screen. */
    private fun openBeranda() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(intent) }
            .onFailure { Log.w(DNS_TAG, "open beranda failed: ${it.message}") }
    }

    /**
     * Resolve a verdict without letting a slow API stall DNS. Waits at most
     * [VERDICT_TIMEOUT_MILLIS]; the background lookup keeps running (de-duplicated per domain) so
     * the cache warms up for the next query.
     */
    private fun resolveVerdict(domain: String): DomainDecision? {
        val started = SystemClock.elapsedRealtime()
        val future = inFlightVerdicts.computeIfAbsent(domain) { key ->
            CompletableFuture.supplyAsync({
                try {
                    runBlocking { repository.checkDomain(key) }
                } finally {
                    inFlightVerdicts.remove(key)
                }
            }, verdictExecutor)
        }

        return try {
            val decision = future.get(VERDICT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            Log.i(
                DNS_TAG,
                "verdict domain=$domain verdict=${decision.verdict} source=${decision.source} " +
                    "cacheHit=${!decision.fresh} ms=${SystemClock.elapsedRealtime() - started}",
            )
            decision
        } catch (e: TimeoutException) {
            Log.w(
                DNS_TAG,
                "verdict timeout domain=$domain after ${VERDICT_TIMEOUT_MILLIS}ms -> unknown (forward)",
            )
            null
        } catch (e: Exception) {
            Log.w(DNS_TAG, "verdict error domain=$domain ${e.javaClass.simpleName}: ${e.message} -> unknown")
            null
        }
    }

    /** Forward a DNS query, retrying once against a backup upstream if the primary fails. */
    private fun forwardDns(query: ByteArray): ByteArray? {
        val started = SystemClock.elapsedRealtime()
        for ((attempt, upstream) in UPSTREAM_DNS_SERVERS.withIndex()) {
            val answer = forwardOnce(query, upstream)
            if (answer != null) {
                Log.i(
                    DNS_TAG,
                    "forward ok upstream=$upstream attempt=${attempt + 1} ms=${SystemClock.elapsedRealtime() - started}",
                )
                return answer
            }
            Log.w(DNS_TAG, "forward attempt failed upstream=$upstream attempt=${attempt + 1}")
        }
        Log.w(DNS_TAG, "forward failed allUpstreams ms=${SystemClock.elapsedRealtime() - started}")
        return null
    }

    /** One UDP attempt to [upstream]; the socket is protect()ed so it bypasses our own tunnel. */
    private fun forwardOnce(query: ByteArray, upstream: String): ByteArray? {
        val socket = DatagramSocket()
        if (!protect(socket)) {
            socket.close()
            Log.w(DNS_TAG, "forward protect() failed upstream=$upstream")
            return null
        }
        return try {
            socket.soTimeout = FORWARD_TIMEOUT_MILLIS
            socket.send(DatagramPacket(query, query.size, InetSocketAddress(upstream, DNS_PORT)))
            val buffer = ByteArray(DNS_RESPONSE_BUFFER_SIZE)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            if (response.length < DNS_HEADER_MIN || !sameTransactionId(query, buffer, response.length)) {
                Log.w(DNS_TAG, "forward bad response upstream=$upstream length=${response.length}")
                null
            } else {
                buffer.copyOf(response.length)
            }
        } catch (e: SocketTimeoutException) {
            Log.w(DNS_TAG, "forward timeout upstream=$upstream ms=$FORWARD_TIMEOUT_MILLIS")
            null
        } catch (e: Exception) {
            Log.w(DNS_TAG, "forward error upstream=$upstream ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun sameTransactionId(query: ByteArray, response: ByteArray, length: Int): Boolean =
        length >= DNS_HEADER_MIN && query.size >= 2 && query[0] == response[0] && query[1] == response[1]

    private fun queryTypeName(qType: Int): String = when (qType) {
        1 -> "A"
        2 -> "NS"
        5 -> "CNAME"
        6 -> "SOA"
        12 -> "PTR"
        15 -> "MX"
        16 -> "TXT"
        28 -> "AAAA"
        33 -> "SRV"
        64 -> "SVCB"
        65 -> "HTTPS"
        else -> "TYPE_$qType"
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_stat_bulwark)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_START = "id.bulwark.vpn.START"
        const val ACTION_STOP = "id.bulwark.vpn.STOP"

        private const val TAG = "BulwarkVpnService"
        private const val DNS_TAG = "BulwarkDns"

        // Virtual endpoints kept inside the tunnel.
        private const val TUN_ADDRESS = "10.111.222.1"
        private const val TUN_PREFIX = 32
        private const val VIRTUAL_DNS = "10.111.222.2"
        private const val TUN_MTU = 1500

        // VERIFY: fixed resolvers. Prefer reading the underlying network's DNS from
        // ConnectivityManager; fixed resolvers are used only because the tunnel must not recurse.
        private val UPSTREAM_DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8")

        private const val DNS_PORT = 53
        private const val DNS_HEADER_MIN = 12

        private const val FORWARD_TIMEOUT_MILLIS = 2_000
        private const val VERDICT_TIMEOUT_MILLIS = 1_000L
        private const val VERDICT_THREADS = 4
        private const val PACKET_BUFFER_SIZE = 32_767
        private const val DNS_RESPONSE_BUFFER_SIZE = 4_096

        private const val BLOCK_COOLDOWN_MILLIS = 15_000L
        private const val ALLOWLIST_TTL_MILLIS = 300_000L
        private const val CHANNEL_ID = "bulwark_protection"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startService(Intent(context, BulwarkVpnService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BulwarkVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
