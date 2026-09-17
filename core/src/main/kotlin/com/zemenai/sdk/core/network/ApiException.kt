package com.zemenai.sdk.core.network

/** Base type for every error the SDK surfaces from a network call — a
 * host app can catch this one type regardless of the underlying cause. */
sealed class ZemenApiException(message: String) : Exception(message) {
    /** The device couldn't reach the server at all (no connectivity, DNS
     * failure, timeout, etc.) — never even got an HTTP response. */
    class NetworkError(message: String, cause: Throwable? = null) : ZemenApiException(message) {
        init {
            if (cause != null) initCause(cause)
        }
    }

    /** The API key was missing, malformed, or rejected. Distinct from
     * other 4xx/5xx so a host app can react specifically — e.g. surface
     * "this app isn't configured correctly" rather than a generic error. */
    class Unauthorized(message: String) : ZemenApiException(message)

    /** Any other non-2xx HTTP response. */
    class HttpError(val statusCode: Int, message: String) : ZemenApiException(message)

    /** The response body wasn't valid JSON, or didn't match the shape
     * expected for this call. */
    class MalformedResponse(message: String) : ZemenApiException(message)

    /** The SDK was used before [com.zemenai.sdk.ZemenAI.initialize] was
     * called — e.g. a host app forgot to register its Application class in
     * AndroidManifest.xml (the exact real-world failure this type exists
     * to catch), or a screen ran before app startup finished initializing
     * Zemen. This is a developer-integration mistake, not a network or
     * server condition, so it's modeled separately: a host app should
     * never need to guess whether a caught [ZemenApiException] means "the
     * network is down" or "you forgot a setup step". */
    class ConfigurationError(message: String) : ZemenApiException(message)
}
