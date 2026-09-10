package com.zemenai.sdk.network

import com.zemenai.sdk.core.network.HttpRequest
import com.zemenai.sdk.core.network.HttpResponse
import com.zemenai.sdk.core.network.HttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * The [HttpTransport] that actually ships on Android. Deliberately uses
 * `HttpURLConnection`, not `java.net.http.HttpClient` — the latter only
 * exists on Android starting at API 34 (Android 14), which would silently
 * break this SDK on every older device. `HttpURLConnection` has been
 * available since API 1 and needs no core library desugaring.
 *
 * NOT independently verified by any compiler in the environment this SDK
 * was built in — see the SDK README's "What was and wasn't verified"
 * section. Written carefully against long-stable, unchanged
 * `HttpURLConnection` APIs specifically to minimize that risk; still,
 * build and run this against a real Android target before shipping.
 */
class AndroidHttpTransport(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000
) : HttpTransport {

    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.doInput = true

            for ((name, value) in request.headers) {
                connection.setRequestProperty(name, value)
            }

            if (request.body != null) {
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(request.body)
                }
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let { readFully(it) } ?: ""

            HttpResponse(statusCode = statusCode, body = body)
        } finally {
            connection.disconnect()
        }
    }

    private fun readFully(stream: java.io.InputStream): String {
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            val sb = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                sb.append(line)
                line = reader.readLine()
            }
            return sb.toString()
        }
    }
}
