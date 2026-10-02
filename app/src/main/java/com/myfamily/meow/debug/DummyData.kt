package com.myfamily.meow.debug

import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.ClassificationSource
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionSource
import com.myfamily.meow.data.entity.TransactionStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/** Debug-only: realistic confirmed spending for the last ~3 months, to demo the report graphs. */
object DummyData {

    private data class Kind(
        val category: Category,
        val merchants: List<String>,
        val amount: IntRange,
        val cards: List<String>,
        /** Rough chance this kind appears on a given day. */
        val dailyChance: Double,
        val hours: IntRange = 8..21,
    )

    private val CARDS = listOf("KB국민", "신한카드", "삼성카드", "토스", "카카오페이", "NH카드")

    private val KINDS = listOf(
        Kind(Category.CAFE, listOf("스타벅스 강남점", "메가MGC커피", "투썸플레이스", "이디야커피", "컴포즈커피", "빽다방"), 2_000..6_500, CARDS, 0.7, 8..20),
        Kind(Category.FOOD, listOf("배달의민족", "김밥천국", "맘스터치", "버거킹", "한솥도시락", "서브웨이", "본죽", "엽기떡볶이"), 6_000..24_000, CARDS, 0.8, 11..21),
        Kind(Category.TRANSPORT, listOf("지하철", "시내버스", "카카오 T", "티머니"), 1_250..9_000, CARDS, 0.75, 7..22),
        Kind(Category.LIVING, listOf("GS25 역삼점", "CU 성균관대점", "세븐일레븐", "다이소", "이마트24", "올리브영"), 2_000..28_000, CARDS, 0.5, 9..22),
        Kind(Category.SHOPPING, listOf("무신사", "쿠팡", "29CM", "지그재그", "에이블리"), 15_000..78_000, CARDS, 0.18, 10..23),
        Kind(Category.CULTURE, listOf("CGV", "메가박스", "교보문고", "YES24", "방탈출카페"), 8_000..30_000, CARDS, 0.12, 12..22),
        Kind(Category.MEDICAL, listOf("온누리약국", "연세내과의원", "밝은세상안과"), 5_000..35_000, CARDS, 0.05, 9..18),
        Kind(Category.EDUCATION, listOf("교보문고", "인프런", "알라딘"), 12_000..55_000, CARDS, 0.04, 10..20),
    )

    /** Fixed monthly subscriptions. */
    private data class Sub(val merchant: String, val amount: Long, val day: Int, val card: String)

    private val SUBS = listOf(
        Sub("넷플릭스", 13_500, 5, "신한카드"),
        Sub("유튜브 프리미엄", 14_900, 12, "KB국민"),
        Sub("쿠팡 와우", 7_890, 20, "토스"),
        Sub("스포티파이", 10_900, 25, "삼성카드"),
    )

    /** ~3 months ending today. [seed] keeps it reproducible so re-runs look the same. */
    fun generate(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault(), seed: Long = 42): List<ExpenseTransaction> {
        val rnd = Random(seed)
        val start = today.minusDays(92)
        val out = mutableListOf<ExpenseTransaction>()

        var day = start
        while (!day.isAfter(today)) {
            val weekend = day.dayOfWeek.value >= 6
            for (kind in KINDS) {
                val chance = if (weekend) kind.dailyChance * 1.25 else kind.dailyChance
                if (rnd.nextDouble() > chance) continue
                val amount = (kind.amount.first + rnd.nextInt(kind.amount.last - kind.amount.first + 1)) / 100 * 100
                val hour = kind.hours.first + rnd.nextInt(kind.hours.last - kind.hours.first + 1)
                out += confirmed(
                    merchant = kind.merchants[rnd.nextInt(kind.merchants.size)],
                    amount = amount.toLong(),
                    category = kind.category,
                    card = kind.cards[rnd.nextInt(kind.cards.size)],
                    at = day.atTime(LocalTime.of(hour, rnd.nextInt(60))),
                    zone = zone,
                    source = ClassificationSource.RULE,
                )
            }
            day = day.plusDays(1)
        }

        for (monthsAgo in 0..3) {
            val month = today.minusMonths(monthsAgo.toLong())
            for (sub in SUBS) {
                val d = runCatching { month.withDayOfMonth(sub.day) }.getOrNull() ?: continue
                if (d < start || d > today) continue
                out += confirmed(sub.merchant, sub.amount, Category.SUBSCRIPTION, sub.card, d.atTime(9, 0), zone, ClassificationSource.RULE)
            }
        }
        return out.sortedBy { it.transactionTime }
    }

    private fun confirmed(
        merchant: String,
        amount: Long,
        category: Category,
        card: String,
        at: java.time.LocalDateTime,
        zone: ZoneId,
        source: ClassificationSource,
    ): ExpenseTransaction {
        val millis = at.atZone(zone).toInstant().toEpochMilli()
        return ExpenseTransaction(
            amount = amount,
            merchant = merchant,
            transactionTime = millis,
            sourceLabel = card,
            predictedCategory = category,
            classificationSource = source,
            status = TransactionStatus.INCLUDED,
            source = TransactionSource.NOTIFICATION,
            confirmedAt = millis,
        )
    }
}
