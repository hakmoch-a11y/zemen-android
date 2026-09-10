package com.zemenai.sdk.core.json

/**
 * A minimal, dependency-free JSON value model. The SDK deliberately does
 * not depend on kotlinx.serialization, Gson, or Moshi — see JsonParser's
 * doc comment for why. This handles exactly the shapes Zemen AI's API
 * actually returns; it is not a general-purpose JSON library.
 */
sealed class JsonValue {
    data class JsonObject(val entries: Map<String, JsonValue>) : JsonValue() {
        operator fun get(key: String): JsonValue? = entries[key]

        fun string(key: String): String? = (entries[key] as? JsonString)?.value
        fun requireString(key: String): String =
            string(key) ?: throw JsonException("Missing or non-string field \"$key\"")

        fun number(key: String): Double? = (entries[key] as? JsonNumber)?.value
        fun boolean(key: String): Boolean? = (entries[key] as? JsonBoolean)?.value

        fun array(key: String): List<JsonValue>? = (entries[key] as? JsonArray)?.items
        fun objectOrNull(key: String): JsonObject? = entries[key] as? JsonObject
    }

    data class JsonArray(val items: List<JsonValue>) : JsonValue()
    data class JsonString(val value: String) : JsonValue()
    data class JsonNumber(val value: Double) : JsonValue()
    data class JsonBoolean(val value: Boolean) : JsonValue()
    object JsonNull : JsonValue()
}

class JsonException(message: String) : Exception(message)
