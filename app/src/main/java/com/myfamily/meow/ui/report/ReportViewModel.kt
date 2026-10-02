package com.myfamily.meow.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.TransactionStatus
import com.myfamily.meow.data.repository.TransactionRepository
import com.myfamily.meow.ui.common.isIncome
import com.myfamily.meow.ui.common.startMillis
import com.myfamily.meow.ui.common.toLocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class MonthSpending(
    val month: YearMonth = YearMonth.now(),
    val total: Long = 0,
    val byCategory: Map<Category, Long> = emptyMap(),
    /** Spending per day of month, index 0 = day 1. */
    val daily: List<Long> = emptyList(),
)

/** This month's included spending (income excluded) for the report and goal screens. */
class ReportViewModel(repository: TransactionRepository) : ViewModel() {
    private val month = YearMonth.now()

    val spending: StateFlow<MonthSpending> = repository
        .reviewedBetween(month.atDay(1).startMillis(), month.plusMonths(1).atDay(1).startMillis())
        .map { rows ->
            val spent = rows.filter { it.status == TransactionStatus.INCLUDED && !it.isIncome }
            val daily = LongArray(month.lengthOfMonth())
            spent.forEach { daily[it.transactionTime.toLocalDate().dayOfMonth - 1] += it.amount }
            MonthSpending(
                month = month,
                total = spent.sumOf { it.amount },
                byCategory = spent.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amount } },
                daily = daily.toList(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthSpending(month))
}
