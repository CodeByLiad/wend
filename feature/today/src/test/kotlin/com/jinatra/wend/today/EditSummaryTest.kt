package com.jinatra.wend.today

import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.Command
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.Kind
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Priority
import com.jinatra.wend.engine.Reason
import com.jinatra.wend.engine.SchedulingEngine
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class EditSummaryTest :
    FunSpec({
        test("title-only edits are described as edits rather than retries") {
            val block = Block("a", "Before", Kind.FLEXIBLE, Minute(540), Minute(570), 30, 15, Priority.NORMAL)
            val day = Day(LocalDate.of(2026, 11, 18), Minute(420), Minute(1380), listOf(block))
            val replacement = block.copy(title = "After")
            val proposal = SchedulingEngine.propose(day, Command.EditBlock("a", replacement), Minute(420))
            proposalLines(proposal) shouldBe listOf(ChangeLine("After", Effect.EDITED, block, replacement, Reason.USER_EDIT, null))
        }
    })
