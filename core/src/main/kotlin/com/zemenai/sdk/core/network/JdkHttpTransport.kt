package com.zemenai.sdk.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest as JdkHttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

/**
 * Reference [HttpTransport] implementation using `java.net.http.HttpClient`.
 * This is intentionally NOT used by the Android module — see
 * [HttpTransport]'s doc comment for why (API 34+ requirement). It exists
 * so the core module is independently testable/usable on a plain JVM
 * (this SDK's own test suite uses it against a local test server), and
 * for any future non-Android reuse of this module.
 */
class JdkHttpTransport(
    connectTimeout: Duration = Duration.ofSeconds(15)
) : HttpTransport {
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(connectTimeout)
        .build()

    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val builder = JdkHttpRequest.newBuilder()
            .uri(URI.create(request.url))
            .timeout(Duration.ofSeconds(30))

        for ((name, value) in request.headers) {
            builder.header(name, value)
        }

        val bodyPublisher = if (request.body != null) {
            JdkHttpRequest.BodyPublishers.ofString(request.body)
        } else {
            JdkHttpRequest.BodyPublishers.noBody()
        }
        builder.method(request.method, bodyPublisher)

        val response = client.send(builder.build(), BodyHandlers.ofString())
        HttpResponse(statusCode = response.statusCode(), body = response.body())
    }
}
