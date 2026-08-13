package dev.hearthd.android.portal.dashboard.widgets

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.hearthd.android.portal.dashboard.Binding
import dev.hearthd.android.portal.dashboard.ScreensaverScaffold
import dev.hearthd.android.portal.dashboard.Widget
import dev.hearthd.android.portal.dashboard.parseWidget
import dev.hearthd.android.portal.dashboard.resolveObject
import dev.hearthd.android.portal.dashboard.resolveString
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * A digital photo frame that doubles as the screensaver: the resting state is a
 * slideshow of [photos] with the date, time, and weather overlaid; a touch
 * reveals [child] (the dashboard) for [dwellSeconds] before the photos return.
 * It wraps its child so it can nest under the ambient screensaver — the photos
 * rest by day, and a dark room lets the ambient clock take over above it.
 *
 * Everything dynamic comes from state: [photos] is a slot holding an array of
 * image URLs, [weather] the same `{temp_c, condition}` object the weather widget
 * reads, and [timezone] a live IANA id. The overlay's layout and formatting are
 * fixed. With no photos configured it falls back to the overlay on black, still
 * dimming the dashboard while idle.
 */
data class PhotoScreensaverWidget(
    val child: Widget,
    val photos: Binding,
    val timezone: Binding,
    val weather: Binding,
    val dwellSeconds: Long,
    val rotateSeconds: Long,
) : Widget {
    @Composable
    override fun Render(state: JSONObject, modifier: Modifier) {
        ScreensaverScaffold(
            // The photo frame is the resting state, so it's always armed; the
            // scaffold shows it whenever the surface is idle.
            armed = true,
            dwellMillis = dwellSeconds * 1_000L,
            saver = { PhotoFrame(state) },
            child = { child.Render(state, Modifier.fillMaxSize()) },
            modifier = modifier.fillMaxSize(),
        )
    }

    @Composable
    private fun PhotoFrame(state: JSONObject) {
        val urls = resolveUrls(state)

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (urls.isNotEmpty()) {
                var index by remember(urls) { mutableIntStateOf(0) }
                LaunchedEffect(urls, rotateSeconds) {
                    if (urls.size <= 1) return@LaunchedEffect
                    while (true) {
                        delay(rotateSeconds * 1_000L)
                        index = (index + 1) % urls.size
                    }
                }
                Crossfade(
                    targetState = index.coerceIn(urls.indices),
                    animationSpec = tween(PHOTO_FADE_MILLIS),
                    label = "photo",
                ) { i ->
                    AsyncImage(
                        model = urls[i],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // A soft bottom scrim keeps the white overlay legible over a bright
            // photo without darkening the whole frame.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.65f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f),
                        ),
                    ),
            )

            Overlay(state)
        }
    }

    /** Date and time bottom-left, weather to its right. Fixed layout. */
    @Composable
    private fun BoxScope.Overlay(state: JSONObject) {
        val zoneId = timezone.resolveString(state)
        val zone = remember(zoneId) {
            runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
        }
        val dateFormat = remember { DateTimeFormatter.ofPattern("EEE d MMM") }
        val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
        var now by remember(zone) { mutableStateOf(ZonedDateTime.now(zone)) }
        LaunchedEffect(zone) {
            while (true) {
                now = ZonedDateTime.now(zone)
                delay(10_000)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(32.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column {
                Text(
                    text = now.format(dateFormat),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Text(
                    text = now.format(timeFormat),
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                )
            }

            val data = weather.resolveObject(state)
            val tempC = data?.optDouble("temp_c")?.takeUnless { it.isNaN() }
            val condition = data?.optString("condition").orEmpty()
            if (tempC != null || condition.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(conditionGlyph(condition), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = tempC?.let { "${it.roundToInt()}°" } ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                }
            }
        }
    }

    private fun resolveUrls(state: JSONObject): List<String> {
        val array = photos.resolve(state) as? JSONArray ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            array.optString(i).takeIf { it.isNotBlank() }
        }
    }

    companion object {
        fun parse(obj: JSONObject) = PhotoScreensaverWidget(
            child = parseWidget(obj.getJSONObject("child")),
            photos = Binding.of(obj.opt("photos")),
            timezone = Binding.of(obj.opt("timezone")),
            weather = Binding.of(obj.opt("weather")),
            dwellSeconds = obj.optLong("dwell_seconds", 20L).coerceAtLeast(1L),
            rotateSeconds = obj.optLong("rotate_seconds", 30L).coerceAtLeast(1L),
        )
    }
}

private const val PHOTO_FADE_MILLIS = 1_000

/** Placeholder icon: map a condition string to an emoji until real icons land. */
private fun conditionGlyph(condition: String): String = when (condition.lowercase()) {
    "clear", "sunny", "clear_night" -> "☀️"
    "partly_cloudy", "partlycloudy" -> "⛅"
    "cloudy", "overcast" -> "☁️"
    "rain", "rainy", "showers" -> "🌧️"
    "snow", "snowy" -> "❄️"
    "fog", "foggy", "mist" -> "🌫️"
    "thunderstorm", "storm" -> "⛈️"
    else -> "🌡️"
}
