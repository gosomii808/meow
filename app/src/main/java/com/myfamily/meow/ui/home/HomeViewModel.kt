package com.myfamily.meow.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfamily.meow.analysis.SpendingTendency
import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.TransactionSource
import com.myfamily.meow.data.repository.TransactionRepository
import com.myfamily.meow.ui.common.isIncome
import com.myfamily.meow.ui.common.startMillis
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Numbers for the home "오늘의 소비 요약" card. */
data class HomeSummary(
    /** Notification candidates that arrived today, any status. */
    val todayIncoming: Int = 0,
    /** Pending cards still waiting for the user. */
    val pending: Int = 0,
    /** Pending cards already categorized by history/rules/AI. */
    val autoSorted: Int = 0,
    /** Pending cards flagged as possible duplicate or account move. */
    val suspicious: Int = 0,
)

class HomeViewModel(repository: TransactionRepository) : ViewModel() {
    private val today = LocalDate.now()

    val summary: StateFlow<HomeSummary> = combine(
        repository.pending,
        repository.allBetween(today.startMillis(), today.plusDays(1).startMillis()),
    ) { pending, todays ->
        HomeSummary(
            todayIncoming = todays.count { it.source == TransactionSource.NOTIFICATION },
            pending = pending.size,
            autoSorted = pending.count { it.isIncome || it.category != Category.ETC },
            suspicious = pending.count { it.duplicateGroupId != null || it.transferLikely },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSummary())

    /** The cat's spending personality over the last 30 days (spec J). */
    val tendency: StateFlow<SpendingTendency.Result> = repository
        .reviewedBetween(today.minusDays(30).startMillis(), today.plusDays(1).startMillis())
        .map { SpendingTendency.analyze(it, today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpendingTendency.Result(SpendingTendency.Tendency.BALANCED, false))
}
