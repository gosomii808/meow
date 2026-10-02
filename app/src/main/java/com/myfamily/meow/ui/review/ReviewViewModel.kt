package com.myfamily.meow.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfamily.meow.ai.AiCategorizer
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.TransactionStatus
import com.myfamily.meow.data.repository.TransactionRepository
import com.myfamily.meow.ui.common.startMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class SwipeAction { INCLUDE, EXCLUDE, SETTLE }

class ReviewViewModel(
    private val repository: TransactionRepository,
    private val aiCategorizer: AiCategorizer,
) : ViewModel() {
    /** All unreviewed candidates, oldest first. Null while loading. */
    val pending: StateFlow<List<ExpenseTransaction>?> = repository.pending
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Everything reviewed or manually added today — drives the completion summary. */
    val reviewedToday: StateFlow<List<ExpenseTransaction>> = repository.reviewedSince(LocalDate.now().startMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _summaryDismissed = MutableStateFlow(false)
    val summaryDismissed = _summaryDismissed.asStateFlow()

    /** [snapshot] is the row before the decision, restored as-is on undo. */
    data class Swipe(val snapshot: ExpenseTransaction, val action: SwipeAction)

    private val _lastSwiped = MutableStateFlow<Swipe?>(null)
    /** Most recent decision, offered for undo. */
    val lastSwiped = _lastSwiped.asStateFlow()

    /** Pending ids the on-device model is still working on. */
    val aiRunningIds = aiCategorizer.runningIds

    init {
        // New candidates can arrive while the screen is open; classify leftovers each time.
        viewModelScope.launch {
            repository.pending
                .map { list -> list.map { it.id }.toSet() }
                .distinctUntilChanged()
                .collect { aiCategorizer.classifyPending() }
        }
    }

    fun decide(transaction: ExpenseTransaction, include: Boolean) {
        record(transaction, if (include) SwipeAction.INCLUDE else SwipeAction.EXCLUDE)
        viewModelScope.launch {
            repository.setStatus(transaction.id, if (include) TransactionStatus.INCLUDED else TransactionStatus.EXCLUDED)
        }
    }

    /** Up swipe: keep only my share of a bill split among [people]. */
    fun settle(transaction: ExpenseTransaction, people: Int) {
        record(transaction, SwipeAction.SETTLE)
        viewModelScope.launch { repository.settle(transaction, people) }
    }

    private fun record(transaction: ExpenseTransaction, action: SwipeAction) {
        _summaryDismissed.value = false
        _lastSwiped.value = Swipe(transaction, action)
    }

    fun undo() {
        val swipe = _lastSwiped.value ?: return
        _lastSwiped.value = null
        viewModelScope.launch { repository.restore(swipe.snapshot) }
    }

    fun clearUndo() {
        _lastSwiped.value = null
    }

    fun saveEdit(original: ExpenseTransaction, edited: ExpenseTransaction) {
        viewModelScope.launch { repository.saveEdit(original, edited) }
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
