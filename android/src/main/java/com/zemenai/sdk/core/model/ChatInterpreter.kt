package com.zemenai.sdk.core.model

import com.zemenai.sdk.core.json.JsonParser
import com.zemenai.sdk.core.network.ZemenApiException

object ChatInterpreter {
    /**
     * Turns a raw [ChatAnswer] into a [ChatTurn].
     *
     * Two action shapes are recognized, in order:
     *  1. (current) backend-gateway's Action Engine already resolved the
     *     proposal server-side and returned it as the *top-level* response
     *     object — {"type":"ACTION","action":...,"parameters":{...}} — with
     *     no "answer" field at all.
     *  2. (legacy/back-compat) an older or alternate deployment instead
     *     embeds that same JSON *inside* the "answer" string. Any parse
     *     failure here is the normal case — `answer` is almost always
     *     plain prose — and falls through to [ChatTurn.Text] rather than
     *     surfacing as an error.
     *
     * A host app should never see a JSON parse exception from calling
     * this: a response that is neither a recognizable action nor plain
     * text raises [ZemenApiException.MalformedResponse] instead, which
     * ChatActivity (and any host app calling ZemenAI.ask directly) already
     * knows how to show as a graceful, non-crashing message.
     */
    fun interpret(answer: ChatAnswer): ChatTurn {
        ActionProposal.tryParse(answer.raw)?.let { return ChatTurn.Action(it, answer.sources) }

        val text = answer.answer
        if (text != null) {
            val nested = runCatching { JsonParser.parse(text) }
                .getOrNull()
                ?.let { ActionProposal.tryParse(it) }

            return if (nested != null) {
                ChatTurn.Action(nested, answer.sources)
            } else {
                ChatTurn.Text(text, answer.sources)
            }
        }

        val type = answer.raw.string("type") ?: "TEXT"
        throw ZemenApiException.MalformedResponse(
            "Response had type=\"$type\" but was neither a recognizable action nor a text reply with an \"answer\" field"
        )
    }
}
