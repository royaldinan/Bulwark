package id.bulwark.net

import android.util.Log
import id.bulwark.BuildConfig
import id.bulwark.data.model.MediationGateway
import id.bulwark.data.model.RemoteVerdict
import id.bulwark.data.model.ReportAlreadyListedException
import id.bulwark.data.model.ReportKind
import id.bulwark.data.model.Verdict
import id.bulwark.data.model.VerdictSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the Bulwark Mediation API (PRD Architecture).
 *
 * - POST /v1/check/domain  { prefix: 0x<16 hex>, hash: 0x<64 hex> } -> { verdict, source }
 * - POST /v1/check/apk     { sha256: <64 hex> }                     -> { verdict, source }
 * - POST /v1/report        { kind, hash }                           -> { txHash }
 * - GET  /v1/health                                                 -> { ok, contractAddress, chainId }
 *
 * The backend expects 0x-prefixed hex for domain checks (verified with
 * 0xbd567f3c6619d191 / 0xbd56…dd1d). Only the 16-hex prefix is logged, never the full hash.
 */
class MediationApi(
    baseUrl: String = BuildConfig.API_BASE_URL,
    private val sharedToken: String = BuildConfig.API_SHARED_TOKEN,
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) : MediationGateway {

    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun checkDomain(domain: String, domainHash: String, prefix: String): RemoteVerdict? =
        withContext(Dispatchers.IO) {
            val prefixHex = "0x$prefix"
            val hashHex = "0x$domainHash"
            val body = JSONObject()
                .put("prefix", prefixHex)
                .put("hash", hashHex)

            val response = execute("POST", "/v1/check/domain", body, authenticated = false)
            val remote = parseVerdict(response)
            Log.i(
                TAG,
                "check/domain domain=$domain prefix16=$prefixHex status=${response.code} " +
                    "verdict=${remote?.verdict ?: "none"} source=${remote?.source ?: "none"} " +
                    "error=${response.error ?: "-"}",
            )
            if (remote == null) {
                Log.w(
                    TAG,
                    "check/domain failed domain=$domain prefix16=$prefixHex status=${response.code} " +
                        "error=${response.error ?: "http_or_parse"}",
                )
            }
            remote
        }

    override suspend fun checkApk(sha256: String): RemoteVerdict? = withContext(Dispatchers.IO) {
        // Backend `validate.js` requires `0x` + 64 hex for sha256 too.
        val sha256Hex = if (sha256.startsWith("0x") || sha256.startsWith("0X")) sha256 else "0x$sha256"
        val response = execute("POST", "/v1/check/apk", JSONObject().put("sha256", sha256Hex), authenticated = false)
        val remote = parseVerdict(response)
        val prefix16 = sha256Hex.take(18)
        Log.i(
            TAG,
            "check/apk prefix16=$prefix16 status=${response.code} " +
                "verdict=${remote?.verdict ?: "none"} source=${remote?.source ?: "none"} " +
                "error=${response.error ?: "-"}",
        )
        if (remote == null) {
            Log.w(TAG, "check/apk failed prefix16=$prefix16 status=${response.code} error=${response.error ?: "http_or_parse"}")
        }
        remote
    }

    override suspend fun report(kind: ReportKind, hash: String): String? = withContext(Dispatchers.IO) {
        // Backend rejects a bare hash: it validates `^0x[0-9a-f]{64}$` for both kinds. The app used
        // to send 64 hex without the prefix, so every report failed with HTTP 400 invalid_hash.
        val hashHex = if (hash.startsWith("0x") || hash.startsWith("0X")) hash else "0x$hash"
        val body = """{"kind":"${kind.wire}","hash":"$hashHex"}"""
        val response = execute("POST", "/v1/report", body, authenticated = true)
        if (response.code == 409 && response.body?.contains(ALREADY_LISTED_ERROR) == true) {
            throw ReportAlreadyListedException()
        }
        val json = parseBody(response)
        val txHash = json?.optString("txHash")?.takeIf { it.isNotEmpty() }
        Log.i(
            TAG,
            "report kind=${kind.wire} hash16=${hashHex.take(18)} status=${response.code} " +
                "txHash=${txHash ?: "none"} error=${response.error ?: "-"}",
        )
        if (json == null) {
            Log.w(
                TAG,
                "report failed kind=${kind.wire} hash16=${hashHex.take(18)} status=${response.code} " +
                    "error=${response.error ?: response.body?.take(120) ?: "http_or_parse"}",
            )
            return@withContext null
        }
        txHash
    }

    suspend fun health(): HealthInfo? = withContext(Dispatchers.IO) {
        val json = parseBody(execute("GET", "/v1/health", body = null, authenticated = false))
            ?: return@withContext null
        HealthInfo(
            ok = json.optBoolean("ok", true),
            contractAddress = json.optString("contractAddress").takeIf { it.isNotEmpty() },
            chainId = json.optLong("chainId", 0L).takeIf { it != 0L },
        )
    }

    private fun parseVerdict(response: HttpResponse): RemoteVerdict? {
        val json = parseBody(response) ?: return null
        if (!json.has("verdict")) return null
        return RemoteVerdict(
            verdict = Verdict.fromWire(json.optString("verdict")),
            source = VerdictSource.fromWire(json.optString("source")),
        )
    }

    private fun parseBody(response: HttpResponse): JSONObject? {
        if (response.code !in 200..299) return null
        val text = response.body
        if (text.isNullOrEmpty()) return null
        return runCatching { JSONObject(text) }.getOrNull()
    }

    private data class HttpResponse(val code: Int, val body: String?, val error: String? = null)

    private fun execute(
        method: String,
        path: String,
        body: JSONObject?,
        authenticated: Boolean,
    ): HttpResponse = executeRequest(method, path, body?.toString(), authenticated)

    private fun execute(
        method: String,
        path: String,
        body: String,
        authenticated: Boolean,
    ): HttpResponse = executeRequest(method, path, body, authenticated)

    private fun executeRequest(
        method: String,
        path: String,
        body: String?,
        authenticated: Boolean,
    ): HttpResponse {
        val connection = open(method, path, authenticated)
        return try {
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }
            HttpResponse(code, text)
        } catch (e: Exception) {
            HttpResponse(-1, null, "${e.javaClass.simpleName}: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }

    private fun open(method: String, path: String, authenticated: Boolean): HttpURLConnection {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        // Header name matches the backend `tokenGuard` in backend/src/app.js.
        if (authenticated && sharedToken.isNotEmpty()) {
            connection.setRequestProperty("x-api-token", sharedToken)
        }
        return connection
    }

    data class HealthInfo(
        val ok: Boolean,
        val contractAddress: String?,
        val chainId: Long?,
    )

    companion object {
        private const val TAG = "BulwarkDns"
        private const val DEFAULT_TIMEOUT_MILLIS = 10_000
        private val ALREADY_LISTED_ERROR = Regex("\\\"error\\\"\\s*:\\s*\\\"already_listed\\\"")
    }
}
