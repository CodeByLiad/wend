package com.jinatra.wend.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.Command
import com.jinatra.wend.engine.Kind
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Priority
import com.jinatra.wend.engine.ProposalStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoutineDataTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val date = LocalDate.of(2026, 11, 18)
    private val first = "00000000-0000-4000-8000-000000000011"
    private val second = "00000000-0000-4000-8000-000000000012"
    private fun draft() = RoutineDraft(
        UUID.randomUUID().toString(),
        "Synthetic routine",
        "workday",
        Recurrence.Weekdays(setOf(DayOfWeek.WEDNESDAY)),
        listOf(
            ActivityTemplate(Block(first, "Synthetic first", Kind.FLEXIBLE, Minute(540), Minute(570), 30, 15, Priority.NORMAL), "focus", "work"),
            ActivityTemplate(Block(second, "Synthetic next", Kind.FLEXIBLE, Minute(570), Minute(600), 30, 15, Priority.NORMAL, after = first), "care", "shower"),
        ),
    )
    private inner class Fixture {
        val name = "routine-" + UUID.randomUUID() + ".db"
        var db = EncryptedDatabaseFactory.open(context, name)
        val schedules get() = ScheduleRepository(db, Clock.systemUTC())
        val routines get() = RoutineRepository(db, schedules, Clock.systemUTC())
        fun reopen() {
            db.close()
            db = EncryptedDatabaseFactory.open(context, name)
        }
    }
    private fun fixture(test: suspend (Fixture) -> Unit) = runBlocking {
        val f = Fixture()
        try {
            test(f)
        } finally {
            f.db.close()
            listOf("", "-wal", "-shm", "-journal").forEach { File(context.noBackupFilesDir, f.name + it).delete() }
            DatabaseKeyVault(context, f.name).delete()
        }
    }

    @Test fun templatesRecurrenceAndMetadataReopenExactly() = fixture { f ->
        val saved = f.routines.save(draft()) as RoutineSaveResult.Saved
        f.reopen()
        assertEquals(saved.routine, f.routines.get(saved.routine.draft.id))
        assertEquals(listOf(saved.routine), f.routines.observe().first())
        assertEquals(listOf(saved.routine), f.routines.matching(date, "workday"))
        assertTrue(f.routines.matching(date.plusDays(1)).isEmpty())
        assertTrue(f.routines.matching(date, "weekend").isEmpty())
        assertFalse(File(context.noBackupFilesDir, f.name).readBytes().toString(Charsets.ISO_8859_1).contains("Synthetic routine"))
    }

    @Test fun staleSavesAndGenerationNeverOverwriteChanges() = fixture { f ->
        val saved = (f.routines.save(draft()) as RoutineSaveResult.Saved).routine
        val preview = requireNotNull(f.routines.previewGeneration(saved.draft.id, date, Minute(420), Minute(1380), Minute(420)))
        val updated = saved.draft.copy(name = "Updated")
        assertTrue(f.routines.save(updated) is RoutineSaveResult.Stale)
        assertTrue(f.routines.save(updated, saved.revision) is RoutineSaveResult.Saved)
        assertTrue(f.routines.save(saved.draft, saved.revision) is RoutineSaveResult.Stale)
        assertEquals(GenerationResult.Stale, f.routines.applyGeneration(preview))
        assertEquals(null, f.schedules.get(date))
    }

    @Test fun generationRemapsInstancesAndExistingDaysSurviveRoutineEditsAndDeletion() = fixture { f ->
        val saved = (f.routines.save(draft()) as RoutineSaveResult.Saved).routine
        suspend fun preview(day: LocalDate) = requireNotNull(f.routines.previewGeneration(saved.draft.id, day, Minute(420), Minute(1380), Minute(420)))
        val firstPreview = preview(date)
        val otherPreview = preview(date)
        val generated = f.routines.applyGeneration(firstPreview) as GenerationResult.Generated
        val blocks = generated.stored.day.blocks
        assertNotEquals(first, blocks[0].id)
        assertNotEquals(second, blocks[1].id)
        assertEquals(blocks[0].id, blocks[1].after)
        val details = f.schedules.observeDetails(date).first()
        assertEquals(ActivityDetails(first, saved.draft.id, "focus", "work"), details[blocks[0].id])
        val secondDay = f.routines.applyGeneration(preview(date.plusDays(1))) as GenerationResult.Generated
        assertTrue(secondDay.stored.day.blocks.none { next -> blocks.any { it.id == next.id } })
        assertEquals(GenerationResult.Existing(generated.stored), f.routines.applyGeneration(otherPreview))
        val edited = (f.routines.save(saved.draft.copy(activities = saved.draft.activities.map { it.copy(block = it.block.copy(title = "Edited template")) }), saved.revision) as RoutineSaveResult.Saved).routine
        assertFalse(f.routines.delete(saved.draft.id, saved.revision))
        assertTrue(f.routines.delete(edited.draft.id, edited.revision))
        f.reopen()
        assertEquals(generated.stored, f.schedules.get(date))
        assertEquals(details, f.schedules.observeDetails(date).first())
        assertEquals(1, f.schedules.history(date).size)
        assertEquals("GENERATE_DAY", f.schedules.history(date).single().operation)
    }

    @Test fun oneOffEditsNeverAlterTheRoutine() = fixture { f ->
        val saved = (f.routines.save(draft()) as RoutineSaveResult.Saved).routine
        val preview = requireNotNull(f.routines.previewGeneration(saved.draft.id, date, Minute(420), Minute(1380), Minute(420)))
        val generated = f.routines.applyGeneration(preview) as GenerationResult.Generated
        val block = generated.stored.day.blocks.first()
        val edit = requireNotNull(f.schedules.preview(date, Command.EditBlock(block.id, block.copy(title = "One-off")), Minute(420)))
        assertTrue(f.schedules.apply(edit) is ScheduleResult.Applied)
        assertEquals(saved, f.routines.get(saved.draft.id))
    }

    @Test fun invalidGenerationCreatesNoPartialDayOrHistory() = fixture { f ->
        val original = draft()
        val conflicting = original.copy(activities = original.activities.map { it.copy(block = it.block.copy(kind = Kind.FIXED, start = Minute(540), end = Minute(570), after = null)) })
        val saved = (f.routines.save(conflicting) as RoutineSaveResult.Saved).routine
        val preview = requireNotNull(f.routines.previewGeneration(saved.draft.id, date, Minute(420), Minute(1380), Minute(420)))
        assertEquals(ProposalStatus.CONFLICT, preview.proposal.status)
        assertTrue(f.routines.applyGeneration(preview) is GenerationResult.NotApplicable)
        assertEquals(null, f.schedules.get(date))
        assertTrue(f.schedules.history(date).isEmpty())
    }

    @Test fun duplicateTemplateOwnershipIsRejectedWithoutDamagingEitherRoutine() = fixture { f ->
        val saved = (f.routines.save(draft()) as RoutineSaveResult.Saved).routine
        val other = saved.draft.copy(id = UUID.randomUUID().toString())
        val result = f.routines.save(other) as RoutineSaveResult.Invalid
        assertEquals(setOf(RoutineFieldError.TEMPLATE_IN_OTHER_ROUTINE), result.fields)
        assertEquals(saved, f.routines.get(saved.draft.id))
        assertEquals(null, f.routines.get(other.id))
    }
}
