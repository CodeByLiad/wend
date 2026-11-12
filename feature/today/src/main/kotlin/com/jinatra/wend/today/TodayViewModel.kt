package com.jinatra.wend.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinatra.wend.engine.Proposal
import com.jinatra.wend.engine.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Supplies "now" as minutes since midnight of the plan date. The engine never reads a clock itself. */
fun interface MinuteClock {
    fun now(): Int
}

sealed interface Sheet {
    data object RunningLate : Sheet

    data class BlockActions(val blockId: String) : Sheet

    data class Review(val proposal: Proposal, val settings: Settings) : Sheet
}

/** A one-off message; [id] keeps two equal outcomes in a row distinct. */
data class Message(val id: Long, val outcome: Outcome)

data class TodayUiState(
    val date: LocalDate,
    val now: Int,
    val model: TodayModel,
    val canUndo: Boolean,
    val isSample: Boolean,
    val sheet: Sheet? = null,
    val message: Message? = null,
)

class TodayViewModel(
    private val controller: TodayController,
    private val clock: MinuteClock,
    private val isSample: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                refresh()
            }
        }
    }

    fun openRunningLate() = _state.update { it.copy(sheet = Sheet.RunningLate) }

    fun openActions(blockId: String) = _state.update { it.copy(sheet = Sheet.BlockActions(blockId)) }

    fun dismissSheet() = _state.update { it.copy(sheet = null) }

    fun messageShown(id: Long) = _state.update { if (it.message?.id == id) it.copy(message = null) else it }

    fun runningLate(minutes: Int) = handle(controller.runningLate(minutes, clock.now()))

    fun rebuild() = handle(controller.rebuild(clock.now()))

    fun rebuildPastBedtime() = handle(controller.rebuild(clock.now(), Settings(allowPastBedtime = true)))

    fun markDone(id: String) = handle(controller.markDone(id))

    fun start(id: String) = handle(controller.start(id))

    fun skip(id: String) = handle(controller.skip(id, clock.now()))

    fun moveLater(id: String, minutes: Int) = handle(controller.moveLater(id, minutes, clock.now()))

    fun ranOver(id: String, minutes: Int) = handle(controller.ranOver(id, minutes, clock.now()))

    fun retry(id: String) = handle(controller.retry(id, clock.now()))

    fun makeFixed(id: String) = handle(controller.makeFixed(id, clock.now()))

    fun approve(proposal: Proposal) {
        controller.apply(proposal)
        refresh(sheet = null, message = message(Outcome.Applied(AppliedKind.PLAN_UPDATED, null)))
    }

    fun undo() = handle(controller.undo(clock.now()))

    private fun handle(outcome: Outcome) {
        if (outcome is Outcome.NeedsApproval) {
            refresh(sheet = Sheet.Review(outcome.proposal, outcome.settings), message = null)
        } else {
            refresh(sheet = null, message = message(outcome))
        }
    }

    private var nextMessageId = 0L

    private fun message(outcome: Outcome) = Message(nextMessageId++, outcome)

    private fun refresh(sheet: Sheet? = _state.value.sheet, message: Message? = _state.value.message) {
        _state.value = snapshot().copy(sheet = sheet, message = message)
    }

    private fun snapshot(): TodayUiState {
        val now = clock.now()
        return TodayUiState(
            date = controller.day.date,
            now = now,
            model = buildTodayModel(controller.day, now),
            canUndo = controller.canUndo,
            isSample = isSample,
        )
    }

    private companion object {
        const val TICK_MILLIS = 15_000L
    }
}
