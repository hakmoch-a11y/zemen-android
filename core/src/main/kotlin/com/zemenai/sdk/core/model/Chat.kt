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

/** Raw response from POST /sdk/ask, before interpretation. */
data class ChatAnswer(
    val answer: String,
    val sources: List<ChatSource>
) {
    companion object {
        fun fromJson(json: JsonValue.JsonObject): ChatAnswer {
            val sourcesJson = json.array("sources") ?: emptyList()
            return ChatAnswer(
                answer = json.requireString("answer"),
                sources = sourcesJson.filterIsInstance<JsonValue.JsonObject>()
                    .map { ChatSource.fromJson(it) }
            )
        }
    }
}

/**
 * What a chat turn actually means once interpreted: either a plain-text
 * answer to show the user, or a structured action proposal to run
 * through validation before dispatching to a generic host handler.
 *
 * ai-service's prompt does not yet instruct Gemini to emit the
 * `{"type":"ACTION",...}` shape from the spec (see backend-gateway's
 * Phase 7 README, "What this deliberately does NOT do yet") — so in
 * practice every ChatAnswer today interprets as [Text]. [Action] exists
 * so the SDK's dispatch pipeline (see ChatInterpreter) is ready the
 * moment that prompt work happens, without an SDK version bump.
 */
sealed class ChatTurn {
    data class Text(val message: String, val sources: List<ChatSource>) : ChatTurn()
    data class Action(val proposal: ActionProposal, val sources: List<ChatSource>) : ChatTurn()
}
