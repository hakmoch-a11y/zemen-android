package com.zemenai.sdk.core

import com.zemenai.sdk.core.model.ActionOutcome
import com.zemenai.sdk.core.model.ChatTurn
import com.zemenai.sdk.core.network.FakeHttpTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZemenAiCoreTest {

    @Test
    fun `sendMessage interprets a plain-prose response as Text`() = runBlocking {
        val transport = FakeHttpTransport.respondingWith(
            200,
            """{"success":true,"data":{"answer":"A savings account earns interest.","sources":[]},"timestamp":"now"}"""
        )
        val core = ZemenAiCore("https://api.test", "key", transport)

        val turn = core.sendMessage("What is a savings account?")

        assertTrue(turn is ChatTurn.Text)
    }

    @Test
    fun `handleAction dispatches to the registered handler when the server approves`() = runBlocking {
        val transport = FakeHttpTransport.respondingWith(
            200,
            """{"success":true,"data":{"valid":true,"reasons":[],"route":"/transfer"},"timestamp":"now"}"""
        )
        val core = ZemenAiCore("https://api.test", "key", transport)

        var receivedParams: Map<String, Any?>? = null
        core.registerAction("open_transfer") { params -> receivedParams = params }

        val outcome = core.handleAction(
            com.zemenai.sdk.core.model.ActionProposal("open_transfer", mapOf("amount" to 500.0))
        )

        assertTrue(outcome is ActionOutcome.Dispatched)
        assertEquals("/transfer", (outcome as ActionOutcome.Dispatched).route)
        assertEquals(mapOf("amount" to 500.0), receivedParams)
    }

    @Test
    fun `handleAction never dispatches when the server rejects the proposal`() = runBlocking {
        val transport = FakeHttpTransport.respondingWith(
            200,
            """{"success":true,"data":{"valid":false,"reasons":["unexpected parameter \"x\""]},"timestamp":"now"}"""
        )
        val core = ZemenAiCore("https://api.test", "key", transport)

        var handlerCalled = false
        core.registerAction("open_transfer") { handlerCalled = true }

        val outcome = core.handleAction(
            com.zemenai.sdk.core.model.ActionProposal("open_transfer", mapOf("x" to "smuggled"))
        )

        assertTrue(outcome is ActionOutcome.Rejected)
        assertEquals(false, handlerCalled)
        assertTrue((outcome as ActionOutcome.Rejected).reasons[0].contains("unexpected parameter"))
    }

    @Test
    fun `handleAction reports NoHandlerRegistered when the server approves but nothing is registered`() =
        runBlocking {
            // This is the case worth distinguishing precisely: the SERVER
            // said this action is fine, but the HOST APP has an
            // integration bug (forgot to call registerAction). It's not
            // the same failure mode as a rejected proposal, and a host
            // app debugging "why doesn't my action run" needs to be able
            // to tell these two apart.
            val transport = FakeHttpTransport.respondingWith(
                200,
                """{"success":true,"data":{"valid":true,"reasons":[],"route":"/transfer"},"timestamp":"now"}"""
            )
            val core = ZemenAiCore("https://api.test", "key", transport)
            // deliberately no registerAction call

            val outcome = core.handleAction(
                com.zemenai.sdk.core.model.ActionProposal("open_transfer", mapOf("amount" to 500.0))
            )

            assertTrue(outcome is ActionOutcome.NoHandlerRegistered)
        }

    @Test
    fun `an AI proposing an action is never sufficient on its own to dispatch it`() = runBlocking {
        // Structural guarantee: even though a handler IS registered, if
        // the server-side Action Engine rejects the proposal, the
        // handler must never run. This is the client-side half of "AI
        // must never execute transactions" from the spec.
        val transport = FakeHttpTransport.respondingWith(
            200,
            """{"success":true,"data":{"valid":false,"reasons":["not registered"]},"timestamp":"now"}"""
        )
        val core = ZemenAiCore("https://api.test", "key", transport)

        var handlerCalled = false
        core.registerAction("delete_everything") { handlerCalled = true }

        core.handleAction(com.zemenai.sdk.core.model.ActionProposal("delete_everything", emptyMap()))

        assertEquals(false, handlerCalled)
    }

    @Test
    fun `loadConfig returns the parsed configuration`() = runBlocking {
        val transport = FakeHttpTransport.respondingWith(
            200,
            """{"success":true,"data":{"applicationId":"app-1","applicationName":"Zemen Mobile","platform":"ANDROID","bankId":"bank-1","bankName":"Zemen Bank","status":"ACTIVE"},"timestamp":"now"}"""
        )
        val core = ZemenAiCore("https://api.test", "key", transport)

        val config = core.loadConfig()

        assertEquals("Zemen Mobile", config.applicationName)
        assertTrue(config.isActive)
    }

    @Test
    fun `isActionRegistered reflects the core's own registry state`() {
        val core = ZemenAiCore("https://api.test", "key", FakeHttpTransport.respondingWith(200, "{}"))
        assertEquals(false, core.isActionRegistered("open_transfer"))
        core.registerAction("open_transfer") { }
        assertEquals(true, core.isActionRegistered("open_transfer"))
    }
}
