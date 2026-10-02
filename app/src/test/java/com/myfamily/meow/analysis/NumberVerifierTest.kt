package com.myfamily.meow.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberVerifierTest {
    @Test fun extractsAllSupportedFormats() {
        val t = NumberVerifier.extract("카페에 12,000원 썼고 1만 2천원 더, 2.3배, 23%, 5번, 3건, 1.2만원")
        assertTrue("W12000" in t) // 12,000원
        assertTrue("X2.3" in t)
        assertTrue("P23" in t)
        assertTrue("N5" in t)
        assertTrue("N3" in t)
        assertTrue("W12000" in t) // 1.2만원 and 1만 2천원 both normalize to 12000
    }

    @Test fun koreanUnitAmountsNormalizeToWon() {
        assertTrue("W10000" in NumberVerifier.extract("1만원"))
        assertTrue("W3000" in NumberVerifier.extract("3천원"))
        assertTrue("W12000" in NumberVerifier.extract("1만 2천원"))
        assertTrue("W12000" in NumberVerifier.extract("1.2만원"))
    }

    @Test fun passesWhenAllNumbersAreAllowed() {
        val allowed = setOf("12,000원", "2.3배", "5번")
        val r = NumberVerifier.verify("카페에 12,000원을 5번 썼고 평소의 2.3배야", allowed)
        assertTrue(r.ok)
        assertTrue(r.unverified.isEmpty())
    }

    @Test fun flagsInventedNumber() {
        val allowed = setOf("12,000원")
        val r = NumberVerifier.verify("카페에 99,000원 썼어", allowed)
        assertFalse(r.ok)
        assertEquals(listOf("W99000"), r.unverified)
    }

    @Test fun acceptsRoundedAmount() {
        // Exact figure is 19,800원; the cat says "약 2만원".
        val allowed = setOf("19,800원")
        val r = NumberVerifier.verify("이번 달에 약 2만원 썼어", allowed)
        assertTrue(r.unverified.toString(), r.ok)
    }

    @Test fun acceptsThousandRounding() {
        val allowed = setOf("12,340원")
        val r = NumberVerifier.verify("12,000원 정도야", allowed)
        assertTrue(r.ok)
    }

    @Test fun noNumbersIsOk() {
        val r = NumberVerifier.verify("안녕! 오늘도 알뜰하게 보내자 냥", emptySet())
        assertTrue(r.ok)
    }
}
