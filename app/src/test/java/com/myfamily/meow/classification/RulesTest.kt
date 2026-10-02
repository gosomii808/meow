package com.myfamily.meow.classification

import com.myfamily.meow.debug.FakePaymentNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    @Test
    fun merchantKeyDropsBranch() {
        assertEquals("스타벅스", merchantKey("스타벅스 강남점"))
        assertEquals("gs25", merchantKey(" GS25 역삼점"))
        assertEquals("배달의민족", merchantKey("배달의민족"))
    }

    @Test
    fun ruleExamplesFromSpec() {
        assertEquals(Category.FOOD, RuleClassifier.classify("배달의민족"))
        assertEquals(Category.SHOPPING, RuleClassifier.classify("무신사"))
        assertEquals(Category.CULTURE, RuleClassifier.classify("CGV 용산아이파크몰"))
        assertEquals(Category.CAFE, RuleClassifier.classify("스타벅스 강남점"))
    }

    @Test
    fun shortBrandsNeedWholeToken() {
        assertEquals(Category.LIVING, RuleClassifier.classify("CU 성균관대점"))
        assertNull(RuleClassifier.classify("Cucina 이태원"))
    }

    @Test
    fun unknownMerchantIsLeftForAi() = assertNull(RuleClassifier.classify("성균문구사랑"))

    @Test
    fun transferDetection() {
        val (withdraw, charge, purchase) = FakePaymentNotifier.CHARGE_SCENARIO
        assertTrue(TransferDetector.isTransferLike(withdraw.title, withdraw.text))
        assertTrue(TransferDetector.isTransferLike(charge.title, charge.text))
        assertFalse(TransferDetector.isTransferLike(purchase.title, purchase.text))
        assertTrue(TransferDetector.isTransferLike("토스", "홍길동님에게 15,000원을 보냈어요"))
        val tossTransfer = FakePaymentNotifier.SINGLES[4]
        assertTrue(TransferDetector.isTransferLike(tossTransfer.title, tossTransfer.text))

    }
}
