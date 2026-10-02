package com.myfamily.meow.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.repository.TransactionRepository
import com.myfamily.meow.ui.common.startMillis
import com.myfamily.meow.ui.common.toLocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class CalendarViewModel(private val repository: TransactionRepository) : ViewModel() {
    private val _month = MutableStateFlow(YearMonth.now())
    val month = _month.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate = _selectedDate.asStateFlow()

    /** Included transactions of the shown month, grouped by day. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val byDay: StateFlow<Map<LocalDate, List<ExpenseTransaction>>> = _month
        .flatMapLatest { ym ->
            repository.includedBetween(ym.atDay(1).startMillis(), ym.plusMonths(1).atDay(1).startMillis())
        }
        .map { list -> list.groupBy { it.transactionTime.toLocalDate() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun previousMonth() = changeMonth(-1)
    fun nextMonth() = changeMonth(1)

    private fun changeMonth(delta: Long) {
        _month.value = _month.value.plusMonths(delta)
        _selectedDate.value = null
    }

    fun select(date: LocalDate?) {
        _selectedDate.value = date
    }

    fun addManual(transaction: ExpenseTransaction) {
        viewModelScope.launch { repository.addManual(transaction) }
    }
}
