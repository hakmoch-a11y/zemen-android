package com.zemenai.sdk.core.model

import com.zemenai.sdk.core.json.JsonParser
import com.zemenai.sdk.core.json.JsonValue
import com.zemenai.sdk.core.network.ZemenApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionProposalTest {

    @Test
    fun `parses the exact shape from the spec's example`() {
        val json = JsonParser.parse(
            """{"type":"ACTION","action":"open_transfer","parameters":{"amount":500}}"""
        )
        val proposal = ActionProposal.tryParse(json)

        assertEquals("open_transfer", proposal?.action)
        assertEquals(500.0, proposal?.parameters?.get("amount"))
    }

    @Test
    fun `returns null for plain prose, not an exception`() {
        val json = JsonValue.JsonString("A savings account earns interest.")
        assertNull(ActionProposal.tryParse(json))
    }

    @Test
    fun `returns null when type field is missing`() {
        val json = JsonParser.parse("""{"action":"open_transfer","parameters":{}}""")
        assertNull(ActionProposal.tryParse(json))
    }

    @Test
    fun `returns null when type is not exactly ACTION`() {
        val json = JsonParser.parse(
            """{"type":"NOT_AN_ACTION","action":"open_transfer","parameters":{}}"""
        )
        assertNull(ActionProposal.tryParse(json))
    }

    @Test
    fun `returns null when action field is missing`() {
        val json = JsonParser.parse("""{"type":"ACTION","parameters":{"amount":500}}""")
        assertNull(ActionProposal.tryParse(json))
    }

    @Test
    fun `defaults to empty parameters when parameters field is missing`() {
        val json = JsonParser.parse("""{"type":"ACTION","action":"open_atm_locator"}""")
        val proposal = ActionProposal.tryParse(json)
        assertEquals(emptyMap<String, Any?>(), proposal?.parameters)
    }

    @Test
    fun `converts nested parameter types correctly`() {
        val json = JsonParser.parse(
            """{"type":"ACTION","action":"x","parameters":{"amount":500,"note":"hi","urgent":true,"tags":["a","b"]}}"""
        )
        val proposal = ActionProposal.tryParse(json)!!
        assertEquals(500.0, proposal.parameters["amount"])
        assertEquals("hi", proposal.parameters["note"])
        assertEquals(true, proposal.parameters["urgent"])
        assertEquals(listOf("a", "b"), proposal.parameters["tags"])
    }
}

class ChatInterpreterTest {

    private fun textAnswer(answer: String, sources: List<ChatSource> = emptyList()) = ChatAnswer.fromJson(
        JsonParser.parse(
            """{"type":"TEXT","answer":${JsonWriterEscape.string(answer)},"sources":[]}"""
        ) as JsonValue.JsonObject
    ).let { it.copy(sources = sources) }

    @Test
    fun `plain prose interprets as Text`() {
        val answer = textAnswer("A savings account earns interest.")
        val turn = ChatInterpreter.interpret(answer)
        assertTrue(turn is ChatTurn.Text)
        assertEquals("A savings account earns interest.", (turn as ChatTurn.Text).message)
    }

    @Test
    fun `backend-gateway's current top-level ACTION shape (no answer field) interprets as Action without crashing`() {
        // This is the exact shape SdkService.askValidated in backend-gateway
        // actually returns for a resolved action (Phase 8) — note there is
        // deliberately NO "answer" key. Before this fix, ChatAnswer.fromJson
        // required "answer" unconditionally and threw an uncaught
        // JsonException here, which crashed the host app.
        val json = JsonParser.parse(
            """{"type":"ACTION","action":"open_transfer","route":"/transfer","parameters":{"amount":500},"sources":[]}"""
        ) as JsonValue.JsonObject
        val answer = ChatAnswer.fromJson(json)

        val turn = ChatInterpreter.interpret(answer)

        assertTrue(turn is ChatTurn.Action)
        assertEquals("open_transfer", (turn as ChatTurn.Action).proposal.action)
        assertEquals(500.0, turn.proposal.parameters["amount"])
    }

    @Test
    fun `legacy ACTION JSON embedded inside the answer string still interprets as Action`() {
        val json = JsonParser.parse(
            """{"type":"TEXT","answer":"{\"type\":\"ACTION\",\"action\":\"open_transfer\",\"parameters\":{\"amount\":500}}","sources":[]}"""
        ) as JsonValue.JsonObject
        val turn = ChatInterpreter.interpret(ChatAnswer.fromJson(json))

        assertTrue(turn is ChatTurn.Action)
        assertEquals("open_transfer", (turn as ChatTurn.Action).proposal.action)
    }

    @Test
    fun `malformed JSON in the answer falls back to Text rather than throwing`() {
        val answer = textAnswer("{not valid json at all")
        val turn = ChatInterpreter.interpret(answer)
        assertTrue(turn is ChatTurn.Text)
    }

    @Test(expected = ZemenApiException.MalformedResponse::class)
    fun `neither an action nor an answer field raises MalformedResponse instead of crashing`() {
        val json = JsonParser.parse("""{"type":"TEXT","sources":[]}""") as JsonValue.JsonObject
        ChatInterpreter.interpret(ChatAnswer.fromJson(json))
    }

    @Test
    fun `sources are preserved through interpretation for both Text and Action turns`() {
        val source = ChatSource("loans.txt", "content", 0.9)
        val textTurn = ChatInterpreter.interpret(textAnswer("prose", listOf(source)))
        assertEquals(listOf(source), (textTurn as ChatTurn.Text).sources)

        val actionJson = JsonParser.parse(
            """{"type":"ACTION","action":"x","parameters":{},"sources":[]}"""
        ) as JsonValue.JsonObject
        val actionTurn = ChatInterpreter.interpret(ChatAnswer.fromJson(actionJson).copy(sources = listOf(source)))
        assertEquals(listOf(source), (actionTurn as ChatTurn.Action).sources)
    }
}

/** Minimal helper so test literals can safely embed arbitrary strings as
 * JSON string values without pulling in JsonWriter for a handful of tests. */
private object JsonWriterEscape {
    fun string(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
