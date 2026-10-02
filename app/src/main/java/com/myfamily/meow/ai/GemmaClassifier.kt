package com.myfamily.meow.ai

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.myfamily.meow.classification.Category
import com.myfamily.meow.classification.CategoryClassifier
import com.myfamily.meow.classification.ClassificationInput
import com.myfamily.meow.classification.ClassificationResult

/** Category classification on the shared on-device [GemmaEngine]. */
class GemmaClassifier(private val engine: GemmaEngine) : CategoryClassifier {
    override suspend fun classify(input: ClassificationInput): ClassificationResult = engine.use { e ->
        val config = ConversationConfig(
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.1, seed = 0),
        )
        e.createConversation(config).use { conversation ->
            val raw = conversation.sendMessage(PromptBuilder.categoryPrompt(input)).text()
            ClassificationResult(Category.fromModelOutput(raw), raw)
        }
    }
}

/** Plain text of a LiteRT-LM message. */
fun Message.text(): String = contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
