package com.zemenai.sdk.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.zemenai.sdk.ZemenAI
import com.zemenai.sdk.core.model.ActionOutcome
import com.zemenai.sdk.core.model.ChatTurn
import com.zemenai.sdk.core.network.ZemenApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log

/**
 * The SDK's built-in chat screen. Deliberately built with plain,
 * long-stable Android View APIs (LinearLayout, ScrollView, EditText,
 * Button) constructed programmatically rather than Jetpack Compose or an
 * XML layout — see the SDK README's "What was and wasn't verified"
 * section for why: this file could not be compiled by any tool in the
 * environment this SDK was built in, so it deliberately sticks to the
 * View APIs least likely to have changed or been misremembered, rather
 * than Compose's larger and more actively evolving surface.
 *
 * A host app that wants full visual control should call ZemenAI.ask and
 * ZemenAI.handleAction directly from its own UI instead of using this
 * screen — this Activity is a convenience, not the only integration
 * path.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var messagesContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var inputField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        addSystemMessage("Ask me anything about your account.")
    }

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        messagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(16)
            setPadding(padding, padding, padding, padding)
        }

        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(messagesContainer)
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val padding = dp(8)
            setPadding(padding, padding, padding, padding)
        }

        inputField = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            hint = "Type a message"
        }

        val sendButton = Button(this).apply {
            text = "Send"
            setOnClickListener { onSendClicked() }
        }

        inputRow.addView(inputField)
        inputRow.addView(sendButton)

        root.addView(scrollView)
        root.addView(inputRow)
        return root
    }

    private fun onSendClicked() {
        val question = inputField.text?.toString()?.trim().orEmpty()
        if (question.isEmpty()) return

        inputField.setText("")
        addUserMessage(question)
        val loadingView = addSystemMessage("Thinking…")

        lifecycleScope.launch {
            try {
                val turn = ZemenAI.ask(question)
                messagesContainer.removeView(loadingView)
                handleTurn(turn)
            } catch (e: ZemenApiException) {
                messagesContainer.removeView(loadingView)
                addSystemMessage(userFacingMessage(e))
            } catch (e: CancellationException) {
                // Structured concurrency needs to see this to cancel properly
                // (e.g. the Activity finishing mid-request) — never swallow it.
                throw e
            } catch (t: Throwable) {
                // Defense in depth: the host app must never crash because of
                // something in the chat round-trip, even a case not yet
                // modeled as a specific ZemenApiException (an unexpected
                // response shape, a future server change, etc.).
                Log.e("ZemenAI", "Unexpected error handling chat turn", t)
                messagesContainer.removeView(loadingView)
                addSystemMessage("Something went wrong. Please try again.")
            }
        }
    }

    private suspend fun handleTurn(turn: ChatTurn) {
        when (turn) {
            is ChatTurn.Text -> addAssistantMessage(turn.message)
            is ChatTurn.Action -> {
                val outcome = ZemenAI.handleAction(turn.proposal)
                when (outcome) {
                    is ActionOutcome.Dispatched -> {
                        // The validated generic action has already been delivered
                        // to the host application's single action handler.
                        addSystemMessage("Opening ${turn.proposal.action.replace('_', ' ')}…")
                        delay(400)
                        finish()
                    }
                    is ActionOutcome.Rejected ->
                        addSystemMessage("That action couldn't be completed: ${outcome.reasons.joinToString("; ")}")
                    is ActionOutcome.NoHandlerRegistered ->
                        addSystemMessage("This app has not installed its generic Zemen action handler yet.")
                }
            }
        }
    }

    private fun userFacingMessage(e: ZemenApiException): String = when (e) {
        is ZemenApiException.NetworkError -> "Couldn't reach the server. Check your connection and try again."
        is ZemenApiException.Unauthorized -> "This app isn't configured correctly. Please contact support."
        is ZemenApiException.HttpError -> "Something went wrong. Please try again."
        is ZemenApiException.MalformedResponse -> "Something went wrong. Please try again."
    }

    private fun addUserMessage(text: String) =
        addBubble(text, alignEnd = true, backgroundColor = Color.parseColor("#DCF0FF"))

    private fun addAssistantMessage(text: String) =
        addBubble(text, alignEnd = false, backgroundColor = Color.parseColor("#F0F0F0"))

    private fun addSystemMessage(text: String): View =
        addBubble(text, alignEnd = false, backgroundColor = Color.TRANSPARENT, italic = true)

    private fun addBubble(
        text: String,
        alignEnd: Boolean,
        backgroundColor: Int,
        italic: Boolean = false
    ): View {
        val bubble = TextView(this).apply {
            this.text = text
            setBackgroundColor(backgroundColor)
            val padding = dp(12)
            setPadding(padding, padding, padding, padding)
            if (italic) {
                setTypeface(typeface, android.graphics.Typeface.ITALIC)
            }
        }
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(8) }
            gravity = if (alignEnd) Gravity.END else Gravity.START
            addView(bubble)
        }
        messagesContainer.addView(row)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        return row
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
