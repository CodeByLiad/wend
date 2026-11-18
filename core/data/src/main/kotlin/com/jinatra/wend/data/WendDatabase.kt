package com.jinatra.wend.data

import androidx.room.AutoMigration
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
    @Query("SELECT * FROM routines ORDER BY name, id")
    fun observeRoutines(): Flow<List<RoutineWithActivities>>

    @Transaction
    @Query("SELECT * FROM routines ORDER BY name, id")
    suspend fun routines(): List<RoutineWithActivities>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun routine(id: String): RoutineWithActivities?

    @Query("SELECT block_origins.* FROM block_origins INNER JOIN days ON days.id = block_origins.dayId WHERE days.date = :date")
    fun observeOrigins(date: String): Flow<List<BlockOriginEntity>>

    @Insert suspend fun insertOrigins(origins: List<BlockOriginEntity>)

    @Insert suspend fun insertRoutine(routine: RoutineEntity)

    @Update suspend fun updateRoutine(routine: RoutineEntity)

    @Query("SELECT * FROM activity_templates WHERE id IN (:ids)")
    suspend fun templates(ids: List<String>): List<ActivityTemplateEntity>

    @Insert suspend fun insertTemplates(templates: List<ActivityTemplateEntity>)

    @Query("DELETE FROM activity_templates WHERE routineId = :id")
    suspend fun deleteTemplates(id: String)

    @Query("DELETE FROM routines WHERE id = :id AND revision = :revision")
    suspend fun deleteRoutine(id: String, revision: Long): Int

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

@Database(
    entities = [DayEntity::class, PlannedBlockEntity::class, ChangeLogEntity::class, PreferenceEntity::class, RoutineEntity::class, ActivityTemplateEntity::class, BlockOriginEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
internal abstract class WendDatabase : RoomDatabase() {
    abstract fun dao(): WendDao
}
