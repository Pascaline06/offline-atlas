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
        engine.setSystemPrompt("You answer offline research questions using only numbered evidence. Cite each factual sentence. Explain supported causes or mechanisms clearly. Never invent a causal link, public reaction, source, venue, hours, ranking, or current fact. If the excerpts do not answer the question, say so. Keep answers below 100 words and end with a full stop.")
        loaded = true
    }

    fun answer(question: String, evidence: String, onProgress: Consumer<String>): String = runBlocking {
        check(loaded) { "Select a GGUF model first" }
        rejectedDraft = null
        var mostRecentDraft = ""
        try {
            withTimeout(120_000) {
                val comparison = evidence.contains("[2]")
                val mechanism = question.trimStart().startsWith("how ", ignoreCase = true)
                val instruction = if (comparison)
                    "Compare one shared attribute covered by BOTH excerpts. Write one sentence using 'whereas' or 'while' to state each side of the difference. Place [1] AFTER the fact from excerpt 1 and [2] AFTER the fact from excerpt 2. Do not output URLs, source labels, snapshot dates, or unrelated claims."
                else if (mechanism)
                    "No introduction or heading. Write exactly one complete sentence explaining the mechanism explicitly stated in the excerpt. Place [1] after the fact and finish with a period. Do not add an effect absent from the excerpt."
                else "Begin immediately with the answer, with no introduction or heading. Write two or three complete sentences. State only causal steps expressly linked in the excerpt; do not infer public reactions or consequences from adjacent facts. Cite [1] before the full stop of every sentence. Use only the supplied excerpt."
                val first = generate(question, evidence, instruction, 256, onProgress)
                mostRecentDraft = first
                val review = AnswerReview.check(first, evidence, comparison)
                if (review.accepted()) return@withTimeout review.text

                onProgress.accept("First draft failed the evidence check (${review.reason}). Retrying once…")
                val retryInstruction = if (comparison)
                    "Write exactly one sentence in this form: 'Subject A uses X [1], whereas subject B uses Y [2].' Replace X and Y with supported facts about the same attribute. Citations must come AFTER the facts. Do not output URLs, dates, or source labels."
                else if (mechanism)
                    "No introduction. Write exactly one sentence copied closely from the excerpt that answers the question. Put [1] before the final period."
                else "No introduction or heading. Begin with one fact explicitly stated in the excerpt, place [1] after that fact, and end the sentence with a period. You may add one more equally sourced sentence. Do not add a causal link or reaction the excerpt does not state."
                val retry = generate(question, evidence, retryInstruction, 192, onProgress)
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
                if (result.length==it.length || now-lastUpdate>=1200L) {
                    onProgress.accept(result.toString())
                    lastUpdate=now
                }
            }
        return result.toString()
    }

    fun close() { if (loaded) engine.destroy() }
}
