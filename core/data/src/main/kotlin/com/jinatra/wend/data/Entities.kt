package com.jinatra.wend.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "days", indices = [Index(value = ["date"], unique = true)])
internal data class DayEntity(
    @PrimaryKey val id: String,
    val date: String,
    val wake: Int,
    val bedtime: Int,
    val revision: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "planned_blocks",
    foreignKeys = [ForeignKey(entity = DayEntity::class, parentColumns = ["id"], childColumns = ["dayId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dayId"), Index(value = ["dayId", "position"], unique = true)],
)
internal data class PlannedBlockEntity(
    @PrimaryKey val id: String,
    val dayId: String,
    val position: Int,
    val title: String,
    val kind: String,
    val start: Int,
    val end: Int,
    val duration: Int,
    val minDuration: Int,
    val priority: String?,
    val windowJson: String?,
    val afterId: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "change_log",
    foreignKeys = [ForeignKey(entity = DayEntity::class, parentColumns = ["id"], childColumns = ["dayId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["dayId", "revision"], unique = true)],
)
internal data class ChangeLogEntity(
    @PrimaryKey val id: String,
    val dayId: String,
    val revision: Long,
    val operation: String,
    val source: String,
    val beforeJson: String?,
    val afterJson: String,
    val changesJson: String,
    val undoable: Boolean,
    val undoneBy: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "preferences", indices = [Index(value = ["name"], unique = true)])
internal data class PreferenceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val valueJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

internal data class DayWithBlocks(
    @Embedded val record: DayEntity,
    @Relation(parentColumn = "id", entityColumn = "dayId") val blocks: List<PlannedBlockEntity>,
)
