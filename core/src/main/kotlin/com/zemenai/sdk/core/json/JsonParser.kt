package com.zemenai.sdk.core.json

/**
 * A small recursive-descent JSON parser/writer, used instead of
 * kotlinx.serialization/Gson/Moshi for two reasons: (1) this SDK has a
 * small, fixed set of request/response shapes (config, ask, action
 * validation) — pulling in a general-purpose JSON library is unnecessary
 * weight for that; (2) it avoids forcing a specific JSON library version
 * on the host app, which could otherwise conflict with whatever the host
 * app already depends on for its own networking. This mirrors the same
 * design call made for ai-service's chunking logic in Phase 4 (no
 * LangChain) and backend-gateway's Action Engine (no external validation
 * framework) — minimal footprint for a small, well-defined job.
 */
object JsonParser {
    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.isAtEnd()) {
            throw JsonException("Unexpected trailing content at position ${parser.pos}")
        }
        return value
    }

    private class Parser(private val text: String) {
        var pos = 0

        fun isAtEnd() = pos >= text.length

        fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun parseValue(): JsonValue {
            skipWhitespace()
            if (isAtEnd()) throw JsonException("Unexpected end of input")
            return when (text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.JsonString(parseStringLiteral())
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> parseNumber()
            }
        }

        private fun expect(char: Char) {
            if (isAtEnd() || text[pos] != char) {
                throw JsonException("Expected '$char' at position $pos")
            }
            pos++
        }

        private fun parseObject(): JsonValue.JsonObject {
            expect('{')
            skipWhitespace()
            val entries = LinkedHashMap<String, JsonValue>()
            if (!isAtEnd() && text[pos] == '}') {
                pos++
                return JsonValue.JsonObject(entries)
            }
            var done = false
            while (!done) {
                skipWhitespace()
                val key = parseStringLiteral()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                entries[key] = value
                skipWhitespace()
                if (isAtEnd()) throw JsonException("Unexpected end of input in object")
                val next = text[pos]
                if (next == ',') {
                    pos++
                } else if (next == '}') {
                    pos++
                    done = true
                } else {
                    throw JsonException("Expected ',' or '}' at position $pos")
                }
            }
            return JsonValue.JsonObject(entries)
        }

        private fun parseArray(): JsonValue.JsonArray {
            expect('[')
            skipWhitespace()
            val items = mutableListOf<JsonValue>()
            if (!isAtEnd() && text[pos] == ']') {
                pos++
                return JsonValue.JsonArray(items)
            }
            var done = false
            while (!done) {
                items.add(parseValue())
                skipWhitespace()
                if (isAtEnd()) throw JsonException("Unexpected end of input in array")
                val next = text[pos]
                if (next == ',') {
                    pos++
                } else if (next == ']') {
                    pos++
                    done = true
                } else {
                    throw JsonException("Expected ',' or ']' at position $pos")
                }
            }
            return JsonValue.JsonArray(items)
        }

        private fun parseStringLiteral(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (isAtEnd()) throw JsonException("Unterminated string")
                val c = text[pos]
                when {
                    c == '"' -> {
                        pos++
                        return sb.toString()
                    }
                    c == '\\' -> {
                        pos++
                        if (isAtEnd()) throw JsonException("Unterminated escape sequence")
                        when (val escaped = text[pos]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = text.substring(pos + 1, pos + 5)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw JsonException("Invalid escape '\\$escaped'")
                        }
                        pos++
                    }
                    else -> {
                        sb.append(c)
                        pos++
                    }
                }
            }
        }

        private fun parseBoolean(): JsonValue.JsonBoolean {
            return when {
                text.startsWith("true", pos) -> {
                    pos += 4
                    JsonValue.JsonBoolean(true)
                }
                text.startsWith("false", pos) -> {
                    pos += 5
                    JsonValue.JsonBoolean(false)
                }
                else -> throw JsonException("Invalid literal at position $pos")
            }
        }

        private fun parseNull(): JsonValue {
            if (text.startsWith("null", pos)) {
                pos += 4
                return JsonValue.JsonNull
            }
            throw JsonException("Invalid literal at position $pos")
        }

        private fun parseNumber(): JsonValue.JsonNumber {
            val start = pos
            if (!isAtEnd() && text[pos] == '-') pos++
            while (!isAtEnd() && text[pos].isDigit()) pos++
            if (!isAtEnd() && text[pos] == '.') {
                pos++
                while (!isAtEnd() && text[pos].isDigit()) pos++
            }
            if (!isAtEnd() && (text[pos] == 'e' || text[pos] == 'E')) {
                pos++
                if (!isAtEnd() && (text[pos] == '+' || text[pos] == '-')) pos++
                while (!isAtEnd() && text[pos].isDigit()) pos++
            }
            if (pos == start) throw JsonException("Invalid number at position $pos")
            return JsonValue.JsonNumber(text.substring(start, pos).toDouble())
        }
    }
}

/** Writes a small, fixed set of Kotlin values to a JSON string — the
 * request-encoding half of the same minimal-footprint decision as
 * JsonParser. */
object JsonWriter {
    fun write(value: Any?): String {
        val sb = StringBuilder()
        writeValue(value, sb)
        return sb.toString()
    }

    private fun writeValue(value: Any?, sb: StringBuilder) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(value, sb)
            is Boolean -> sb.append(value.toString())
            is Int -> sb.append(value.toString())
            is Long -> sb.append(value.toString())
            is Double -> sb.append(value.toString())
            is Float -> sb.append(value.toString())
            is Map<*, *> -> writeObject(value, sb)
            is List<*> -> writeArray(value, sb)
            else -> throw JsonException("Cannot serialize value of type ${value::class}")
        }
    }

    private fun writeObject(map: Map<*, *>, sb: StringBuilder) {
        sb.append('{')
        map.entries.forEachIndexed { index, (key, v) ->
            if (index > 0) sb.append(',')
            writeString(key.toString(), sb)
            sb.append(':')
            writeValue(v, sb)
        }
        sb.append('}')
    }

    private fun writeArray(list: List<*>, sb: StringBuilder) {
        sb.append('[')
        list.forEachIndexed { index, v ->
            if (index > 0) sb.append(',')
            writeValue(v, sb)
        }
        sb.append(']')
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) {
                    sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
    }
}
