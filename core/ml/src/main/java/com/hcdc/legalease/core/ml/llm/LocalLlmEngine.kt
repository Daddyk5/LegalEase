package com.hcdc.legalease.core.ml.llm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.hcdc.legalease.core.domain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Duration

/** One generation request. Every call has a fixed token budget and timeout. */
data class Generation(
    val system: String,
    val user: String,
    val jsonSchema: String,
    val temperature: Float,
    val maxOutputTokens: Int,
    val timeout: Duration,
)

/**
 * App-scoped LiteRT-LM engine. Loads lazily on a background dispatcher: decrypts the model to a
 * temporary file, initialises the engine, and deletes the file immediately. [release] frees the
 * memory (about 1 GB for the primary tier) when an analysis ends.
 */
@Singleton
class LocalLlmEngine @Inject constructor(
    private val store: ModelStore,
    private val provisioner: ModelProvisioner,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var engine: Engine? = null

    /**
     * Runs one generation in a fresh conversation, so steps never see each other's text. On timeout
     * or cancellation the native generation is cancelled too.
     */
    suspend fun generate(request: Generation): String = coroutineScope {
        val engine = acquire()
        val conversation = engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(request.system),
                samplerConfig = SamplerConfig(
                    topK = TOP_K,
                    topP = TOP_P,
                    temperature = request.temperature.toDouble(),
                    seed = Random.nextInt(),
                ),
                maxOutputToken = request.maxOutputTokens,
                enableResponseFormat = true,
            )
        )
        try {
            val reply = async(ioDispatcher) {
                conversation.sendMessage(request.user, responseFormat = ResponseFormat.json(request.jsonSchema))
            }
            try {
                withTimeout(request.timeout) { reply.await() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                conversation.cancelProcess()
                throw e
            }.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
        } finally {
            conversation.close()
        }
    }

    private suspend fun acquire(): Engine = mutex.withLock {
        engine ?: withContext(ioDispatcher) { load() }.also { engine = it }
    }

    private fun load(): Engine {
        val spec = checkNotNull(provisioner.specForDevice()) { "No model tier for this device" }
        check(store.isReady(spec)) { "Model is not provisioned" }
        val plain = store.decryptToTemp(spec)
        return try {
            Engine(
                EngineConfig(
                    modelPath = plain.absolutePath,
                    backend = Backend.CPU(),
                    maxNumTokens = spec.maxTokens,
                    cacheDir = store.runtimeCacheDir().absolutePath,
                )
            ).apply { initialize() }
        } finally {
            // The engine holds the weights open; the decrypted file itself is removed at once.
            plain.delete()
        }
    }

    /** Context window of the loaded tier, for prompt budgeting. */
    fun maxTokens(): Int = provisioner.specForDevice()?.maxTokens ?: 0

    suspend fun release() = mutex.withLock {
        engine?.close()
        engine = null
        store.clearRuntimeFiles()
    }

    private companion object {
        const val TOP_K = 40
        const val TOP_P = 0.95
    }
}
