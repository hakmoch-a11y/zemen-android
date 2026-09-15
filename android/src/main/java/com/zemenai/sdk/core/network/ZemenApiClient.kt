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
        return ZemenConfig.fromJson(parseDataObject(response))
    }

    suspend fun ask(question: String): ChatAnswer {
        val response = executeAuthenticated(HttpRequest(
            method = "POST",
            url = "$baseUrl/sdk/ask",
            headers = mapOf("Content-Type" to "application/json"),
            body = JsonWriter.write(mapOf("question" to question))
        ))
        return ChatAnswer.fromJson(parseDataObject(response))
    }

    suspend fun validateAction(action: String, parameters: Map<String, Any?>): ActionValidationResult {
        val response = executeAuthenticated(HttpRequest(
            method = "POST",
            url = "$baseUrl/sdk/actions/validate",
            headers = mapOf("Content-Type" to "application/json"),
            body = JsonWriter.write(mapOf("action" to action, "parameters" to parameters))
        ))
        return ActionValidationResult.fromJson(parseDataObject(response))
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
