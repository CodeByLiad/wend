package com.jinatra.wend.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
internal interface WendDao {
    @Transaction
    @Query("SELECT * FROM days WHERE date = :date")
    suspend fun day(date: String): DayWithBlocks?

    @Transaction
    @Query("SELECT * FROM days WHERE date = :date")
    fun observeDay(date: String): Flow<DayWithBlocks?>

    @Query("SELECT * FROM days ORDER BY date")
    suspend fun days(): List<DayEntity>

    @Insert suspend fun insertDay(day: DayEntity)

    @Update suspend fun updateDay(day: DayEntity)

    @Insert suspend fun insertBlocks(blocks: List<PlannedBlockEntity>)

    @Query("DELETE FROM planned_blocks WHERE dayId = :dayId")
    suspend fun deleteBlocks(dayId: String)

    @Insert suspend fun insertChange(change: ChangeLogEntity)

    @Update suspend fun updateChange(change: ChangeLogEntity)

    @Query("SELECT * FROM change_log WHERE dayId = :dayId ORDER BY revision")
    suspend fun changes(dayId: String): List<ChangeLogEntity>

    @Query("SELECT * FROM change_log WHERE dayId = :dayId AND undoable = 1 AND undoneBy IS NULL ORDER BY revision DESC LIMIT 1")
    suspend fun latestUndoable(dayId: String): ChangeLogEntity?

    @Query("SELECT * FROM preferences WHERE name = :name")
    suspend fun preference(name: String): PreferenceEntity?

    @Query("SELECT * FROM preferences WHERE name = :name")
    fun observePreference(name: String): Flow<PreferenceEntity?>

    @Insert suspend fun insertPreference(preference: PreferenceEntity)

    @Update suspend fun updatePreference(preference: PreferenceEntity)
}

@Database(entities = [DayEntity::class, PlannedBlockEntity::class, ChangeLogEntity::class, PreferenceEntity::class], version = 1, exportSchema = true)
internal abstract class WendDatabase : RoomDatabase() {
    abstract fun dao(): WendDao
}
