package com.myfamily.meow.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class DailyReviewSchedulerTest {
    private val now = LocalDateTime.of(2026, 10, 2, 21, 30)

    @Test
    fun laterToday() = assertEquals(Duration.ofMinutes(30), DailyReviewScheduler.delayUntil(LocalTime.of(22, 0), now))

    @Test
    fun alreadyPassedGoesToTomorrow() =
        assertEquals(Duration.ofHours(23), DailyReviewScheduler.delayUntil(LocalTime.of(20, 30), now))

    @Test
    fun exactlyNowGoesToTomorrow() =
        assertEquals(Duration.ofDays(1), DailyReviewScheduler.delayUntil(LocalTime.of(21, 30), now))
}
