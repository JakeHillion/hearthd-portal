package dev.hearthd.android.portal.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persists [UpdateSettings] via a Preferences DataStore. */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("auto_update_enabled")
        val channel = stringPreferencesKey("channel")
        val interval = intPreferencesKey("interval_minutes")
    }

    val settings: Flow<UpdateSettings> = context.dataStore.data.map { prefs ->
        UpdateSettings(
            enabled = prefs[Keys.enabled] ?: false,
            channel = Channel.fromId(prefs[Keys.channel]),
            intervalMinutes = prefs[Keys.interval] ?: 360,
        )
    }

    suspend fun setEnabled(value: Boolean) = context.dataStore.edit { it[Keys.enabled] = value }

    suspend fun setChannel(channel: Channel) =
        context.dataStore.edit { it[Keys.channel] = channel.id }

    suspend fun setIntervalMinutes(minutes: Int) =
        context.dataStore.edit { it[Keys.interval] = minutes }
}
