package com.jinatra.wend.today

import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.ChangeType
import com.jinatra.wend.engine.Proposal
import com.jinatra.wend.engine.Reason

/** What happens to one block, in the order the summary sentence names it. */
enum class Effect { ADDED, RESTORED, DROPPED, UNPLACED, MISSED, SKIPPED, EXTENDED, MOVED_AND_SHRUNK, MOVED, SHRUNK, MADE_FIXED, RETRIED }

/** One line of a rebuild summary, built from structured engine data with no AI. */
data class ChangeLine(
    val title: String,
    val effect: Effect,
    val before: Block?,
    val after: Block,
    val reason: Reason,
    val causeTitle: String?,
)

fun proposalLines(proposal: Proposal): List<ChangeLine> {
    val before = proposal.previousDay.blocks.associateBy { it.id }
    val after = proposal.day.blocks.associateBy { it.id }
    return proposal.changes.mapNotNull { change ->
        val now = after[change.blockId] ?: return@mapNotNull null
        val types = change.types
        val effect = when {
            ChangeType.ADDED in types -> Effect.ADDED
            ChangeType.RESTORED in types -> Effect.RESTORED
            ChangeType.DROPPED in types -> Effect.DROPPED
            ChangeType.UNPLACED in types -> Effect.UNPLACED
            ChangeType.MISSED in types -> Effect.MISSED
            ChangeType.SKIPPED in types -> Effect.SKIPPED
            ChangeType.EXTENDED in types -> Effect.EXTENDED
            ChangeType.MOVED in types && ChangeType.SHRUNK in types -> Effect.MOVED_AND_SHRUNK
            ChangeType.MOVED in types -> Effect.MOVED
            ChangeType.SHRUNK in types -> Effect.SHRUNK
            ChangeType.MADE_FIXED in types -> Effect.MADE_FIXED
            else -> Effect.RETRIED
        }
        ChangeLine(
            title = now.title,
            effect = effect,
            before = before[change.blockId],
            after = now,
            reason = change.reason,
            causeTitle = (change.causeBlockId ?: now.after.takeIf { change.reason == Reason.FOLLOWS_DEPENDENCY })?.let { (after[it] ?: before[it])?.title },
        )
    }
}
