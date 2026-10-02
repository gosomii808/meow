package com.myfamily.meow.analysis

import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionSource
import com.myfamily.meow.data.entity.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ChatToolsTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 15)

    private fun tx(amount: Long, at: LocalDateTime, category: Category, merchant: String) = ExpenseTransaction(
        amount = amount,
        merchant = merchant,
        transactionTime = at.atZone(zone).toInstant().toEpochMilli(),
        sourceLabel = "토스",
        predictedCategory = category,
        status = TransactionStatus.INCLUDED,
        source = TransactionSource.NOTIFICATION,
    )

    // --- parsing & routing ---

    @Test fun parsesToolJsonWithArgs() {
        val call = ChatToolRouter.parse("""여기요: {"tool": "get_spending_summary", "args": {"category": "카페", "limit": 3}}""")!!
        assertEquals(ChatTool.SPENDING_SUMMARY, call.tool)
        assertEquals("카페", call.args["category"])
        assertEquals("3", call.args["limit"])
    }

    @Test fun parseReturnsNullForUnknownTool() {
        assertNull(ChatToolRouter.parse("""{"tool": "do_magic"}"""))
        assertNull(ChatToolRouter.parse("그냥 잡담"))
    }

    @Test fun keywordRouterCoversExamples() {
        assertEquals(ChatTool.RECURRING_PAYMENTS, ChatToolRouter.keywordRoute("매달 나가는 돈 뭐 있어?"))
        assertEquals(ChatTool.SIMULATE_WHATIF, ChatToolRouter.keywordRoute("배달 절반 줄이면 얼마 아껴?"))
        assertEquals(ChatTool.COMPARE_PERIODS, ChatToolRouter.keywordRoute("지난달이랑 비교하면 어때?"))
        assertEquals(ChatTool.SPENDING_SUMMARY, ChatToolRouter.keywordRoute("이번 달 카페에 얼마 썼어?"))
        assertEquals(ChatTool.NONE, ChatToolRouter.keywordRoute("안녕"))
    }

    @Test fun coerceCategoryForcesToElevenOrNull() {
        assertEquals(Category.CAFE, ChatToolRouter.coerceCategory("카페"))
        assertEquals(Category.FOOD, ChatToolRouter.coerceCategory("FOOD"))
        assertEquals(Category.FOOD, ChatToolRouter.coerceCategory("식비가 궁금해"))
        assertNull(ChatToolRouter.coerceCategory("코인투자"))
        assertNull(ChatToolRouter.coerceCategory(null))
    }

    // --- execution ---

    private val rows = listOf(
        tx(5_000, today.atTime(9, 0), Category.CAFE, "스타벅스"),
        tx(6_000, today.minusDays(1).atTime(9, 0), Category.CAFE, "스타벅스"),
        tx(20_000, today.minusDays(2).atTime(20, 0), Category.FOOD, "배달의민족"),
    )

    @Test fun summaryComputesTotalsAndNumbers() {
        val exec = ChatToolExecutor(rows, today, zone = zone)
        val r = exec.run(ToolCall(ChatTool.SPENDING_SUMMARY, mapOf("category" to "카페")))
        assertTrue(r.text, r.text.contains("11,000원"))
        assertTrue("numbers include category total", r.numbers.contains("11,000원"))
    }

    @Test fun whatIfUsesCategorySpendAndSavings() {
        val exec = ChatToolExecutor(rows, today, zone = zone)
        val r = exec.run(ToolCall(ChatTool.SIMULATE_WHATIF, mapOf("category" to "식비", "reduce_ratio" to "0.5")))
        assertTrue(r.text, r.text.contains("10,000원")) // 20,000 * 0.5 saved
        assertTrue(r.numbers.contains("10,000원"))
    }

    @Test fun noneToolReturnsEmpty() {
        val exec = ChatToolExecutor(rows, today, zone = zone)
        val r = exec.run(ToolCall(ChatTool.NONE))
        assertEquals("", r.text)
        assertTrue(r.numbers.isEmpty())
    }

    @Test fun executorNumbersSatisfyVerifier() {
        // Every number the tool surfaces must pass NumberVerifier against its own allow-list.
        val exec = ChatToolExecutor(rows, today, zone = zone)
        val r = exec.run(ToolCall(ChatTool.SPENDING_SUMMARY))
        assertTrue(NumberVerifier.verify(r.text, r.numbers).ok)
    }
}
