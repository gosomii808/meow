package com.myfamily.meow.classification

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTest {
    @Test
    fun exactLabel() = assertEquals(Category.EDUCATION, Category.fromModelOutput("교육"))

    @Test
    fun labelWithWhitespaceAndPunctuation() = assertEquals(Category.CAFE, Category.fromModelOutput("  \"카페\".\n"))

    @Test
    fun labelInsideSentence() = assertEquals(Category.CULTURE, Category.fromModelOutput("문화/여가입니다"))

    @Test
    fun earliestMentionWins() = assertEquals(Category.FOOD, Category.fromModelOutput("식비 (카페 아님)"))

    @Test
    fun unknownFallsBackToEtc() = assertEquals(Category.ETC, Category.fromModelOutput("문구류"))
}
