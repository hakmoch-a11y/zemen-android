package com.zemenai.sdk.core.model

import com.zemenai.sdk.core.json.JsonValue
import com.zemenai.sdk.core.json.JsonWriter

/** Runtime action catalog supplied by the Zemen AI Gateway. */
data class ConfiguredAction(
    val id: String,
    val name: String,
    val displayName: String?,
    val route: String,
    val description: String?,
    val parameters: Map<String, Map<String, Any?>>,
    val status: String,
    val metadata: Map<String, Any?> = emptyMap()
)

data class ZemenConfig(
    val applicationId: String,
    val applicationName: String,
    val platform: String,
    val bankId: String,
    val bankName: String,
    val status: String,
    val configurationVersion: Long,
    val actions: List<ConfiguredAction>
) {
    val isActive: Boolean get() = status == "ACTIVE"

    fun toJson(): String = JsonWriter.write(
        mapOf(
            "applicationId" to applicationId,
            "applicationName" to applicationName,
            "platform" to platform,
            "bankId" to bankId,
            "bankName" to bankName,
            "status" to status,
            "configurationVersion" to configurationVersion,
            "actions" to actions.map { action ->
                mapOf(
                    "id" to action.id,
                    "name" to action.name,
                    "displayName" to action.displayName,
                    "route" to action.route,
                    "description" to action.description,
                    "parameters" to action.parameters,
                    "status" to action.status,
                    "metadata" to action.metadata,
                )
            },
        )
    )

    companion object {
        private fun value(json: JsonValue): Any? = when (json) {
            is JsonValue.JsonString -> json.value
            is JsonValue.JsonNumber -> json.value
            is JsonValue.JsonBoolean -> json.value
            is JsonValue.JsonNull -> null
            is JsonValue.JsonArray -> json.items.map(::value)
            is JsonValue.JsonObject -> json.entries.mapValues { value(it.value) }
        }

        private fun parameterMap(json: JsonValue.JsonObject): Map<String, Map<String, Any?>> =
            json.entries.mapValues { (_, definition) ->
                val obj = definition as? JsonValue.JsonObject ?: JsonValue.JsonObject(emptyMap())
                obj.entries.mapValues { value(it.value) }
            }

        fun fromJson(json: JsonValue.JsonObject): ZemenConfig = ZemenConfig(
            applicationId = json.requireString("applicationId"),
            applicationName = json.requireString("applicationName"),
            platform = json.requireString("platform"),
            bankId = json.requireString("bankId"),
            bankName = json.requireString("bankName"),
            status = json.requireString("status"),
            configurationVersion = (json["configurationVersion"] as? JsonValue.JsonNumber)?.value?.toLong() ?: 0L,
            actions = (json.array("actions") ?: emptyList()).mapNotNull { item ->
                val obj = item as? JsonValue.JsonObject ?: return@mapNotNull null
                val params = obj.objectOrNull("parameters")
                ConfiguredAction(
                    id = obj.requireString("id"),
                    name = obj.requireString("name"),
                    displayName = obj.string("displayName"),
                    route = obj.requireString("route"),
                    description = obj.string("description"),
                    parameters = params?.let(::parameterMap) ?: emptyMap(),
                    status = obj.requireString("status"),
                    metadata = obj.objectOrNull("metadata")?.entries?.mapValues { value(it.value) } ?: emptyMap(),
                )
            },
        )
    }
}
