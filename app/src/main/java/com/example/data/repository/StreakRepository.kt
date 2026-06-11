package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StreakRepository(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "streak_prefs")
        private val KEY_STREAK = intPreferencesKey("current_streak")
        private val KEY_LAST_ACTIVE_DATE = stringPreferencesKey("last_active_date")
    }

    val streakFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_STREAK] ?: 0
    }

    suspend fun checkAndIncrementStreak() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        try {
            context.dataStore.edit { preferences ->
                val lastActive = preferences[KEY_LAST_ACTIVE_DATE]
                val currentStreak = preferences[KEY_STREAK] ?: 0

                if (lastActive == null) {
                    // First time
                    preferences[KEY_STREAK] = 1
                    preferences[KEY_LAST_ACTIVE_DATE] = todayStr
                } else if (lastActive == todayStr) {
                    // Today already recorded, no change
                } else {
                    val lastActiveDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(lastActive)
                    if (lastActiveDate != null) {
                        val calYesterday = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, -1)
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        
                        val calLast = Calendar.getInstance().apply {
                            time = lastActiveDate
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        if (calLast.timeInMillis == calYesterday.timeInMillis) {
                            // Consecutive day
                            preferences[KEY_STREAK] = currentStreak + 1
                        } else {
                            // Broken streak (more than 1 day ago)
                            preferences[KEY_STREAK] = 1
                        }
                    } else {
                        preferences[KEY_STREAK] = 1
                    }
                    preferences[KEY_LAST_ACTIVE_DATE] = todayStr
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("StreakRepository", "Failed to update streak in DataStore", e)
        }
    }
}
