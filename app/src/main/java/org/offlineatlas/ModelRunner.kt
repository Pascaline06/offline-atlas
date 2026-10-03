package org.offlineatlas
import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/** In-process inference: no sockets, APIs, Play Services, or network dependency. */
class ModelRunner(context: Context) {
    private val engine = AiChat.getInferenceEngine(context)
    @Volatile private var loaded = false
    private val active = AtomicReference<Job?>(null)
    private var rejectedDraft: String? = null
    private var usedEvidence = ""
    fun rejectedDraft(): String? = rejectedDraft
    fun evidenceUsed(): String = usedEvidence
    fun isReady(): Boolean = loaded && engine.state.value is InferenceEngine.State.ModelReady
    fun load(path: String) = runBlocking {
        loaded = false
        if (engine.state.value is InferenceEngine.State.ModelReady || engine.state.value is InferenceEngine.State.Error)
            engine.cleanUp()
        withTimeout(30_000) {
            engine.state.first { it is InferenceEngine.State.Initialized || it is InferenceEngine.State.Error }
        }
        val state = engine.state.value
        check(state !is InferenceEngine.State.Error) { "Inference initialization failed: $state" }
        engine.loadModel(path)
        engine.setSystemPrompt(PromptPolicy.SYSTEM)
        loaded = true
    }
    fun cancel() { engine.requestStop(); active.get()?.cancel() }
    fun answer(question: String, evidence: String, comparison: Boolean, onProgress: Consumer<String>): String = runBlocking {
        check(isReady()) { "The local model is not ready" }
        rejectedDraft = null
        usedEvidence = evidence
        active.set(coroutineContext[Job])
        try {
            withTimeout(90_000) {
                fun prompt() = PromptPolicy.answer(question,usedEvidence,comparison)
                while(engine.countTokens(prompt())>2800 && usedEvidence.isNotEmpty()) {
                    val last=Regex("(?m)^\\[\\d+\\] ").findAll(usedEvidence).lastOrNull()
                    usedEvidence=if(last!=null && last.range.first>0) usedEvidence.substring(0,last.range.first).trimEnd() else ""
                }
                if(evidence.isNotBlank() && usedEvidence.isBlank())
                    return@withTimeout "Local model answer rejected (source context exceeds token budget)."
                check(engine.countTokens(prompt())<=2800) { "Question exceeds the model context budget" }
                val draft=generate(prompt(),384,onProgress)
                val reviewed=AnswerReview.check(draft,usedEvidence,comparison && usedEvidence.isNotBlank())
                if(!reviewed.accepted()) {
                    rejectedDraft=draft
                    return@withTimeout "Local model answer rejected (" + reviewed.reason + ")."
                }
                if(usedEvidence.isNotBlank()) {
                    val checkPrompt=PromptPolicy.verify(usedEvidence,reviewed.text)
                    if(engine.countTokens(checkPrompt)>3200) {
                        rejectedDraft=draft
                        return@withTimeout "Local model answer rejected (verification context exceeds token budget)."
                    }
                    val verdict=generate(checkPrompt,16,Consumer { }).trim().uppercase()
                    if(verdict!="SUPPORTED") {
                        rejectedDraft=draft
                        return@withTimeout "Local model answer rejected (local source check: $verdict)."
                    }
                }
                reviewed.text
            }
        } catch (_: TimeoutCancellationException) {
            engine.requestStop()
            "Local model answer rejected (90-second limit)."
        } catch (_: CancellationException) {
            engine.requestStop()
            "Local model answer cancelled."
        } finally { active.set(null) }
    }
    private suspend fun generate(prompt: String, limit: Int, progress: Consumer<String>): String {
        val text=StringBuilder();var last=0L
        engine.sendUserPrompt(prompt,limit).collect {
            text.append(it)
            val now=android.os.SystemClock.elapsedRealtime()
            if(last==0L || now-last>=250) { progress.accept(text.toString());last=now }
        }
        return text.toString()
    }
    fun close() { cancel(); loaded=false; engine.destroy() }
}
