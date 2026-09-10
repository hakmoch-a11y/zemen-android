package com.zemenai.sdk.core.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsonParserTest {

    @Test
    fun `parses a simple object`() {
        val result = JsonParser.parse("""{"name":"Zemen","active":true}""") as JsonValue.JsonObject
        assertEquals("Zemen", result.string("name"))
        assertEquals(true, result.boolean("active"))
    }

    @Test
    fun `parses nested objects and arrays`() {
        val result = JsonParser.parse(
            """{"sources":[{"filename":"a.txt","score":0.9},{"filename":"b.txt","score":0.5}]}"""
        ) as JsonValue.JsonObject
        val sources = result.array("sources")!!
        assertEquals(2, sources.size)
        val first = sources[0] as JsonValue.JsonObject
        assertEquals("a.txt", first.string("filename"))
        assertEquals(0.9, first.number("score"))
    }

    @Test
    fun `parses numbers including negative and decimal`() {
        val result = JsonParser.parse("""{"amount":-42.5}""") as JsonValue.JsonObject
        assertEquals(-42.5, result.number("amount"))
    }

    @Test
    fun `parses escaped characters in strings`() {
        val result = JsonParser.parse(""""line1\nline2\ttab\"quote"""") as JsonValue.JsonString
        assertEquals("line1\nline2\ttab\"quote", result.value)
    }

    @Test
    fun `parses unicode escapes`() {
        val result = JsonParser.parse(""""\u0041\u0042"""") as JsonValue.JsonString
        assertEquals("AB", result.value)
    }

    @Test
    fun `parses null`() {
        val result = JsonParser.parse("null")
        assertTrue(result is JsonValue.JsonNull)
    }

    @Test
    fun `parses an empty object and empty array`() {
        val obj = JsonParser.parse("{}") as JsonValue.JsonObject
        assertTrue(obj.entries.isEmpty())
        val arr = JsonParser.parse("[]") as JsonValue.JsonArray
        assertTrue(arr.items.isEmpty())
    }

    @Test
    fun `throws JsonException on malformed input`() {
        try {
            JsonParser.parse("{not valid json")
            fail("Expected JsonException")
        } catch (e: JsonException) {
            // expected
        }
    }

    @Test
    fun `throws JsonException on trailing content`() {
        try {
            JsonParser.parse("""{"a":1} garbage""")
            fail("Expected JsonException")
        } catch (e: JsonException) {
            // expected
        }
    }

    @Test
    fun `round trips write then parse`() {
        val original = mapOf("action" to "open_transfer", "parameters" to mapOf("amount" to 500))
        val json = JsonWriter.write(original)
        val parsed = JsonParser.parse(json) as JsonValue.JsonObject
        assertEquals("open_transfer", parsed.string("action"))
        val params = parsed.objectOrNull("parameters")!!
        assertEquals(500.0, params.number("amount"))
    }

    @Test
    fun `writer escapes special characters`() {
        val json = JsonWriter.write(mapOf("text" to "line1\nline2\"quoted\""))
        val parsed = JsonParser.parse(json) as JsonValue.JsonObject
        assertEquals("line1\nline2\"quoted\"", parsed.string("text"))
    }
}
