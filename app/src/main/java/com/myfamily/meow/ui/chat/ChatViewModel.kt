package com.myfamily.meow.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfamily.meow.ai.CatAdvisor
import com.myfamily.meow.ai.ChatTurn
import com.myfamily.meow.ai.GemmaEngine
import com.myfamily.meow.ai.SpendingDigest
import com.myfamily.meow.analysis.ChatToolExecutor
import com.myfamily.meow.classification.Category
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.repository.TransactionRepository
import com.myfamily.meow.ui.common.startMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class CatStatus { NO_MODEL, WAKING, READY, THINKING, ERROR }

data class ChatState(
    val turns: List<ChatTurn> = emptyList(),
    val status: CatStatus = CatStatus.WAKING,
    val error: String? = null,
)

class ChatViewModel(
    private val repository: TransactionRepository,
    private val engine: GemmaEngine,
    private val goals: () -> Pair<Long, Map<Category, Long>>,
) : ViewModel() {
    private val advisor = CatAdvisor(engine)
    private val _state = MutableStateFlow(ChatState())
    val state = _state.asStateFlow()

    private var digest = ""

    init {
        viewModelScope.launch {
            digest = buildDigest()
            val total = Regex("""총 ([\d,]+원)""").find(digest)?.groupValues?.get(1)
            _state.update {
                it.copy(turns = listOf(ChatTurn(true, greeting(total))))
            }
            if (!engine.isAvailable) {
                _state.update { it.copy(status = CatStatus.NO_MODEL) }
                return@launch
            }
            // Load the model while the user reads the greeting (~5 s the first time).
            runCatching { engine.load() }
                .onSuccess { _state.update { it.copy(status = CatStatus.READY) } }
                .onFailure { e -> _state.update { it.copy(status = CatStatus.ERROR, error = e.message) } }
        }
    }

    private suspend fun buildDigest(): String {
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val rows = repository
            .reviewedBetween(month.minusMonths(1).atDay(1).startMillis(), month.plusMonths(1).atDay(1).startMillis())
            .first()
        val (monthly, perCategory) = goals()
        return SpendingDigest.build(rows, today, monthly, perCategory)
    }

    /** Wider window so compare/recurring/forecast tools have history (spec H). */
    private suspend fun loadToolRows(today: LocalDate): List<ExpenseTransaction> {
        val from = YearMonth.from(today).minusMonths(TOOL_MONTHS_BACK).atDay(1).startMillis()
        val to = YearMonth.from(today).plusMonths(1).atDay(1).startMillis()
        return repository.reviewedBetween(from, to).first()
    }

    private fun greeting(total: String?) =
        if (total != null && total != "0원") {
            "안녕 냥! 이번 달엔 지금까지 ${total} 썼어. 소비 습관이 궁금하면 뭐든 물어봐냥 🐾"
        } else {
            "안녕 냥! 아직 이번 달 기록이 거의 없어. 스와이프로 소비를 기록하면 더 똑똑하게 조언해줄게냥 🐾"
        }

    fun send(question: String) {
        val q = question.trim()
        val current = _state.value
        if (q.isEmpty() || current.status != CatStatus.READY) return
        val history = current.turns.drop(1) // the greeting is local, not model output
        _state.update { it.copy(turns = it.turns + ChatTurn(false, q) + ChatTurn(true, ""), status = CatStatus.THINKING) }

        viewModelScope.launch {
            val today = LocalDate.now()
            val (monthly, _) = goals()
            val executor = ChatToolExecutor(loadToolRows(today), today, monthly)
            runCatching {
                advisor.answer(executor, today, history, q).collect { partial -> replaceLast(partial) }
            }.onFailure { e ->
                replaceLast("앗, 생각하다가 졸았다냥… 다시 물어봐 줄래? (${e.message ?: "오류"})")
            }
            _state.update { s ->
                if (s.turns.last().text.isBlank()) s.copy(turns = s.turns.dropLast(1) + ChatTurn(true, "음… 할 말이 떠오르지 않는다냥. 다르게 물어봐 줄래?"))
                else s
            }
            _state.update { it.copy(status = CatStatus.READY) }
        }
    }

    private fun replaceLast(text: String) {
        _state.update { s -> s.copy(turns = s.turns.dropLast(1) + ChatTurn(true, text.trim())) }
    }

    private companion object {
        const val TOOL_MONTHS_BACK = 6L
    }
}
