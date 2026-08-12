package dev.hearthd.android.portal.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.hearthd.android.portal.wakeword.WakeWordModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persists [UpdateSettings] via a Preferences DataStore. */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("auto_update_enabled")
        val channel = stringPreferencesKey("channel")
        val interval = intPreferencesKey("interval_minutes")
        val wakeEnabled = booleanPreferencesKey("wake_enabled")
        val wakeModel = stringPreferencesKey("wake_model")
        val wakeThreshold = floatPreferencesKey("wake_threshold")
    }

    val settings: Flow<UpdateSettings> = context.dataStore.data.map { prefs ->
        UpdateSettings(
            enabled = prefs[Keys.enabled] ?: false,
            channel = Channel.fromId(prefs[Keys.channel]),
            intervalMinutes = prefs[Keys.interval] ?: 360,
        )
    }

    val wakeWord: Flow<WakeWordSettings> = context.dataStore.data.map { prefs ->
        val model = WakeWordModel.fromId(prefs[Keys.wakeModel])
        WakeWordSettings(
            enabled = prefs[Keys.wakeEnabled] ?: false,
            model = model,
            // Fall back to the model's tuned default until the user moves the slider.
            threshold = prefs[Keys.wakeThreshold] ?: model.defaultThreshold,
        )
    }

    suspend fun setEnabled(value: Boolean) = context.dataStore.edit { it[Keys.enabled] = value }

    suspend fun setChannel(channel: Channel) =
        context.dataStore.edit { it[Keys.channel] = channel.id }

    suspend fun setIntervalMinutes(minutes: Int) =
        context.dataStore.edit { it[Keys.interval] = minutes }

    suspend fun setWakeEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.wakeEnabled] = value }

    suspend fun setWakeModel(model: WakeWordModel) =
        context.dataStore.edit { it[Keys.wakeModel] = model.id }

    suspend fun setWakeThreshold(threshold: Float) =
        context.dataStore.edit { it[Keys.wakeThreshold] = threshold }
}
