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
    fun tossChargeUsesRecipientAfterArrow() =
        assertEquals(ParsedPayment(10_000, "카카오페이"), parse(FakePaymentNotifier.SINGLES[3]))

    @Test
    fun tossPersonTransfer() = assertEquals(ParsedPayment(15_000, "홍길동"), parse(FakePaymentNotifier.SINGLES[4]))

    @Test
    fun tossDepositIsRecognizedAsIncome() =
        assertTrue(NotificationParser.parse("30,000원 입금", "홍길동 → 내 토스뱅크 통장")!!.isIncome)

    @Test
    fun cardCancellationIsIncome() = assertEquals(
        ParsedPayment(5_500, "스타벅스 강남점", isIncome = true),
        NotificationParser.parse("KB국민카드", "KB국민카드(1234)승인취소 홍*동 5,500원 10/02 09:10 스타벅스 강남점"),
    )

    @Test
    fun outgoingIsNotIncome() {
        val parsed = FakePaymentNotifier.SINGLES.map(::parse) + FakePaymentNotifier.CHARGE_SCENARIO.map(::parse)
        assertTrue(parsed.none { it!!.isIncome })
    }

    @Test
    fun sentenceStyleTransfer() =
        assertEquals(ParsedPayment(15_000, "홍길동"), NotificationParser.parse("토스", "홍길동님에게 15,000원을 보냈어요"))

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

    @Test
    fun kakaoSettlementParsesAmountAndCounterparty() {
        // "상대가 N원을 받았어요" = money the user paid out; the counterpart is the merchant.
        val p = NotificationParser.parse("홍길동", "홍길동님이 8,000원을 받았어요")!!
        assertEquals(8_000, p.amount)
        assertEquals("홍길동", p.merchant)
    }

    @Test
    fun kakaoSettlementDetection() {
        // Only KakaoTalk + the "받았어요" wording counts; other apps/wording don't.
        assertTrue(com.myfamily.meow.notification.PaymentSources.isKakaoSettlement("com.kakao.talk", "홍길동", "12,000원을 받았어요"))
        assertFalse(com.myfamily.meow.notification.PaymentSources.isKakaoSettlement("com.kakao.talk", "친구", "내일 보자"))
        assertFalse(com.myfamily.meow.notification.PaymentSources.isKakaoSettlement("com.whatever.app", "홍길동", "12,000원을 받았어요"))
    }
}
