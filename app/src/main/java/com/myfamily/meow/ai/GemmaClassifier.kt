package com.myfamily.meow.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.myfamily.meow.classification.Category
import com.myfamily.meow.classification.CategoryClassifier
import com.myfamily.meow.classification.ClassificationInput
import com.myfamily.meow.classification.ClassificationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gemma 4 E2B via LiteRT-LM. The model is not bundled; it is pushed to the device with adb
 * (see [modelFile]). Loading takes several seconds, so call [initialize] ahead of time.
 */
class GemmaClassifier(context: Context) : CategoryClassifier, AutoCloseable {
    val modelFile = modelFile(context)
    private val cacheDir = context.cacheDir.path
    private val mutex = Mutex()
    private var engine: Engine? = null

    /** Name of the backend the engine loaded on, or null before [initialize]. */
    var backendName: String? = null
        private set

    suspend fun initialize() = withContext(Dispatchers.Default) {
        mutex.withLock { engineLocked() }
    }

    override suspend fun classify(input: ClassificationInput): ClassificationResult =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val config = ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.1, seed = 0),
                )
                engineLocked().createConversation(config).use { conversation ->
                    val reply = conversation.sendMessage(PromptBuilder.categoryPrompt(input))
                    val raw = reply.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                    ClassificationResult(Category.fromModelOutput(raw), raw)
                }
            }
        }

    /** Tries GPU first, then CPU. Must be called with [mutex] held. */
    private fun engineLocked(): Engine {
        engine?.let { return it }
        check(modelFile.exists()) { "모델 파일 없음: ${modelFile.path}" }

        var lastError: Exception? = null
        for (backend in listOf(Backend.GPU(), Backend.CPU())) {
            val candidate = Engine(
                EngineConfig(
                    modelPath = modelFile.path,
                    backend = backend,
                    maxNumTokens = 1024,
                    cacheDir = cacheDir,
                )
            )
            try {
                candidate.initialize()
                engine = candidate
                backendName = backend.name
                return candidate
            } catch (e: Exception) {
                candidate.close()
                lastError = e
            }
        }
        throw IllegalStateException("Gemma 엔진 초기화 실패", lastError)
    }

    override fun close() {
        engine?.close()
        engine = null
    }

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"

        /** /sdcard/Android/data/com.myfamily.meow/files/ — writable by `adb push`, no permission needed. */
        fun modelFile(context: Context): File = File(context.getExternalFilesDir(null), MODEL_FILE_NAME)
    }
}
