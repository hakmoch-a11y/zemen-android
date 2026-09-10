package com.zemenai.sdk.core

import com.zemenai.sdk.core.actions.ActionHandler
import com.zemenai.sdk.core.actions.ActionRegistry
import com.zemenai.sdk.core.model.ZemenActionHandler
import com.zemenai.sdk.core.model.ZemenAction
import com.zemenai.sdk.core.model.ActionOutcome
import com.zemenai.sdk.core.model.ActionProposal
import com.zemenai.sdk.core.model.ChatInterpreter
import com.zemenai.sdk.core.model.ChatTurn
import com.zemenai.sdk.core.model.ZemenConfig
import com.zemenai.sdk.core.network.HttpTransport
import com.zemenai.sdk.core.network.ZemenApiClient

/**
 * The platform-agnostic heart of the SDK. Has no knowledge of Android
 * Context, Activities, or Views — the `android` module's `ZemenAI`
 * facade wraps exactly one instance of this per app process. Kept
 * separate specifically so it's testable on a plain JVM (see
 * ZemenAiCoreTest) without any Android test framework or emulator.
 */
class ZemenAiCore(
    baseUrl: String,
    apiKey: String,
    transport: HttpTransport
) {
    private val apiClient = ZemenApiClient(baseUrl, apiKey, transport)
    private val actionRegistry = ActionRegistry()
    @Volatile
    private var genericActionHandler: ZemenActionHandler? = null
    @Volatile private var lastConfig: ZemenConfig? = null

    suspend fun loadConfig(): ZemenConfig = apiClient.getConfig().also { lastConfig = it }

    /** Sends [question] and returns the interpreted result — either
     * plain text to display, or a proposed action ready for
     * [handleAction]. Never dispatches an action automatically; that is
     * always a separate, explicit step. */
    suspend fun sendMessage(question: String): ChatTurn {
        val answer = apiClient.ask(question)
        return ChatInterpreter.interpret(answer)
    }

    /**
     * Validates [proposal] against the live Action Engine
     * (`POST /sdk/actions/validate`) and, only if approved, dispatches
     * to whatever handler the host app registered for that action name.
     * The AI proposing an action is never sufficient on its own to run
     * it — this is the client-side half of the same principle
     * backend-gateway's Action Engine enforces server-side: nothing
     * executes without passing validation first.
     */
    suspend fun handleAction(proposal: ActionProposal): ActionOutcome {
        val result = apiClient.validateAction(proposal.action, proposal.parameters)
        if (!result.valid) {
            return ActionOutcome.Rejected(result.reasons)
        }
        if (genericActionHandler != null) {
            val configured = lastConfig?.actions?.firstOrNull { it.name == proposal.action }
            genericActionHandler?.handle(
                ZemenAction(
                    id = configured?.id,
                    name = proposal.action,
                    route = result.route ?: configured?.route ?: proposal.action,
                    parameters = proposal.parameters,
                    metadata = configured?.metadata ?: emptyMap(),
                )
            )
            return ActionOutcome.Dispatched(result.route)
        }

        val dispatched = actionRegistry.dispatch(proposal.action, proposal.parameters)
        return if (dispatched) {
            ActionOutcome.Dispatched(result.route)
        } else {
            ActionOutcome.NoHandlerRegistered
        }
    }

    fun setActionHandler(handler: ZemenActionHandler?) {
        genericActionHandler = handler
    }

    fun registerAction(name: String, handler: ActionHandler) {
        actionRegistry.register(name, handler)
    }

    fun unregisterAction(name: String) {
        actionRegistry.unregister(name)
    }

    fun isActionRegistered(name: String): Boolean = actionRegistry.isRegistered(name)
}
