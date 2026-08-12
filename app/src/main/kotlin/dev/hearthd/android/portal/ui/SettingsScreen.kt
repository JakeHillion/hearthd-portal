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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.TextButton
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
import dev.hearthd.android.portal.settings.THRESHOLD_RANGE
import dev.hearthd.android.portal.settings.UpdateSettings
import dev.hearthd.android.portal.settings.WakeWordSettings
import dev.hearthd.android.portal.settings.formatInterval
import dev.hearthd.android.portal.update.UpdateController
import dev.hearthd.android.portal.update.UpdateStatus
import dev.hearthd.android.portal.update.UpdateUiState
import dev.hearthd.android.portal.wakeword.WakeWordDetector
import dev.hearthd.android.portal.wakeword.WakeWordModel
import dev.hearthd.android.portal.wakeword.WakeWordStatus
import dev.hearthd.android.portal.wakeword.WakeWordUiState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.roundToInt

/** Sections shown in the settings navigation rail. */
private enum class SettingsSection { UPDATES, WAKE_WORD, DEVICE_INFO, SENSORS }

/** Root settings screen: a navigation rail with sections. */
@Composable
fun SettingsScreen(
    settingsRepo: SettingsRepository,
    controller: UpdateController,
    wakeWord: WakeWordDetector,
    onRequestMicPermission: () -> Unit,
    onClose: () -> Unit,
) {
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = UpdateSettings())
    val ui by controller.state.collectAsStateWithLifecycle()
    val wakeSettings by settingsRepo.wakeWord.collectAsStateWithLifecycle(initialValue = WakeWordSettings())
    val wakeUi by wakeWord.state.collectAsStateWithLifecycle()
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
                selected = section == SettingsSection.WAKE_WORD,
                onClick = { section = SettingsSection.WAKE_WORD },
                icon = { Text("🎤") },
                label = { Text(stringResource(R.string.settings_wake_word)) },
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
            SettingsSection.WAKE_WORD -> WakeWordPane(
                settings = wakeSettings,
                ui = wakeUi,
                onEnabledChange = { scope.launch { settingsRepo.setWakeEnabled(it) } },
                onModelChange = { scope.launch { settingsRepo.setWakeModel(it) } },
                onThresholdChange = { scope.launch { settingsRepo.setWakeThreshold(it) } },
                onRequestPermission = onRequestMicPermission,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WakeWordPane(
    settings: WakeWordSettings,
    ui: WakeWordUiState,
    onEnabledChange: (Boolean) -> Unit,
    onModelChange: (WakeWordModel) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onRequestPermission: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.settings_wake_word), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        // Opt-in toggle.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.wake_enable), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.wake_enable_summary),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = settings.enabled, onCheckedChange = onEnabledChange)
        }
        Spacer(Modifier.height(24.dp))

        // Permission prompt, shown only when enabled but the mic isn't granted.
        if (ui.status == WakeWordStatus.NO_PERMISSION) {
            Text(
                stringResource(R.string.wake_permission_needed),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRequestPermission) {
                Text(stringResource(R.string.wake_grant_permission))
            }
            Spacer(Modifier.height(24.dp))
        }

        // Wake-word model.
        Text(stringResource(R.string.wake_model), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WakeWordModel.entries.forEach { model ->
                FilterChip(
                    selected = settings.model == model,
                    onClick = { onModelChange(model) },
                    label = { Text(model.label) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // Sensitivity (detection threshold).
        Text(
            "${stringResource(R.string.wake_sensitivity)} ${"%.2f".format(settings.threshold)}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.wake_sensitivity_summary),
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = settings.threshold,
            onValueChange = onThresholdChange,
            valueRange = THRESHOLD_RANGE,
        )
        Spacer(Modifier.height(24.dp))

        // Live status: what the detector is doing plus the current score.
        Text(wakeStatusLine(ui), style = MaterialTheme.typography.bodyMedium)
        if (ui.status == WakeWordStatus.LISTENING) {
            Text(
                "Score: ${"%.2f".format(ui.lastScore)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        ui.lastDetectionEpochMs?.let {
            Text(
                "Last detected ${DateUtils.getRelativeTimeSpanString(it)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DeviceInfoPane() {
    // The licenses page lives under Device (the "About" section), the way most
    // apps nest their open-source notices. A local flag swaps it in and back.
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    if (showLicenses) {
        LicensesPane(onBack = { showLicenses = false })
        return
    }

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

        // Open-source notices.
        Text(
            stringResource(R.string.licenses_summary),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { showLicenses = true }) {
            Text(stringResource(R.string.settings_licenses))
        }
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
 * Open-source licenses / attribution — the "third-party software" page every
 * app tucks under About. One block per component, showing its copyright and
 * license; the notices are legal text so they stay verbatim in code, not in
 * translatable resources.
 */
@Composable
private fun LicensesPane(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
            Text("←  ${stringResource(R.string.licenses_back)}")
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_licenses), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.licenses_intro), style = MaterialTheme.typography.bodyMedium)

        THIRD_PARTY_LICENSES.forEach { entry ->
            Spacer(Modifier.height(24.dp))
            Text(entry.name, style = MaterialTheme.typography.titleMedium)
            Text(entry.copyright, style = MaterialTheme.typography.bodySmall)
            Text(
                entry.license,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(entry.notice, style = MaterialTheme.typography.bodySmall)
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

private fun wakeStatusLine(ui: WakeWordUiState): String = when (ui.status) {
    WakeWordStatus.DISABLED -> "Disabled"
    WakeWordStatus.NO_PERMISSION -> "Microphone permission needed"
    WakeWordStatus.STARTING -> "Starting…"
    WakeWordStatus.LISTENING -> "Listening for \"${ui.model?.label ?: ""}\""
    WakeWordStatus.ERROR -> "Error: ${ui.message ?: "unknown error"}"
}

/** One third-party component shown on the licenses page. */
private data class LicenseEntry(
    val name: String,
    val copyright: String,
    val license: String,
    val notice: String,
)

/**
 * Third-party components bundled into the app. openWakeWord's models are the
 * only non-permissive item: CC BY-NC-SA 4.0, used unmodified and non-commercially.
 */
private val THIRD_PARTY_LICENSES = listOf(
    LicenseEntry(
        name = "openWakeWord — wake-word models",
        copyright = "© David Scripka and the openWakeWord contributors",
        license = "CC BY-NC-SA 4.0",
        notice = "The bundled melspectrogram, embedding, and wake-word models are " +
            "distributed under the Creative Commons Attribution-NonCommercial-" +
            "ShareAlike 4.0 International license, used unmodified for non-commercial, " +
            "on-device detection. The shared speech-embedding backbone derives from " +
            "Google's speech_embedding model (Apache-2.0). openWakeWord's own source " +
            "code is Apache-2.0; the detection pipeline here is an independent " +
            "reimplementation.\n\n" +
            "License: https://creativecommons.org/licenses/by-nc-sa/4.0/\n" +
            "Project: https://github.com/dscripka/openWakeWord",
    ),
    LicenseEntry(
        name = "ONNX Runtime",
        copyright = "© Microsoft Corporation",
        license = "MIT License",
        notice = "Permission is hereby granted, free of charge, to any person obtaining " +
            "a copy of this software and associated documentation files (the " +
            "\"Software\"), to deal in the Software without restriction, including " +
            "without limitation the rights to use, copy, modify, merge, publish, " +
            "distribute, sublicense, and/or sell copies of the Software, and to permit " +
            "persons to whom the Software is furnished to do so, subject to the " +
            "following conditions:\n\n" +
            "The above copyright notice and this permission notice shall be included in " +
            "all copies or substantial portions of the Software.\n\n" +
            "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR " +
            "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, " +
            "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE " +
            "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER " +
            "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING " +
            "FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER " +
            "DEALINGS IN THE SOFTWARE.",
    ),
    LicenseEntry(
        name = "Jetpack Compose & AndroidX",
        copyright = "© The Android Open Source Project",
        license = "Apache License 2.0",
        notice = "Licensed under the Apache License, Version 2.0.\n" +
            "License: https://www.apache.org/licenses/LICENSE-2.0",
    ),
    LicenseEntry(
        name = "Kotlin & kotlinx.coroutines",
        copyright = "© JetBrains s.r.o. and the Kotlin contributors",
        license = "Apache License 2.0",
        notice = "Licensed under the Apache License, Version 2.0.\n" +
            "License: https://www.apache.org/licenses/LICENSE-2.0",
    ),
)
