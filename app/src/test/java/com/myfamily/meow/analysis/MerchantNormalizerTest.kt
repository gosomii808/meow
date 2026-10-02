package com.myfamily.meow.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantNormalizerTest {
    private fun n(s: String) = MerchantNormalizer.normalize(s)

    @Test fun specExamples() {
        assertEquals("스타벅스", n("스타벅스 강남R점"))
        assertEquals("스타벅스코리아", n("(주)스타벅스코리아"))
        assertEquals("gs25", n("GS25 역삼역점"))
        assertEquals("cu편의점", n("CU편의점"))
        assertEquals("이마트", n("이마트 본점"))
    }

    @Test fun keepsStoreTypeWords() {
        // A trailing store-type word ending in 점 must be kept.
        assertEquals("롯데 백화점", n("롯데 백화점"))
        assertEquals("이마트 편의점", n("이마트 편의점"))
    }

    @Test fun stripsNumberedAndMultipleBranches() {
        assertEquals("배스킨라빈스", n("배스킨라빈스 2호점"))
        assertEquals("스타벅스 강남", n("스타벅스 강남 3호점")) // only the trailing 호점 token goes
    }

    @Test fun lowercasesAndCollapsesSpaces() {
        assertEquals("mega mgc커피", n("  MEGA   MGC커피 "))
        assertEquals("29cm", n("29CM!!"))
    }

    @Test fun fallsBackToTrimmedRawWhenEmpty() {
        assertEquals("(주)", n("(주)")) // everything stripped → original trimmed
        assertEquals("...", n("..."))
    }
}
