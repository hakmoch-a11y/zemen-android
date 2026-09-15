package com.zemenai.sdk.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ZemenApiClientTest {

    @Test
    fun `getConfig sends the API key as X-Api-Key and unwraps the data envelope`() = runBlocking {
        val transport = FakeHttpTransport.respondingWith(
            200,
            """{"success":true,"data":{"applicationId":"app-1","applicationName":"Zemen Mobile","platform":"ANDROID","bankId":"bank-1","bankName":"Zemen Bank","status":"ACTIVE"},"timestamp":"now"}"""
        )
        val client = ZemenApiClient("https://api.test", "zemen_live_secret", transport)

        val config = client.getConfig()

        assertEquals("app-1", config.applicationId)
        assertEquals("Zemen Bank", config.bankName)
        assertEquals("zemen_live_secret", transport.lastRequest?.headers?.get("X-Api-Key"))
    }

    @Test
    fun `ask sends the question and never leaks the API key into the request body`() = runBlocking {
        val transport = FakeHttpTransport.respondingWith(
            200,
            """{"success":true,"data":{"answer":"You need a 650 credit score.","sources":[]},"timestamp":"now"}"""
        )
        val client = ZemenApiClient("https://api.test", "zemen_live_secret", transport)

        val answer = client.ask("How can I get a loan?")

        assertEquals("You need a 650 credit score.", answer.answer)
        val body = transport.lastRequest?.body ?: ""
        assertTrue(body.contains("How can I get a loan?"))
        assertTrue("API key must never appear in the request body", !body.contains("zemen_live_secret"))
    }

    @Test
    fun `ask parses backend-gateway's ACTION envelope, which has no answer field, without throwing`() = runBlocking {
        // Reproduces the exact response shape backend-gateway's
        // SdkService.askValidated sends for a resolved action (Phase 8) —
        // regression test for the crash where ChatAnswer.fromJson required
        // "answer" unconditionally.
        val transport = FakeHttpTransport.respondingWith(
            200,
            """{"success":true,"data":{"type":"ACTION","action":"open_transfer","route":"/transfer","parameters":{"amount":500},"sources":[]},"timestamp":"now"}"""
        )
        val client = ZemenApiClient("https://api.test", "zemen_live_secret", transport)

        val answer = client.ask("Send 500 to my savings")

        assertEquals(null, answer.answer)
        assertEquals("ACTION", answer.raw.string("type"))
    }

    @Test
    fun `validateAction posts action and parameters and parses the verdict`() = runBlocking {
        val transport = FakeHttpTransport.respondingWith(
            200,
            """{"success":true,"data":{"valid":true,"reasons":[],"route":"/transfer"},"timestamp":"now"}"""
        )
        val client = ZemenApiClient("https://api.test", "zemen_live_secret", transport)

        val result = client.validateAction("open_transfer", mapOf("amount" to 500))

        assertTrue(result.valid)
        assertEquals("/transfer", result.route)
        val body = transport.lastRequest?.body ?: ""
        assertTrue(body.contains("open_transfer"))
        assertTrue(body.contains("500"))
    }

    @Test
    fun `a 401 response throws Unauthorized with the server's message`() = runBlocking {
        val transport = FakeHttpTransport.respondingWith(
            401,
            """{"statusCode":401,"path":"/sdk/config","timestamp":"now","message":"Invalid API key","error":"Unauthorized"}"""
        )
        val client = ZemenApiClient("https://api.test", "wrong_key", transport)

        try {
            client.getConfig()
            fail("Expected ZemenApiException.Unauthorized")
        } catch (e: ZemenApiException.Unauthorized) {
            assertEquals("Invalid API key", e.message)
        }
    }

    @Test
    fun `a class-validator array message is joined into a readable string`() = runBlocking {
        val transport = FakeHttpTransport.respondingWith(
            400,
            """{"statusCode":400,"path":"/sdk/ask","timestamp":"now","message":["question must be a string","question should not be empty"],"error":"Bad Request"}"""
        )
        val client = ZemenApiClient("https://api.test", "key", transport)

        try {
            client.ask("")
            fail("Expected ZemenApiException.HttpError")
        } catch (e: ZemenApiException.HttpError) {
            assertEquals(400, e.statusCode)
            assertTrue(e.message!!.contains("question must be a string"))
        }
    }

    @Test
    fun `an underlying transport exception surfaces as NetworkError, not a crash`() = runBlocking {
        val transport = FakeHttpTransport.throwing(RuntimeException("connection refused"))
        val client = ZemenApiClient("https://api.test", "key", transport)

        try {
            client.getConfig()
            fail("Expected ZemenApiException.NetworkError")
        } catch (e: ZemenApiException.NetworkError) {
            assertTrue(e.message!!.contains("connection refused"))
        }
    }

    @Test
    fun `a malformed success body throws MalformedResponse rather than crashing`() = runBlocking {
        val transport = FakeHttpTransport.respondingWith(200, "not json at all")
        val client = ZemenApiClient("https://api.test", "key", transport)

        try {
            client.getConfig()
            fail("Expected ZemenApiException.MalformedResponse")
        } catch (e: ZemenApiException.MalformedResponse) {
            // expected
        }
    }

    @Test
    fun `a response missing the data envelope throws MalformedResponse`() = runBlocking {
        val transport = FakeHttpTransport.respondingWith(200, """{"success":true,"timestamp":"now"}""")
        val client = ZemenApiClient("https://api.test", "key", transport)

        try {
            client.getConfig()
            fail("Expected ZemenApiException.MalformedResponse")
        } catch (e: ZemenApiException.MalformedResponse) {
            // expected
        }
    }
}
