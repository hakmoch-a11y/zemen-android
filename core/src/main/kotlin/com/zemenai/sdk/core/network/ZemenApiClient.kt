package com.zemenai.sdk.core.network

import com.zemenai.sdk.core.json.JsonException
import com.zemenai.sdk.core.json.JsonParser
import com.zemenai.sdk.core.json.JsonValue
import com.zemenai.sdk.core.json.JsonWriter
import com.zemenai.sdk.core.model.ActionValidationResult
import com.zemenai.sdk.core.model.ChatAnswer
import com.zemenai.sdk.core.model.ZemenConfig

/**
 * Talks to backend-gateway's `/sdk` routes (Phase 7), authenticating
 * every request with the application's own scoped API key via the
 * `X-Api-Key` header — never a user JWT, never ai-service's internal key.
 * See backend-gateway's README, "Phase 7 addition: the SDK proxy", for
 * the server side of this contract.
 *
 * Every public method here is guaranteed to only ever throw
 * [ZemenApiException] — never a raw [JsonException] or any other
 * exception type. That guarantee is enforced centrally in
 * [parseResponseBody] rather than by each individual model's `fromJson`,
 * so a field a `fromJson` didn't anticipate (missing, wrong type, or a
 * gateway/SDK contract that has since drifted, as happened once already
 * with the `/sdk/ask` ACTION shape) surfaces as
 * [ZemenApiException.MalformedResponse] instead of crashing the host
 * app — regardless of which endpoint or which model hits it.
 */
class ZemenApiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val transport: HttpTransport
) {
    suspend fun getConfig(): ZemenConfig {
        val response = executeAuthenticated(HttpRequest(
            method = "GET",
            url = "$baseUrl/sdk/config",
            headers = emptyMap(),
            body = null
        ))
        return parseResponseBody(response) { ZemenConfig.fromJson(it) }
    }

    suspend fun ask(question: String): ChatAnswer {
        val response = executeAuthenticated(HttpRequest(
            method = "POST",
            url = "$baseUrl/sdk/ask",
            headers = mapOf("Content-Type" to "application/json"),
            body = JsonWriter.write(mapOf("question" to question))
        ))
        return parseResponseBody(response) { ChatAnswer.fromJson(it) }
    }

    suspend fun validateAction(action: String, parameters: Map<String, Any?>): ActionValidationResult {
        val response = executeAuthenticated(HttpRequest(
            method = "POST",
            url = "$baseUrl/sdk/actions/validate",
            headers = mapOf("Content-Type" to "application/json"),
            body = JsonWriter.write(mapOf("action" to action, "parameters" to parameters))
        ))
        return parseResponseBody(response) { ActionValidationResult.fromJson(it) }
    }

    private suspend fun executeAuthenticated(request: HttpRequest): HttpResponse {
        val authenticated = request.copy(
            headers = request.headers + ("X-Api-Key" to apiKey)
        )
        val response = try {
            transport.execute(authenticated)
        } catch (e: Exception) {
            throw ZemenApiException.NetworkError("Failed to reach Zemen AI: ${e.message}", e)
        }

        if (response.statusCode == 401) {
            throw ZemenApiException.Unauthorized(extractErrorMessage(response) ?: "Invalid API key")
        }
        if (response.statusCode !in 200..299) {
            throw ZemenApiException.HttpError(
                response.statusCode,
                extractErrorMessage(response) ?: "Request failed with status ${response.statusCode}"
            )
        }
        return response
    }

    // backend-gateway wraps every success response as { success, data,
    // timestamp } (TransformInterceptor, present since Phase 1) — this
    // unwraps that envelope and returns the inner `data` object.
    private fun parseDataObject(response: HttpResponse): JsonValue.JsonObject {
        val parsed = try {
            JsonParser.parse(response.body)
        } catch (e: JsonException) {
            throw ZemenApiException.MalformedResponse("Invalid JSON in response: ${e.message}")
        }
        val envelope = parsed as? JsonValue.JsonObject
            ?: throw ZemenApiException.MalformedResponse("Expected a JSON object response")
        return envelope.objectOrNull("data")
            ?: throw ZemenApiException.MalformedResponse("Response missing \"data\" field")
    }

    // The single point every successful response passes through on its
    // way to becoming a domain model. `parse` is whichever model's
    // `fromJson` the caller needs; anything it throws (a missing/
    // mistyped field, a ZemenApiException raised deliberately by
    // ChatInterpreter for an unrecognized shape, or anything else) is
    // normalized to ZemenApiException.MalformedResponse here so it can
    // never reach the host app as a bare exception.
    private fun <T> parseResponseBody(response: HttpResponse, parse: (JsonValue.JsonObject) -> T): T {
        val data = parseDataObject(response)
        return try {
            parse(data)
        } catch (e: ZemenApiException) {
            throw e
        } catch (e: Exception) {
            throw ZemenApiException.MalformedResponse("Unexpected response shape: ${e.message}")
        }
    }

    // backend-gateway's error shape (AllExceptionsFilter, Phase 1):
    // { statusCode, path, timestamp, message, error }. message may be a
    // string or a string array (class-validator sends arrays).
    private fun extractErrorMessage(response: HttpResponse): String? {
        val parsed = runCatching { JsonParser.parse(response.body) }.getOrNull()
        val obj = parsed as? JsonValue.JsonObject ?: return null
        return when (val messageField = obj["message"]) {
            is JsonValue.JsonString -> messageField.value
            is JsonValue.JsonArray -> messageField.items
                .filterIsInstance<JsonValue.JsonString>()
                .joinToString("; ") { it.value }
                .ifEmpty { null }
            else -> null
        }
    }
}
