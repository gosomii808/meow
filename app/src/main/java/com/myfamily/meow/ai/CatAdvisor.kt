package com.myfamily.meow.ai

import android.util.Log
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.myfamily.meow.analysis.ChatTool
import com.myfamily.meow.analysis.ChatToolExecutor
import com.myfamily.meow.analysis.ChatToolRouter
import com.myfamily.meow.analysis.NumberVerifier
import com.myfamily.meow.analysis.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.time.LocalDate

/** One chat turn. [fromCat] = model reply. */
data class ChatTurn(val fromCat: Boolean, val text: String)

/**
 * Spending-habit advice from the 고양이지갑 cat, on the shared on-device [GemmaEngine]
 * (spec §19). Each question opens a fresh conversation seeded with the spending digest and
 * the recent turns, so the engine is free for classification in between.
 */
class CatAdvisor(private val engine: GemmaEngine) {

    /**
     * Function-calling answer (spec H): the model picks a tool as JSON, the app computes the
     * numbers ([ChatToolExecutor]), then the model phrases the answer in the cat's voice using
     * only those numbers. [NumberVerifier] guards the final text: on failure it regenerates once
     * with a stricter instruction, then falls back to a template built from the tool result.
     *
     * We use the JSON tool protocol rather than LiteRT-LM's native tool API: it exists in this
     * version but the JSON approach is more reliable for a small on-device model (spec H).
     */
    fun answer(
        executor: ChatToolExecutor,
        today: LocalDate,
        history: List<ChatTurn>,
        question: String,
    ): Flow<String> = channelFlow {
        engine.use { e ->
            // Step 1: choose a tool (deterministic), fall back to the keyword router.
            val selection = runCatching { prompt(e, toolSelectionPrompt(today, question)) }.getOrDefault("")
            val call = ChatToolRouter.parse(selection)
                ?: ToolCall(ChatToolRouter.keywordRoute(question))
            val result = runCatching { executor.run(call) }.getOrNull()

            // Step 2: phrase the answer. General chat (NONE / no numbers) skips verification.
            val system = if (result == null || result.text.isBlank()) chatOnlyPrompt() else answerPrompt(result.text)
            var reply = ""
            streamReply(e, system, history, question) { reply = it; trySend(it) }

            val allowed = result?.numbers ?: emptySet()
            val needsCheck = call.tool != ChatTool.NONE && allowed.isNotEmpty()
            if (needsCheck && !NumberVerifier.verify(reply, allowed).ok) {
                Log.w(TAG, "number check failed: '$reply' allowed=$allowed")
                val stricter = answerPrompt(result!!.text) + "\n\n중요: 위 자료에 있는 숫자만 그대로 써. 다른 숫자는 절대 만들지 마."
                val retry = runCatching { prompt(e, stricter, question) }.getOrDefault("")
                reply = if (retry.isNotBlank() && NumberVerifier.verify(retry, allowed).ok) {
                    retry
                } else {
                    Log.w(TAG, "number check failed again, using template")
                    templateAnswer(result.text)
                }
                trySend(reply)
            }
        }
    }

    /** Single-shot deterministic generation (tool selection and retries). */
    private suspend fun prompt(e: com.google.ai.edge.litertlm.Engine, system: String, user: String = ""): String {
        val config = ConversationConfig(
            systemInstruction = Contents.of(system),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.1, seed = 0),
        )
        return e.createConversation(config).use { c ->
            c.sendMessage(if (user.isEmpty()) "위 지시를 따라 출력해." else user).text()
        }
    }

    private suspend fun streamReply(
        e: com.google.ai.edge.litertlm.Engine,
        system: String,
        history: List<ChatTurn>,
        question: String,
        onText: (String) -> Unit,
    ) {
        val config = ConversationConfig(
            systemInstruction = Contents.of(system),
            initialMessages = history.takeLast(MAX_HISTORY).map {
                if (it.fromCat) Message.model(it.text) else Message.user(it.text)
            },
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7, seed = 0),
        )
        e.createConversation(config).use { conversation ->
            var reply = ""
            conversation.sendMessageAsync(question).collect { chunk ->
                val piece = chunk.text()
                reply = if (piece.startsWith(reply) && reply.isNotEmpty()) piece else reply + piece
                onText(reply.trim())
            }
        }
    }

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
        private const val TAG = "CatAdvisor"

        /** Lists the 8 tools and asks for a single JSON choice. Today's date anchors "지난주" etc. */
        fun toolSelectionPrompt(today: LocalDate, question: String) = """
            |너는 가계부 앱의 도구 선택기야. 사용자 질문에 가장 알맞은 도구 하나를 고른다.
            |오늘 날짜는 $today 다. "이번 달"은 이번 달 1일~오늘, "지난달"은 지난달 1일~말일, "지난주"는 오늘로부터 7일 전~오늘로 해석해.
            |
            |도구 목록:
            |- get_spending_summary(start_date, end_date, category?): 기간 총액·건수·카테고리별 합계
            |- compare_periods(a_start, a_end, b_start, b_end, category?): 두 기간 비교
            |- get_top_merchants(start_date, end_date, category?, limit): 많이 쓴 가게
            |- get_time_pattern(start_date, end_date, category?): 요일·시간대 패턴
            |- simulate_whatif(category, reduce_ratio): 그 카테고리를 그 비율만큼 줄였을 때 절약액
            |- get_recurring_payments(): 매달 고정 지출
            |- get_insights(): 소비 인사이트 요약
            |- none: 도구가 필요 없는 일반 대화
            |
            |규칙:
            |- 날짜는 반드시 YYYY-MM-DD 형식. category는 다음 중 하나만: 식비, 카페, 교통비, 쇼핑, 생활, 문화/여가, 교육, 의료, 여행, 구독, 기타.
            |- 오직 JSON 한 줄만 출력해. 설명 금지. 예: {"tool": "get_spending_summary", "args": {"category": "카페"}}
            |- 도구가 필요 없으면 {"tool": "none"} 만 출력해.
            |
            |질문: $question
        """.trimMargin()

        /** Cat-voice answer, constrained to the numbers the tool produced. */
        fun answerPrompt(toolResult: String) = """
            |너는 가계부 앱 '고양이지갑'의 고양이야. 아래 [계산 결과]만 근거로 사용자에게 답해.
            |
            |말투와 규칙:
            |- 친근한 반말, 귀엽게, 문장 끝에 가끔 '냥'. 3문장 이내로 짧게.
            |- 숫자는 [계산 결과]에 있는 것만 그대로 써. 없는 숫자는 절대 지어내지 마.
            |- 혼내지 말고, 바로 해볼 수 있는 행동 하나를 제안해. 금융상품 추천은 하지 마.
            |
            |[계산 결과]
            |$toolResult
        """.trimMargin()

        fun chatOnlyPrompt() = """
            |너는 가계부 앱 '고양이지갑'의 고양이야. 친근한 반말로, 귀엽게, 문장 끝에 가끔 '냥'을 붙여 3문장 이내로 답해.
            |구체적인 소비 숫자는 모르면 지어내지 말고, 리포트나 스와이프 기록을 권해. 금융상품 추천은 하지 마.
        """.trimMargin()

        /** Safe fallback that reuses the already-correct tool numbers verbatim. */
        fun templateAnswer(toolResult: String) =
            "내가 계산해봤어냥! $toolResult\n자세한 건 리포트에서 확인해줘 🐾"

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
