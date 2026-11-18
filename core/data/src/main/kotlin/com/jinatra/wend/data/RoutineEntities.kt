package com.jinatra.wend.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "routines")
internal data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dayType: String,
    val recurrenceJson: String,
    val enabled: Boolean,
    val revision: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "activity_templates",
    foreignKeys = [ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("routineId"), Index(value = ["routineId", "position"], unique = true)],
)
internal data class ActivityTemplateEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val position: Int,
    val blockJson: String,
    val category: String,
    val icon: String,
    val createdAt: Long,
    val updatedAt: Long,
)

internal data class RoutineWithActivities(
    @Embedded val record: RoutineEntity,
    @Relation(parentColumn = "id", entityColumn = "routineId") val activities: List<ActivityTemplateEntity>,
)

/** Instance metadata is retained through schedule undo and routine deletion. */
@Entity(
    tableName = "block_origins",
    foreignKeys = [ForeignKey(entity = DayEntity::class, parentColumns = ["id"], childColumns = ["dayId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dayId")],
)
internal data class BlockOriginEntity(
    @PrimaryKey val id: String,
    val dayId: String,
    val templateId: String,
    val routineId: String,
    val category: String,
    val icon: String,
    val createdAt: Long,
    val updatedAt: Long,
)
