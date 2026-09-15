package com.zemenai.sdk.core.network

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?
)

data class HttpResponse(
    val statusCode: Int,
    val body: String
)

/**
 * The core module's only boundary with the outside world for networking.
 * Deliberately an interface, not a concrete client, for a specific
 * reason: `java.net.http.HttpClient` (the obvious modern JDK choice) is
 * only available on Android starting at API 34 — using it directly here
 * would silently break on every device below that, which is far too high
 * a minSdk floor for a banking SDK. The `android` module provides its
 * own implementation using `HttpURLConnection` (available since API 1,
 * no desugaring required) — see AndroidHttpTransport. [JdkHttpTransport]
 * in this module is a reference implementation for JVM-side testing and
 * non-Android reuse only; it is NOT what ships inside an Android app.
 */
fun interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}
