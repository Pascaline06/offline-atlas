package org.offlineatlas

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.util.function.Consumer

/** Local GGUF inference. Called only from MainActivity's single background worker. */
class ModelRunner(context: Context) {
    private val engine = AiChat.getInferenceEngine(context)
    private var loaded = false
    private var rejectedDraft: String? = null

    fun rejectedDraft(): String? = rejectedDraft

    fun load(path: String) = runBlocking {
        if (loaded || engine.state.value is InferenceEngine.State.ModelReady) { engine.cleanUp(); loaded = false }
        try {
            withTimeout(30_000) { engine.state.first { it is InferenceEngine.State.Initialized || it is InferenceEngine.State.Error } }
        } catch (_: TimeoutCancellationException) {
            throw IllegalStateException("Native inference engine did not initialize within 30 seconds")
        }
        val state = engine.state.value
        if (state is InferenceEngine.State.Error) throw IllegalStateException("Native inference engine failed to initialize: ${state.exception.javaClass.simpleName}: ${state.exception.message ?: "no detail"}")
        engine.loadModel(path)
        engine.setSystemPrompt("You answer offline research questions using only numbered evidence. Cite each factual sentence. Never invent a source, venue, hours, ranking, or current fact. If the excerpts do not answer the question, say so. Keep answers below 65 words and end with a full stop.")
        loaded = true
    }

    fun answer(question: String, evidence: String, onProgress: Consumer<String>): String = runBlocking {
        check(loaded) { "Select a GGUF model first" }
        rejectedDraft = null
        var mostRecentDraft = ""
        try {
            withTimeout(120_000) {
                val comparison = evidence.contains("[2]")
                val instruction = if (comparison)
                    "Compare one shared attribute covered by BOTH excerpts. Write one sentence using 'whereas' or 'while' to state each side of the difference. Place [1] AFTER the fact from excerpt 1 and [2] AFTER the fact from excerpt 2. Do not output URLs, source labels, snapshot dates, or unrelated claims."
                else "Answer in one or two complete sentences. Cite [1]. Use only the supplied excerpt."
                val first = generate(question, evidence, instruction, 192, onProgress)
                mostRecentDraft = first
                val review = AnswerReview.check(first, evidence, comparison)
                if (review.accepted()) return@withTimeout review.text

                onProgress.accept("First draft failed the evidence check (${review.reason}). Retrying once…")
                val retryInstruction = if (comparison)
                    "Write exactly one sentence in this form: 'Subject A uses X [1], whereas subject B uses Y [2].' Replace X and Y with supported facts about the same attribute. Citations must come AFTER the facts. Do not output URLs, dates, or source labels."
                else "Write one short factual sentence supported by the excerpt, cite [1], and finish with a period."
                val retry = generate(question, evidence, retryInstruction, 128, onProgress)
                mostRecentDraft = retry
                val secondReview = AnswerReview.check(retry, evidence, comparison)
                if (secondReview.accepted()) secondReview.text
                else {
                    rejectedDraft = "First attempt (${review.reason}):\n${first.take(1000)}\n\nRetry (${secondReview.reason}):\n${retry.take(1000)}"
                    "Local model answer rejected (${secondReview.reason}). The cited source summary above is available; the model has not answered this question."
                }
            }
        } catch (_: TimeoutCancellationException) {
            rejectedDraft = if (mostRecentDraft.isNotBlank()) mostRecentDraft.take(1000) else null
            "Local model answer rejected (two-minute limit). Use the cited source summary above."
        }
    }

    private suspend fun generate(question: String, evidence: String, instruction: String,
                                 limit: Int, onProgress: Consumer<String>): String {
        val result = StringBuilder()
        var lastUpdate = 0L
        engine.sendUserPrompt("Question: $question\n\nLocal evidence:\n$evidence\n\n$instruction\nAnswer:", limit)
            .collect {
                result.append(it)
                val now = android.os.SystemClock.elapsedRealtime()
                if (now-lastUpdate>=1200L) {
                    onProgress.accept(result.toString())
                    lastUpdate=now
                }
            }
        return result.toString()
    }

    fun close() { if (loaded) engine.destroy() }
}
