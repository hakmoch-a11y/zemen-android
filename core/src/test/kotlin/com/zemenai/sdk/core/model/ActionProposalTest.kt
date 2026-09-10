package com.zemenai.sdk.core.model

import com.zemenai.sdk.core.json.JsonParser
import com.zemenai.sdk.core.json.JsonValue
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

    @Test
    fun `plain prose interprets as Text`() {
        val answer = ChatAnswer("A savings account earns interest.", emptyList())
        val turn = ChatInterpreter.interpret(answer)
        assertTrue(turn is ChatTurn.Text)
        assertEquals("A savings account earns interest.", (turn as ChatTurn.Text).message)
    }

    @Test
    fun `a valid ACTION-shaped answer interprets as Action`() {
        val answer = ChatAnswer(
            """{"type":"ACTION","action":"open_transfer","parameters":{"amount":500}}""",
            emptyList()
        )
        val turn = ChatInterpreter.interpret(answer)
        assertTrue(turn is ChatTurn.Action)
        assertEquals("open_transfer", (turn as ChatTurn.Action).proposal.action)
    }

    @Test
    fun `malformed JSON in the answer falls back to Text rather than throwing`() {
        val answer = ChatAnswer("{not valid json at all", emptyList())
        val turn = ChatInterpreter.interpret(answer)
        assertTrue(turn is ChatTurn.Text)
    }

    @Test
    fun `sources are preserved through interpretation for both Text and Action turns`() {
        val source = ChatSource("loans.txt", "content", 0.9)
        val textTurn = ChatInterpreter.interpret(ChatAnswer("prose", listOf(source)))
        assertEquals(listOf(source), (textTurn as ChatTurn.Text).sources)

        val actionTurn = ChatInterpreter.interpret(
            ChatAnswer("""{"type":"ACTION","action":"x","parameters":{}}""", listOf(source))
        )
        assertEquals(listOf(source), (actionTurn as ChatTurn.Action).sources)
    }
}
