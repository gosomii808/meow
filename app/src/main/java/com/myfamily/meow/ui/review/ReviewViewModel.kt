package com.myfamily.meow.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionStatus
import com.myfamily.meow.data.repository.TransactionRepository
import com.myfamily.meow.ui.common.startMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReviewViewModel(private val repository: TransactionRepository) : ViewModel() {
    /** All unreviewed candidates, oldest first. Null while loading. */
    val pending: StateFlow<List<ExpenseTransaction>?> = repository.pending
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Everything reviewed or manually added today — drives the completion summary. */
    val reviewedToday: StateFlow<List<ExpenseTransaction>> = repository.reviewedSince(LocalDate.now().startMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _summaryDismissed = MutableStateFlow(false)
    val summaryDismissed = _summaryDismissed.asStateFlow()

    data class Swipe(val transaction: ExpenseTransaction, val include: Boolean)

    private val _lastSwiped = MutableStateFlow<Swipe?>(null)
    /** Most recent swipe, offered for undo. */
    val lastSwiped = _lastSwiped.asStateFlow()

    fun swipe(transaction: ExpenseTransaction, include: Boolean) {
        _summaryDismissed.value = false
        _lastSwiped.value = Swipe(transaction, include)
        viewModelScope.launch {
            repository.setStatus(
                transaction.id,
                if (include) TransactionStatus.INCLUDED else TransactionStatus.EXCLUDED,
            )
        }
    }

    fun undo() {
        val transaction = _lastSwiped.value?.transaction ?: return
        _lastSwiped.value = null
        viewModelScope.launch { repository.setStatus(transaction.id, TransactionStatus.PENDING) }
    }

    fun clearUndo() {
        _lastSwiped.value = null
    }

    fun update(transaction: ExpenseTransaction) {
        viewModelScope.launch { repository.update(transaction) }
    }

    fun addManual(transaction: ExpenseTransaction) {
        _summaryDismissed.value = false
        viewModelScope.launch { repository.addManual(transaction) }
    }

    fun finish() {
        _summaryDismissed.value = true
        _lastSwiped.value = null
    }
}
