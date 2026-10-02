package com.myfamily.meow.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The one on-device Gemma 4 E2B engine (LiteRT-LM), shared by category classification and
 * the cat chatbot so the ~2.6 GB model is loaded once per process. The model is not bundled;
 * it is pushed with adb to [modelFile]. Loading takes ~5 s on the S25+ GPU.
 */
class GemmaEngine(context: Context) : AutoCloseable {
    val modelFile: File = File(context.getExternalFilesDir(null), MODEL_FILE_NAME)
    private val cacheDir = context.cacheDir.path
    private val mutex = Mutex()
    private var engine: Engine? = null

    val isAvailable: Boolean get() = modelFile.exists()

    val isLoaded: Boolean get() = engine != null

    /** Name of the backend the engine loaded on, or null before it is loaded. */
    var backendName: String? = null
        private set

    suspend fun load() {
        use { }
    }

    /** Runs [block] with exclusive access to the engine, loading it first if needed. */
    suspend fun <T> use(block: suspend (Engine) -> T): T = withContext(Dispatchers.Default) {
        mutex.withLock { block(engineLocked()) }
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
                    // Room for the chatbot's spending summary plus a few turns of history.
                    maxNumTokens = 4096,
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
        /** In /sdcard/Android/data/com.myfamily.meow/files/ — writable by `adb push`. */
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
    }
}
