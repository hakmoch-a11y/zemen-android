package com.zemenai.sdk.core.actions

import java.util.concurrent.ConcurrentHashMap

/**
 * A handler a host app registers for one action name. Matches the
 * spec's example exactly:
 * ```
 * ZemenAI.registerAction("open_transfer") { params ->
 *     openTransferScreen(params)
 * }
 * ```
 * `fun interface` (a Kotlin SAM) is what makes the trailing-lambda call
 * syntax above work — `registerAction(name, handler)` where `handler` is
 * this interface's single abstract method.
 */
fun interface ActionHandler {
    fun handle(parameters: Map<String, Any?>)
}

/**
 * Thread-safe registry mapping action names to their handlers.
 * ConcurrentHashMap rather than a plain MutableMap+synchronized block:
 * registration typically happens once at app startup (main thread) while
 * dispatch happens later from a background/coroutine context handling a
 * chat response — a plain map without synchronization would be a data
 * race between those two call sites.
 */
class ActionRegistry {
    private val handlers = ConcurrentHashMap<String, ActionHandler>()

    fun register(name: String, handler: ActionHandler) {
        require(name.isNotBlank()) { "Action name must not be blank" }
        handlers[name] = handler
    }

    fun unregister(name: String) {
        handlers.remove(name)
    }

    fun isRegistered(name: String): Boolean = handlers.containsKey(name)

    /** Returns false (does not throw) if [name] has no registered
     * handler — an AI proposing an action the host app never registered
     * a handler for is treated as "nothing to do," not a crash. */
    fun dispatch(name: String, parameters: Map<String, Any?>): Boolean {
        val handler = handlers[name] ?: return false
        handler.handle(parameters)
        return true
    }

    fun clear() {
        handlers.clear()
    }
}
