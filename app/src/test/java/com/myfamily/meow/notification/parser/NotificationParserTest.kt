package com.myfamily.meow.notification.parser

import com.myfamily.meow.debug.FakePaymentNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {
    private fun parse(fake: FakePaymentNotifier.Fake) = NotificationParser.parse(fake.title, fake.text)

    @Test
    fun cardSmsUsesMerchantAfterDateTimeAndIgnoresCumulative() {
        assertEquals(ParsedPayment(5_500, "스타벅스 강남점"), parse(FakePaymentNotifier.SINGLES[0]))
        assertEquals(ParsedPayment(12_000, "배달의민족"), parse(FakePaymentNotifier.SINGLES[1]))
    }

    @Test
    fun tossSentence() = assertEquals(ParsedPayment(3_200, "CU 성균관대점"), parse(FakePaymentNotifier.SINGLES[2]))

    @Test
    fun chargeScenario() {
        val parsed = FakePaymentNotifier.CHARGE_SCENARIO.map(::parse)
        assertEquals(
            listOf(
                ParsedPayment(10_000, "카카오페이"),
                ParsedPayment(10_000, "카카오페이"),
                ParsedPayment(10_000, "GS25 역삼점"),
            ),
            parsed,
        )
    }

    @Test
    fun noAmountFails() = assertNull(NotificationParser.parse("토스", "결제가 취소되었어요"))

    @Test
    fun paymentKeywordFilter() {
        assertTrue(NotificationParser.looksLikePayment("신한카드", "승인 5,000원"))
        assertFalse(NotificationParser.looksLikePayment("토스", "오늘의 행운퀴즈 정답을 맞혀보세요"))
    }
}
