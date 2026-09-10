package com.zemenai.sdk.core.model

import com.zemenai.sdk.core.json.JsonParser

object ChatInterpreter {
    /**
     * Turns a raw [ChatAnswer] into a [ChatTurn]. The `answer` string is
     * treated as a possible ACTION proposal only if it parses as valid
     * JSON matching that exact shape — any parse failure (which is the
     * normal case: `answer` is almost always plain prose) falls through
     * to [ChatTurn.Text] rather than surfacing as an error. A host app
     * should never see a JSON parse exception from calling this.
     */
    fun interpret(answer: ChatAnswer): ChatTurn {
        val proposal = runCatching { JsonParser.parse(answer.answer) }
            .getOrNull()
            ?.let { ActionProposal.tryParse(it) }

        return if (proposal != null) {
            ChatTurn.Action(proposal, answer.sources)
        } else {
            ChatTurn.Text(answer.answer, answer.sources)
        }
    }
}
