package com.zemenai.sdk.core.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionRegistryTest {

    @Test
    fun `dispatch invokes the registered handler with the given parameters`() {
        val registry = ActionRegistry()
        var received: Map<String, Any?>? = null
        registry.register("open_transfer") { params -> received = params }

        val dispatched = registry.dispatch("open_transfer", mapOf("amount" to 500))

        assertTrue(dispatched)
        assertEquals(mapOf("amount" to 500), received)
    }

    @Test
    fun `dispatch returns false for an unregistered action instead of throwing`() {
        val registry = ActionRegistry()
        val dispatched = registry.dispatch("nonexistent_action", emptyMap())
        assertFalse(dispatched)
    }

    @Test
    fun `isRegistered reflects registration state`() {
        val registry = ActionRegistry()
        assertFalse(registry.isRegistered("open_transfer"))
        registry.register("open_transfer") { }
        assertTrue(registry.isRegistered("open_transfer"))
    }

    @Test
    fun `unregister removes a handler so dispatch falls back to false`() {
        val registry = ActionRegistry()
        registry.register("open_transfer") { }
        registry.unregister("open_transfer")

        assertFalse(registry.isRegistered("open_transfer"))
        assertFalse(registry.dispatch("open_transfer", emptyMap()))
    }

    @Test
    fun `registering the same name twice replaces the previous handler`() {
        val registry = ActionRegistry()
        var callCount = 0
        registry.register("open_transfer") { callCount += 1 }
        registry.register("open_transfer") { callCount += 100 }

        registry.dispatch("open_transfer", emptyMap())

        assertEquals(100, callCount)
    }

    @Test
    fun `clear removes every registered handler`() {
        val registry = ActionRegistry()
        registry.register("a") { }
        registry.register("b") { }

        registry.clear()

        assertFalse(registry.isRegistered("a"))
        assertFalse(registry.isRegistered("b"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `registering a blank name throws`() {
        ActionRegistry().register("   ") { }
    }

    @Test
    fun `multiple distinct actions can be registered independently`() {
        val registry = ActionRegistry()
        val calls = mutableListOf<String>()
        registry.register("open_transfer") { calls.add("transfer") }
        registry.register("open_atm_locator") { calls.add("atm") }

        registry.dispatch("open_atm_locator", emptyMap())
        registry.dispatch("open_transfer", emptyMap())

        assertEquals(listOf("atm", "transfer"), calls)
    }
}
