package id.bulwark.net

import id.bulwark.data.model.ReportAlreadyListedException
import id.bulwark.data.model.ReportKind
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MediationApiTest {

    @Test
    fun actualHttp409AlreadyListedThrowsTheSpecificException() = runTest {
        val requestBody = AtomicReference<String>()
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val serverThread = thread {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                reader.readLine()
                var contentLength = 0
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(':').trim().toInt()
                    }
                }
                val body = CharArray(contentLength)
                var offset = 0
                while (offset < contentLength) {
                    val count = reader.read(body, offset, contentLength - offset)
                    if (count < 0) break
                    offset += count
                }
                requestBody.set(String(body, 0, offset))

                val response = """{"error":"already_listed"}""".toByteArray(Charsets.UTF_8)
                val output = socket.getOutputStream()
                output.write(
                    (
                        "HTTP/1.1 409 Conflict\r\nContent-Type: application/json\r\n" +
                            "Content-Length: ${response.size}\r\nConnection: close\r\n\r\n"
                        ).toByteArray(Charsets.US_ASCII),
                )
                output.write(response)
                output.flush()
            }
        }

        try {
            val api = MediationApi(
                baseUrl = "http://127.0.0.1:${server.localPort}",
                sharedToken = "test-token",
                timeoutMillis = 2_000,
            )

            try {
                api.report(ReportKind.DOMAIN, "ab".repeat(32))
                fail("HTTP 409 already_listed must not be treated as a generic failure")
            } catch (_: ReportAlreadyListedException) {
                // Expected: only this confirmed response is a duplicate.
            }

            val sentBody = requestBody.get()
            assertTrue(sentBody.contains("\"kind\":\"domain\""))
            assertTrue(sentBody.contains("\"hash\":\"0x${"ab".repeat(32)}\""))
        } finally {
            server.close()
            serverThread.join(2_000)
        }
    }
}
