package id.bulwark.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import id.bulwark.R
import id.bulwark.data.AppDatabase
import id.bulwark.data.BulwarkRepository
import id.bulwark.data.model.DomainDecision
import id.bulwark.data.model.LinkAction
import id.bulwark.data.model.Verdict
import id.bulwark.data.model.VerdictSource
import id.bulwark.net.MediationApi
import id.bulwark.overlay.OverlayController
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * DNS-only local connection filter.
 *
 * PRD "Keputusan teknis" 2: the tunnel only routes the virtual DNS address. Blocked domains are
 * answered NXDOMAIN; every other query is forwarded to the real resolver through a `protect()`ed
 * socket. There is no full TCP forwarding on purpose. This is what lets Bulwark stop a scam link
 * before the TCP handshake without a remote VPN server.
 */
class BulwarkVpnService : VpnService() {

    @Volatile
    private var running = false

    private var tunnel: ParcelFileDescriptor? = null
    private var worker: Thread? = null

    private lateinit var repository: BulwarkRepository
    private lateinit var overlay: OverlayController

    private val lastBlockedAt = HashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        repository = BulwarkRepository(AppDatabase.get(this), gateway = MediationApi())
        overlay = OverlayController(this)
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
            stopSelf()
            return
        }

        tunnel = descriptor
        running = true
        startForegroundNotification()
        worker = Thread({ runTunnel(descriptor) }, "bulwark-tunnel").also { it.start() }
    }

    private fun stopTunnel() {
        running = false
        tunnel?.let { runCatching { it.close() } }
        tunnel = null
        worker?.interrupt()
        worker = null
        overlay.dismiss()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    private fun runTunnel(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(PACKET_BUFFER_SIZE)
        try {
            while (running) {
                val length = input.read(buffer)
                if (length <= 0) continue
                handlePacket(buffer, length, output)
            }
        } catch (e: Exception) {
            if (running) Log.w(TAG, "tunnel loop stopped: ${e.message}")
        } finally {
            runCatching { output.close() }
            runCatching { input.close() }
        }
    }

    private fun handlePacket(buffer: ByteArray, length: Int, output: FileOutputStream) {
        val packet = Ipv4Udp.parse(buffer, length) ?: return
        if (packet.destPort != DNS_PORT) return

        val query = DnsParser.parseQuery(packet.payload) ?: return
        val decision = runBlocking { repository.checkDomain(query.domain) }

        if (decision.verdict == Verdict.MALICIOUS) {
            val answer = DnsParser.buildNxdomainResponse(packet.payload) ?: return
            output.write(Ipv4Udp.buildResponse(packet, answer))
            recordBlocked(decision)
            overlay.show(decision.domain, onStaySafe = {}, onProceed = {})
        } else {
            val answer = forwardDns(packet.payload) ?: return
            output.write(Ipv4Udp.buildResponse(packet, answer))
            if (decision.fresh && decision.source != VerdictSource.NONE) {
                runBlocking { repository.recordLinkEvent(decision, LinkAction.ALLOWED) }
            }
        }
    }

    /** Record a block, deduplicating bursts of identical queries from the same page load. */
    private fun recordBlocked(decision: DomainDecision) {
        val now = System.currentTimeMillis()
        val previous = lastBlockedAt[decision.domainHash]
        if (previous != null && now - previous < BLOCK_DEDUP_MILLIS) return
        lastBlockedAt[decision.domainHash] = now
        runBlocking { repository.recordLinkEvent(decision, LinkAction.BLOCKED) }
    }

    /** Forward a DNS query to the real resolver, bypassing our own tunnel via protect(). */
    private fun forwardDns(query: ByteArray): ByteArray? {
        val socket = DatagramSocket()
        if (!protect(socket)) {
            socket.close()
            return null
        }
        return try {
            socket.soTimeout = FORWARD_TIMEOUT_MILLIS
            socket.send(DatagramPacket(query, query.size, InetSocketAddress(UPSTREAM_DNS, DNS_PORT)))
            val buffer = ByteArray(DNS_RESPONSE_BUFFER_SIZE)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            buffer.copyOf(response.length)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { socket.close() }
        }
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

        // Virtual endpoints kept inside the tunnel.
        private const val TUN_ADDRESS = "10.111.222.1"
        private const val TUN_PREFIX = 32
        private const val VIRTUAL_DNS = "10.111.222.2"
        private const val TUN_MTU = 1500

        // VERIFY: fixed public resolver. Prefer reading the underlying network's DNS from
        // ConnectivityManager; a fixed resolver is used only because the tunnel must not recurse.
        private const val UPSTREAM_DNS = "1.1.1.1"

        private const val DNS_PORT = 53
        private const val FORWARD_TIMEOUT_MILLIS = 5_000
        private const val BLOCK_DEDUP_MILLIS = 3_000L
        private const val PACKET_BUFFER_SIZE = 32_767
        private const val DNS_RESPONSE_BUFFER_SIZE = 4096

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
