package com.myfamily.meow.ai

import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/** One chat turn. [fromCat] = model reply. */
data class ChatTurn(val fromCat: Boolean, val text: String)

/**
 * Spending-habit advice from the 고양이지갑 cat, on the shared on-device [GemmaEngine]
 * (spec §19). Each question opens a fresh conversation seeded with the spending digest and
 * the recent turns, so the engine is free for classification in between.
 */
class CatAdvisor(private val engine: GemmaEngine) {

    /** Streams the reply as it grows (each emission is the full text so far). */
    fun ask(digest: String, history: List<ChatTurn>, question: String): Flow<String> = channelFlow {
        engine.use { e ->
            val config = ConversationConfig(
                systemInstruction = Contents.of(systemPrompt(digest)),
                initialMessages = history.takeLast(MAX_HISTORY).map {
                    if (it.fromCat) Message.model(it.text) else Message.user(it.text)
                },
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7, seed = 0),
            )
            e.createConversation(config).use { conversation ->
                var reply = ""
                conversation.sendMessageAsync(question).collect { chunk ->
                    val piece = chunk.text()
                    // Chunks are deltas; tolerate a runtime that sends the cumulative text instead.
                    reply = if (piece.startsWith(reply) && reply.isNotEmpty()) piece else reply + piece
                    send(reply)
                }
            }
        }
    }

    companion object {
        private const val MAX_HISTORY = 6

        fun systemPrompt(digest: String) = """
            |너는 가계부 앱 '고양이지갑'의 고양이 캐릭터야. 사용자의 실제 소비 기록을 보고 소비 습관을 조언해.
            |
            |말투와 규칙:
            |- 친근한 반말로, 귀엽게 말하고 문장 끝에 가끔 '냥'을 붙여.
            |- 3~5문장으로 짧게 답해. 목록이 필요하면 최대 3개까지만.
            |- 숫자는 아래 [소비 데이터]에 있는 것만 써. 없는 숫자는 절대 지어내지 마.
            |- "만약 ~를 줄였다면?" 같은 질문은 [소비 데이터]에서 바로 그 카테고리의 금액으로 계산해서 답해.
            |- 물어본 카테고리가 '기록 없는 카테고리'에 있으면 이번 달엔 그 지출이 없다고 말해. 다른 카테고리 숫자로 대신 계산하지 마.
            |- '기타'는 아직 분류가 안 된 지출이야. 기타가 크면 카드를 눌러 카테고리를 정해달라고 해도 좋아.
            |- 데이터가 부족하면 솔직하게 모른다고 말하고, 스와이프로 소비를 더 기록해 달라고 해.
            |- 혼내지 말고, 바로 해볼 수 있는 구체적인 행동이나 챌린지를 한 가지 제안해. (예: 이번 주 카페 3번 이하로 가기)
            |- 주식, 코인, 대출, 금융상품 추천은 하지 마.
            |
            |[소비 데이터]
            |$digest
        """.trimMargin()
    }
}
