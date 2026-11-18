package com.jinatra.wend.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.Minute
import kotlinx.coroutines.runBlocking
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DataMigrationTest {
    @Test fun encryptedSchemaOneUpgradesWithoutLosingDaysSettingsOrHistory() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-" + UUID.randomUUID() + ".db"
        val file = File(context.noBackupFilesDir, name)
        val vault = DatabaseKeyVault(context, name)
        val password = vault.loadOrCreate(false)
        val schema = JSONObject(instrumentation.context.assets.open("com.jinatra.wend.data.WendDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val date = LocalDate.of(2026, 11, 18)
        val day = Day(date, Minute(420), Minute(1380), emptyList())
        val dayId = UUID.randomUUID().toString()
        System.loadLibrary("sqlcipher")
        Logger.setTarget(NoopTarget())
        val old = object : SQLiteOpenHelper(context, file.absolutePath, password, null, 1, 0, DatabaseErrorHandler { _, _ -> error("Old encrypted fixture could not open") }, null, false) {
            override fun onCreate(db: SQLiteDatabase) {
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.getJSONArray("indices")
                    for (i in 0 until indices.length()) db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Old schema fixture cannot upgrade")
        }
        var upgraded: WendDatabase? = null
        try {
            old.writableDatabase.apply {
                execSQL("INSERT INTO days VALUES (?, ?, 420, 1380, 0, 1, 1)", arrayOf(dayId, date.toString()))
                execSQL("INSERT INTO preferences VALUES (?, 'migration-sentinel', '{\"version\":1}', 2, 3)", arrayOf(UUID.randomUUID().toString()))
                execSQL("INSERT INTO change_log VALUES (?, ?, 0, 'CREATE_DAY', 'SYSTEM', NULL, ?, '[]', 0, NULL, 1, 1)", arrayOf(UUID.randomUUID().toString(), dayId, DayCodec.encode(day)))
            }
            old.close()
            password.fill(0)
            upgraded = EncryptedDatabaseFactory.open(context, name)
            val schedules = ScheduleRepository(upgraded, Clock.systemUTC())
            assertEquals(StoredDay(dayId, 0, day, 1, 1), schedules.get(date))
            assertEquals(day, schedules.history(date).single().after)
            assertEquals("{\"version\":1}", upgraded.dao().preference("migration-sentinel")!!.valueJson)
            assertEquals(2, upgraded.openHelper.writableDatabase.version)
            assertTrue(upgraded.dao().routines().isEmpty())
        } finally {
            old.close()
            password.fill(0)
            upgraded?.close()
            listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
            vault.delete()
        }
    }
}
