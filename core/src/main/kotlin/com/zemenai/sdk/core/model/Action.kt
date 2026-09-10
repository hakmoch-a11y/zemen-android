package com.zemenai.sdk.core.model

import com.zemenai.sdk.core.json.JsonValue

/** Mirrors the spec's example AI output exactly:
 * `{"type":"ACTION","action":"open_transfer","parameters":{"amount":500}}` */

/**
 * A validated action event delivered to the host application. The host app
 * owns the final UI/business operation for the route; Zemen AI owns
 * understanding and server-side validation.
 */
data class ZemenAction(
    val id: String? = null,
    val name: String,
    val route: String,
    val parameters: Map<String, Any?>,
    val metadata: Map<String, Any?> = emptyMap(),
)

fun interface ZemenActionHandler {
    fun handle(action: ZemenAction)
}

data class ActionProposal(
    val action: String,
    val parameters: Map<String, Any?>
) {
    companion object {
        private const val TYPE_FIELD = "type"
        private const val TYPE_VALUE = "ACTION"

        /** Returns null (not an exception) when [json] doesn't match the
         * ACTION shape — this is a normal, expected outcome for the vast
         * majority of chat responses, which are plain prose, not a parse
         * failure to be alarmed about. */
        fun tryParse(json: JsonValue): ActionProposal? {
            if (json !is JsonValue.JsonObject) return null
            if (json.string(TYPE_FIELD) != TYPE_VALUE) return null
            val action = json.string("action") ?: return null
            val parametersJson = json.objectOrNull("parameters") ?: JsonValue.JsonObject(emptyMap())
            return ActionProposal(action, toKotlinMap(parametersJson))
        }

        private fun toKotlinValue(value: JsonValue): Any? = when (value) {
            is JsonValue.JsonString -> value.value
            is JsonValue.JsonNumber -> value.value
            is JsonValue.JsonBoolean -> value.value
            is JsonValue.JsonNull -> null
            is JsonValue.JsonArray -> value.items.map { toKotlinValue(it) }
            is JsonValue.JsonObject -> toKotlinMap(value)
        }

        private fun toKotlinMap(json: JsonValue.JsonObject): Map<String, Any?> =
            json.entries.mapValues { (_, v) -> toKotlinValue(v) }
    }
}

/** Mirrors backend-gateway's ValidateActionResponseDto (Phase 5/7)
 * exactly — returned by POST /sdk/actions/validate. */
data class ActionValidationResult(
    val valid: Boolean,
    val reasons: List<String>,
    val route: String?
) {
    companion object {
        fun fromJson(json: JsonValue.JsonObject): ActionValidationResult {
            val reasonsJson = json.array("reasons") ?: emptyList()
            return ActionValidationResult(
                valid = json.boolean("valid") ?: false,
                reasons = reasonsJson.filterIsInstance<JsonValue.JsonString>().map { it.value },
                route = json.string("route")
            )
        }
    }
}

/**
 * The end-to-end result of [ZemenAiCore.handleAction]: validate against
 * the live Action Engine, then dispatch to a registered handler only if
 * valid. A host app inspects this to know exactly what happened —
 * including the case where the action WAS approved by the server but the
 * host app never called `registerAction` for it, which is a host-app
 * integration bug worth surfacing distinctly rather than silently
 * swallowing.
 */
sealed class ActionOutcome {
    data class Dispatched(val route: String?) : ActionOutcome()
    object NoHandlerRegistered : ActionOutcome()
    data class Rejected(val reasons: List<String>) : ActionOutcome()
}
