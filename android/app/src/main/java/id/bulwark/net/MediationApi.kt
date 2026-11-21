package id.bulwark.net

import id.bulwark.BuildConfig
import id.bulwark.data.model.MediationGateway
import id.bulwark.data.model.RemoteVerdict
import id.bulwark.data.model.ReportKind
import id.bulwark.data.model.Verdict
import id.bulwark.data.model.VerdictSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the Bulwark Mediation API (PRD Architecture). Endpoints and payloads are taken verbatim
 * from the PRD; nothing here is invented.
 *
 * - POST /v1/check/domain  { prefix, hash }  -> { verdict, source }
 * - POST /v1/check/apk     { sha256 }        -> { verdict, source }
 * - POST /v1/report        { kind, hash }    -> { txHash }
 * - GET  /v1/health                          -> { ok, contractAddress, chainId }
 *
 * Only the 8-byte prefix is sent for checks, never the full URL (privacy, PRD R6).
 */
class MediationApi(
    baseUrl: String = BuildConfig.API_BASE_URL,
    private val sharedToken: String = BuildConfig.API_SHARED_TOKEN,
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) : MediationGateway {

    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun checkDomain(domain: String, domainHash: String, prefix: String): RemoteVerdict? =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("prefix", prefix)
                .put("hash", domainHash)
            parseVerdict(post("/v1/check/domain", body))
        }

    suspend fun checkApk(sha256: String): RemoteVerdict? = withContext(Dispatchers.IO) {
        parseVerdict(post("/v1/check/apk", JSONObject().put("sha256", sha256)))
    }

    override suspend fun report(kind: ReportKind, hash: String): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("kind", kind.wire)
            .put("hash", hash)
        val json = post("/v1/report", body, authenticated = true) ?: return@withContext null
        json.optString("txHash").takeIf { it.isNotEmpty() }
    }

    suspend fun health(): HealthInfo? = withContext(Dispatchers.IO) {
        val json = get("/v1/health") ?: return@withContext null
        HealthInfo(
            ok = json.optBoolean("ok", true),
            contractAddress = json.optString("contractAddress").takeIf { it.isNotEmpty() },
            chainId = json.optLong("chainId", 0L).takeIf { it != 0L },
        )
    }

    private fun parseVerdict(json: JSONObject?): RemoteVerdict? {
        if (json == null) return null
        return RemoteVerdict(
            verdict = Verdict.fromWire(json.optString("verdict")),
            source = VerdictSource.fromWire(json.optString("source")),
        )
    }

    private fun post(path: String, body: JSONObject, authenticated: Boolean = false): JSONObject? {
        val connection = open("POST", path, authenticated)
        connection.doOutput = true
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            readJson(connection)
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun get(path: String): JSONObject? {
        val connection = open("GET", path, authenticated = false)
        return try {
            readJson(connection)
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun readJson(connection: HttpURLConnection): JSONObject? {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }
        if (code !in 200..299 || text.isNullOrEmpty()) return null
        return runCatching { JSONObject(text) }.getOrNull()
    }

    private fun open(method: String, path: String, authenticated: Boolean): HttpURLConnection {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        // VERIFY: header name must match the backend's shared-token check.
        if (authenticated && sharedToken.isNotEmpty()) {
            connection.setRequestProperty("X-Bulwark-Token", sharedToken)
        }
        return connection
    }

    data class HealthInfo(
        val ok: Boolean,
        val contractAddress: String?,
        val chainId: Long?,
    )

    companion object {
        private const val DEFAULT_TIMEOUT_MILLIS = 10_000
    }
}
