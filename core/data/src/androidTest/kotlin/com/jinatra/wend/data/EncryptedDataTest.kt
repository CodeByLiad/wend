package com.jinatra.wend.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.BlockStatus
import com.jinatra.wend.engine.Change
import com.jinatra.wend.engine.ChangeType
import com.jinatra.wend.engine.Command
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.Kind
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Priority
import com.jinatra.wend.engine.ProposalStatus
import com.jinatra.wend.engine.Reason
import com.jinatra.wend.engine.Window
import com.jinatra.wend.engine.WindowBound
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EncryptedDataTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val date = LocalDate.of(2026, 11, 18)
    private val firstId = "00000000-0000-4000-8000-000000000001"
    private val secondId = "00000000-0000-4000-8000-000000000002"
    private val title = "PRIVATE_SYNTHETIC_SCHEDULE_SENTINEL_72964"

    private inner class Fixture(val name: String) {
        var db = EncryptedDatabaseFactory.open(context, name)
        val repo get() = ScheduleRepository(db, Clock.fixed(Instant.parse("2026-11-18T00:00:00Z"), ZoneOffset.UTC))
        val file get() = File(context.noBackupFilesDir, name)
        fun reopen() {
            db.close()
            db = EncryptedDatabaseFactory.open(context, name)
        }
        suspend fun create(day: LocalDate = date) = repo.create(day, Minute(420), Minute(1380))
        suspend fun add(id: String, at: Int = 420, day: LocalDate = date): StoredDay {
            val block = Block(
                id, title, Kind.FLEXIBLE, Minute(at), Minute(at + 60), 60, 30, Priority.NORMAL,
                Window(WindowBound.Absolute(Minute(420)), WindowBound.BeforeBedtime(60)),
            )
            val request = requireNotNull(repo.preview(day, Command.AddBlock(block, Minute(at)), Minute(0)))
            return (repo.apply(request) as ScheduleResult.Applied).stored
        }
    }

    private fun fixture(test: suspend (Fixture) -> Unit) = runBlocking {
        val fixture = Fixture("test-" + UUID.randomUUID() + ".db")
        try {
            test(fixture)
        } finally {
            fixture.db.close()
            listOf("", "-wal", "-shm", "-journal").forEach { File(fixture.file.path + it).delete() }
            DatabaseKeyVault(context, fixture.name).delete()
        }
    }

    @Test fun injectionDoesNotOpenTheDatabaseOrLoadItsKey() = fixture { f ->
        assertFalse(f.file.exists())
        assertFalse(File(context.noBackupFilesDir, f.name + ".key").exists())
        f.create()
        assertTrue(f.file.exists())
        assertTrue(File(context.noBackupFilesDir, f.name + ".key").exists())
    }

    @Test fun databaseIsEncryptedAndReopensWithExactHistory() = fixture { f ->
        f.create()
        val stored = f.add(firstId)
        val history = f.repo.history(date)
        f.db.close()
        val bytes = f.file.readBytes()
        assertFalse(bytes.copyOfRange(0, 16).toString(Charsets.ISO_8859_1).startsWith("SQLite format"))
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(title))
        assertEquals(61, File(context.noBackupFilesDir, f.name + ".key").length().toInt())
        f.reopen()
        assertEquals(stored, f.repo.get(date))
        assertEquals(history, f.repo.history(date))
        assertEquals(stored, f.repo.observe(date).first())
    }

    @Test fun snapshotsPreserveRelativeBoundsNullWindowsStatusesAndChangeCauses() {
        val blocks = BlockStatus.entries.mapIndexed { index, status ->
            Block(
                UUID.randomUUID().toString(), "Quotes \" and newline\n synthetic", Kind.FIXED, Minute(420 + index * 60), Minute(450 + index * 60), 60, 15,
                if (index % 2 == 0) null else Priority.HIGH,
                when (index % 3) {
                    0 -> null
                    1 -> Window()
                    else -> Window(WindowBound.Absolute(Minute(300)), WindowBound.BeforeBedtime(0))
                },
                null, status,
            )
        }
        val day = Day(date, Minute(423), Minute(1470), blocks)
        assertEquals(day, DayCodec.decode(DayCodec.encode(day)))
        val changes = listOf(Change(firstId, setOf(ChangeType.MOVED, ChangeType.SHRUNK), Reason.DEPENDENCY_SHRINK, secondId))
        assertEquals(changes, DayCodec.decodeChanges(DayCodec.encodeChanges(changes)))
    }

    @Test fun staleApprovalsNeverOverwriteAChangedSchedule() = fixture { f ->
        f.create()
        f.add(firstId)
        val old = requireNotNull(f.repo.preview(date, Command.SetBedtime(Minute(1320)), Minute(0)))
        val current = requireNotNull(f.repo.preview(date, Command.SetBedtime(Minute(1260)), Minute(0)))
        val applied = f.repo.apply(current) as ScheduleResult.Applied
        assertEquals(ScheduleResult.Stale, f.repo.apply(old))
        assertEquals(applied.stored, f.repo.get(date))
        assertEquals(3, f.repo.history(date).size)
    }

    @Test fun undoSurvivesRestartAndRestoresStatusAndBlocksExactly() = fixture { f ->
        val empty = f.create()
        val a = f.add(firstId)
        val ab = f.add(secondId, 480)
        val started = (f.repo.setStatus(date, ab.revision, firstId, StatusAction.START) as ScheduleResult.Applied).stored
        val done = (f.repo.setStatus(date, started.revision, firstId, StatusAction.DONE) as ScheduleResult.Applied).stored
        f.reopen()
        var revision = done.revision
        for (expected in listOf(started.day, ab.day, a.day, empty.day)) {
            val restored = f.repo.undo(date, revision, Minute(700)) as ScheduleResult.Applied
            assertEquals(expected, restored.stored.day)
            assertEquals(ProposalStatus.OK, restored.proposal!!.status)
            revision = restored.stored.revision
        }
        assertEquals(ScheduleResult.NoHistory, f.repo.undo(date, revision, Minute(700)))
        assertFalse(f.repo.canUndo(date))
        assertEquals(9, f.repo.history(date).size)
    }

    @Test fun failedTransactionRollsBackTheDayAndItsChangeLog() = fixture { f ->
        f.create()
        val original = f.add(firstId)
        val tomorrow = date.plusDays(1)
        val empty = f.create(tomorrow)
        val history = f.repo.history(tomorrow)
        var rejected = false
        try {
            f.add(firstId, day = tomorrow)
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
        assertEquals(empty, f.repo.get(tomorrow))
        assertEquals(history, f.repo.history(tomorrow))
        assertEquals(original, f.repo.get(date))
    }

    @Test fun wrongKeysNeverDeleteOrReplaceTheEncryptedDatabase() = fixture { f ->
        f.create()
        val expected = f.add(firstId)
        f.db.close()
        val before = f.file.readBytes()
        val wrong = EncryptedDatabaseFactory.openWithKey(context, f.file, ByteArray(32) { 77 })
        var rejected = false
        try {
            wrong.dao().days()
        } catch (_: Exception) {
            rejected = true
        } finally {
            wrong.close()
        }
        assertTrue(rejected)
        assertArrayEquals(before, f.file.readBytes())
        f.reopen()
        assertEquals(expected, f.repo.get(date))
    }

    @Test fun missingWrappedKeyFailsClosedWithoutCreatingANewKey() = fixture { f ->
        f.create()
        f.add(firstId)
        f.db.close()
        val before = f.file.readBytes()
        val wrapped = File(context.noBackupFilesDir, f.name + ".key")
        assertTrue(wrapped.delete())
        var rejected = false
        try {
            EncryptedDatabaseFactory.open(context, f.name).let { missing ->
                try {
                    missing.dao().days()
                } finally {
                    missing.close()
                }
            }
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
        assertFalse(wrapped.exists())
        assertArrayEquals(before, f.file.readBytes())
    }

    @Test fun settingsPersistCardOrderAndPreferencesAcrossRestart() = fixture { f ->
        val settings = SettingsRepository(f.db, Clock.systemUTC())
        assertEquals(UserSettings(), settings.observe().first())
        val chosen = settings.update {
            it.copy(
                theme = ThemeMode.DARK,
                accountability = Accountability.GENTLE,
                autoApply = true,
                todayCards = listOf(TodayCard.TRACKERS, TodayCard.TIMELINE),
                onboardingComplete = true,
            )
        }
        f.reopen()
        assertEquals(chosen, SettingsRepository(f.db, Clock.systemUTC()).get())
    }

    @Test fun rolloverChangesOnlyUnfinishedStatusesAndIsIdempotent() = fixture { f ->
        f.create()
        val planned = f.add(firstId)
        val rolled = f.repo.rollover(date, date.plusDays(1)) as ScheduleResult.Applied
        assertEquals(planned.day.copy(blocks = listOf(planned.day.blocks.single().copy(status = BlockStatus.MISSED))), rolled.stored.day)
        assertTrue(f.repo.rollover(date, date.plusDays(1)) is ScheduleResult.NoChange)
        assertEquals(3, f.repo.history(date).size)
    }
}
