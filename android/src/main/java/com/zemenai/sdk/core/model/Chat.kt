package com.zemenai.sdk.core.model

import com.zemenai.sdk.core.json.JsonValue

data class ChatSource(
    val filename: String,
    val content: String,
    val score: Double
) {
    companion object {
        fun fromJson(json: JsonValue.JsonObject): ChatSource = ChatSource(
            filename = json.requireString("filename"),
            content = json.requireString("content"),
            score = json.number("score") ?: 0.0
        )
    }
}

/**
 * Raw response from POST /sdk/ask, before interpretation.
 *
 * backend-gateway's Action Engine (SdkService.askValidated, Phase 8) may
 * resolve the reply as either shape:
 *   - text:   {"type":"TEXT","answer":"...","sources":[...]}
 *   - action: {"type":"ACTION","action":"...","route":"...","parameters":{...},"sources":[...]}
 * The ACTION shape carries no "answer" field at all, so [answer] must be
 * nullable and [raw] is kept so [ChatInterpreter] can recognize an action
 * from the top-level object rather than assuming "answer" always exists.
 */
data class ChatAnswer(
    val answer: String?,
    val sources: List<ChatSource>,
    val raw: JsonValue.JsonObject
) {
    companion object {
        fun fromJson(json: JsonValue.JsonObject): ChatAnswer {
            val sourcesJson = json.array("sources") ?: emptyList()
            return ChatAnswer(
                answer = json.string("answer"),
                sources = sourcesJson.filterIsInstance<JsonValue.JsonObject>()
                    .map { ChatSource.fromJson(it) },
                raw = json
            )
        }
    }
}

/**
 * What a chat turn actually means once interpreted: either a plain-text
 * answer to show the user, or a structured action proposal to run
 * through validation before dispatching to a generic host handler.
 *
 * backend-gateway's Action Engine (Phase 8) resolves action proposals
 * server-side and returns them as the top-level response object itself
 * (see ChatAnswer's doc comment) — see ChatInterpreter for how both that
 * shape and the older "JSON embedded inside answer" shape are handled.
 */
sealed class ChatTurn {
    data class Text(val message: String, val sources: List<ChatSource>) : ChatTurn()
    data class Action(val proposal: ActionProposal, val sources: List<ChatSource>) : ChatTurn()
}
