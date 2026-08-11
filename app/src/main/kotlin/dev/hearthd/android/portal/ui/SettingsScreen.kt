package dev.hearthd.android.portal.ui

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearthd.android.portal.BuildConfig
import dev.hearthd.android.portal.R
import dev.hearthd.android.portal.settings.Channel
import dev.hearthd.android.portal.settings.INTERVAL_STOPS
import dev.hearthd.android.portal.settings.SettingsRepository
import dev.hearthd.android.portal.settings.UpdateSettings
import dev.hearthd.android.portal.settings.formatInterval
import dev.hearthd.android.portal.update.UpdateController
import dev.hearthd.android.portal.update.UpdateStatus
import dev.hearthd.android.portal.update.UpdateUiState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.roundToInt

/** Sections shown in the settings navigation rail. */
private enum class SettingsSection { UPDATES, DEVICE_INFO, SENSORS }

/** Root settings screen: a navigation rail with sections. */
@Composable
fun SettingsScreen(
    settingsRepo: SettingsRepository,
    controller: UpdateController,
    onClose: () -> Unit,
) {
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = UpdateSettings())
    val ui by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(SettingsSection.UPDATES) }

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail {
            // Back to the kiosk surface. With the system bars hidden in kiosk
            // mode this is the way out of settings.
            NavigationRailItem(
                selected = false,
                onClick = onClose,
                icon = { Text("←") },
                label = { Text(stringResource(R.string.settings_close)) },
            )
            NavigationRailItem(
                selected = section == SettingsSection.UPDATES,
                onClick = { section = SettingsSection.UPDATES },
                icon = { Text("↻") },
                label = { Text(stringResource(R.string.settings_updates)) },
            )
            NavigationRailItem(
                selected = section == SettingsSection.DEVICE_INFO,
                onClick = { section = SettingsSection.DEVICE_INFO },
                icon = { Text("ⓘ") },
                label = { Text(stringResource(R.string.settings_device_info)) },
            )
            // Debug-only: sensor probe. Not user-facing polish — kept for
            // capturing what this hardware exposes (esp. proximity, which we
            // want to drive display-off in an empty room).
            NavigationRailItem(
                selected = section == SettingsSection.SENSORS,
                onClick = { section = SettingsSection.SENSORS },
                icon = { Text("◎") },
                label = { Text("Sensors") },
            )
        }
        when (section) {
            SettingsSection.UPDATES -> UpdatesPane(
                settings = settings,
                ui = ui,
                onEnabledChange = { scope.launch { settingsRepo.setEnabled(it) } },
                onChannelChange = { scope.launch { settingsRepo.setChannel(it) } },
                onIntervalChange = { scope.launch { settingsRepo.setIntervalMinutes(it) } },
                onCheckNow = { scope.launch { controller.check(settings.channel) } },
            )
            SettingsSection.DEVICE_INFO -> DeviceInfoPane()
            SettingsSection.SENSORS -> SensorsPane()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdatesPane(
    settings: UpdateSettings,
    ui: UpdateUiState,
    onEnabledChange: (Boolean) -> Unit,
    onChannelChange: (Channel) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onCheckNow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.settings_updates), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        // Opt-in toggle.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.update_auto), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.update_auto_summary),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = settings.enabled, onCheckedChange = onEnabledChange)
        }
        Spacer(Modifier.height(24.dp))

        // Channel.
        Text(stringResource(R.string.update_channel), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Channel.entries.forEach { channel ->
                FilterChip(
                    selected = settings.channel == channel,
                    onClick = { onChannelChange(channel) },
                    label = { Text(channel.label) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // Interval slider over the discrete stops.
        Text(
            "${stringResource(R.string.update_interval)} ${formatInterval(settings.intervalMinutes)}",
            style = MaterialTheme.typography.titleMedium,
        )
        val index = INTERVAL_STOPS.indexOf(settings.intervalMinutes).coerceAtLeast(0)
        Slider(
            value = index.toFloat(),
            onValueChange = {
                onIntervalChange(INTERVAL_STOPS[it.roundToInt().coerceIn(0, INTERVAL_STOPS.lastIndex)])
            },
            valueRange = 0f..INTERVAL_STOPS.lastIndex.toFloat(),
            steps = (INTERVAL_STOPS.size - 2).coerceAtLeast(0),
        )
        Spacer(Modifier.height(24.dp))

        // Current build + last check + status.
        Text(
            stringResource(R.string.version_label, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(statusLine(ui), style = MaterialTheme.typography.bodySmall)
        if (!ui.deviceOwner) {
            Text(
                "Installs need on-screen confirmation. Set device owner via ADB for silent updates.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClick = onCheckNow) {
            Text(stringResource(R.string.update_check_now))
        }
    }
}

@Composable
private fun DeviceInfoPane() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.settings_device_info), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        InfoRow(stringResource(R.string.device_model), "${Build.MANUFACTURER} ${Build.MODEL}")
        InfoRow(stringResource(R.string.device_android), "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        InfoRow(
            stringResource(R.string.device_app_version),
            stringResource(R.string.version_label, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
        )
        Spacer(Modifier.height(24.dp))

        // Escape hatch out of the kiosk into the native Settings app — the only
        // way to reach Developer options and re-enable ADB on a fresh machine.
        Text(
            stringResource(R.string.device_settings_summary),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }) {
            Text(stringResource(R.string.device_open_settings))
        }
    }
}

/**
 * Debug sensor probe. Lists every sensor the platform exposes and shows live
 * readings for proximity and ambient light. The proximity listener is the same
 * plumbing we'd use to switch the display off in an empty room — this pane is
 * where we confirm the sensor exists and see what it actually reports.
 */
@Composable
private fun SensorsPane() {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val all = remember { sm.getSensorList(Sensor.TYPE_ALL) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Sensors (debug)", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Text("Live readings", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LiveSensorRow(sm, Sensor.TYPE_PROXIMITY, "Proximity")
        LiveSensorRow(sm, Sensor.TYPE_LIGHT, "Ambient light")
        Spacer(Modifier.height(24.dp))

        Text("All sensors (${all.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (all.isEmpty()) {
            Text("No sensors reported by this device.", style = MaterialTheme.typography.bodySmall)
        }
        all.forEach { s ->
            Text(
                "${s.name}\n  type=${s.type} vendor=${s.vendor} max=${s.maximumRange} res=${s.resolution}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

/**
 * Registers a listener on [type]'s default sensor while composed and renders its
 * latest value. Unregisters on dispose so we don't hold the sensor open once the
 * pane leaves the screen.
 */
@Composable
private fun LiveSensorRow(sm: SensorManager, type: Int, label: String) {
    val sensor = remember(type) { sm.getDefaultSensor(type) }
    var values by remember(type) { mutableStateOf<FloatArray?>(null) }

    DisposableEffect(sensor) {
        if (sensor == null) return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                values = event.values.copyOf()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    val text = when {
        sensor == null -> "not present"
        values == null -> "waiting…"
        else -> {
            val v = values!!
            val raw = v.joinToString(", ") { it.toString() }
            // Proximity is typically binary: values[0] < maxRange means "near".
            if (type == Sensor.TYPE_PROXIMITY && v.isNotEmpty()) {
                val near = v[0] < sensor.maximumRange
                "$raw  (${if (near) "NEAR" else "FAR"}, max ${sensor.maximumRange})"
            } else {
                "$raw  (max ${sensor.maximumRange})"
            }
        }
    }
    InfoRow(label, text)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun statusLine(ui: UpdateUiState): String {
    val status = when (ui.status) {
        UpdateStatus.IDLE -> "Idle"
        UpdateStatus.CHECKING -> "Checking…"
        UpdateStatus.DOWNLOADING -> "Downloading…"
        UpdateStatus.INSTALLING -> "Installing…"
        UpdateStatus.UP_TO_DATE -> "Up to date"
        UpdateStatus.FAILED -> "Failed: ${ui.message ?: "unknown error"}"
    }
    val checked = ui.lastCheckedEpochMs?.let {
        " · checked ${DateUtils.getRelativeTimeSpanString(it)}"
    } ?: ""
    return status + checked
}
