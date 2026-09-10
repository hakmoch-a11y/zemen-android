package com.zemenai.sdk.core.network

/** Test double — lets tests script exact responses without any real
 * network activity, and inspect exactly what request was sent. */
class FakeHttpTransport(
    private val responder: (HttpRequest) -> HttpResponse
) : HttpTransport {
    var lastRequest: HttpRequest? = null
        private set
    var callCount = 0
        private set

    override suspend fun execute(request: HttpRequest): HttpResponse {
        lastRequest = request
        callCount += 1
        return responder(request)
    }

    companion object {
        fun respondingWith(statusCode: Int, body: String): FakeHttpTransport =
            FakeHttpTransport { HttpResponse(statusCode, body) }

        fun throwing(exception: Exception): FakeHttpTransport =
            FakeHttpTransport { throw exception }
    }
}
