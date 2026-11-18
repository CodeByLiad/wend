package com.jinatra.wend.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.DayOfWeek
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class Accountability { GENTLE, BALANCED, DIRECT }
enum class TimeFormat { SYSTEM, TWELVE_HOUR, TWENTY_FOUR_HOUR }
enum class TodayCard { PROGRESS, TIMELINE, HABITS, TRACKERS, REVIEW }

data class UserSettings(
    val onboardingComplete: Boolean = false,
    val wake: Int = 420,
    val bedtime: Int = 1380,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accountability: Accountability = Accountability.BALANCED,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val autoApply: Boolean = false,
    val quietStart: Int = 1320,
    val quietEnd: Int = 420,
    val reminderRepeats: Int = 2,
    val repeatMinutes: Int = 10,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val todayCards: List<TodayCard> = TodayCard.entries,
) {
    init {
        require(wake in 0..2879 && bedtime in 1..2880 && wake < bedtime && bedtime % 5 == 0)
        require(quietStart in 0..1439 && quietEnd in 0..1439)
        require(reminderRepeats in 0..10 && repeatMinutes in 1..120)
        require(todayCards.distinct().size == todayCards.size)
    }
}

@Singleton
class SettingsRepository @Inject internal constructor(private val db: WendDatabase, private val clock: Clock) {
    fun observe(): Flow<UserSettings> = db.dao().observePreference(NAME).map { it?.valueJson?.let(::decodeSettings) ?: UserSettings() }
    suspend fun get(): UserSettings = db.dao().preference(NAME)?.valueJson?.let(::decodeSettings) ?: UserSettings()
    suspend fun update(change: (UserSettings) -> UserSettings): UserSettings = db.withTransaction {
        val old = db.dao().preference(NAME)
        val settings = change(old?.valueJson?.let(::decodeSettings) ?: UserSettings())
        val now = clock.millis()
        val value = PreferenceEntity(old?.id ?: UUID.randomUUID().toString(), NAME, encodeSettings(settings), old?.createdAt ?: now, now)
        if (old == null) db.dao().insertPreference(value) else db.dao().updatePreference(value)
        settings
    }

    private companion object {
        const val NAME = "user-settings-v1"
    }
}

private fun encodeSettings(s: UserSettings): String = JSONObject().put("version", 1).put("onboardingComplete", s.onboardingComplete)
    .put("wake", s.wake).put("bedtime", s.bedtime).put("theme", s.theme.name).put("accountability", s.accountability.name)
    .put("timeFormat", s.timeFormat.name).put("autoApply", s.autoApply).put("quietStart", s.quietStart).put("quietEnd", s.quietEnd)
    .put("reminderRepeats", s.reminderRepeats).put("repeatMinutes", s.repeatMinutes).put("weekStart", s.weekStart.name)
    .put("todayCards", JSONArray(s.todayCards.map { it.name })).toString()

private fun decodeSettings(text: String): UserSettings {
    val s = JSONObject(text)
    require(s.getInt("version") == 1)
    val cards = s.getJSONArray("todayCards")
    return UserSettings(
        s.getBoolean("onboardingComplete"), s.getInt("wake"), s.getInt("bedtime"), ThemeMode.valueOf(s.getString("theme")),
        Accountability.valueOf(s.getString("accountability")), TimeFormat.valueOf(s.getString("timeFormat")), s.getBoolean("autoApply"),
        s.getInt("quietStart"), s.getInt("quietEnd"), s.getInt("reminderRepeats"), s.getInt("repeatMinutes"), DayOfWeek.valueOf(s.getString("weekStart")),
        (0 until cards.length()).map { TodayCard.valueOf(cards.getString(it)) },
    )
}
