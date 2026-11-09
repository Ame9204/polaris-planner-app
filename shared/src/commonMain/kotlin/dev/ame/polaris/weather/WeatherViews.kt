package dev.ame.polaris.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ame.polaris.util.hhmm
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import dev.ame.polaris.ui.GoldButton

/** The weather shown in the Overview header. Tap it to change the place or unit. */
@Composable
fun WeatherBadge(state: WeatherUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val place = state.place
    if (place == null) {
        TextButton(onClick = onClick, modifier = modifier) { Text("☀ Set weather location") }
        return
    }

    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        val weather = state.weather
        if (weather == null) {
            Text(
                if (state.refreshing) "Loading weather…" else "Weather unavailable",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            val c = condition(weather.code, weather.isDay)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.symbol, style = MaterialTheme.typography.headlineSmall, color = colors.primary)
                Text(
                    " ${degrees(weather.temperature)}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onBackground,
                )
            }
            val highLow = if (weather.high != null && weather.low != null) {
                " · H ${degrees(weather.high)} L ${degrees(weather.low)}"
            } else ""
            Text(c.description + highLow, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Text(
            buildString {
                append(place.name)
                // Offline: say how old the weather is, so it isn't mistaken for live.
                if (state.offline && weather != null) append(" · offline, from ${timeOf(weather.fetchedAt)}")
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

private fun timeOf(millis: Long): String =
    hhmm(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).time)

/** Pick a city and °C/°F. */
@Composable
fun WeatherSettingsDialog(
    state: WeatherUiState,
    onSearch: (String) -> Unit,
    onChoose: (Place) -> Unit,
    onUnit: (TempUnit) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weather") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    state.place?.let { "Showing: ${it.label}" } ?: "Search for your city.",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("City") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                        modifier = Modifier.weight(1f),
                    )
                    GoldButton(onClick = { onSearch(query) }, enabled = query.trim().length >= 2 && !state.searching) {
                        Text(if (state.searching) "…" else "Search")
                    }
                }

                state.searchMessage?.let { Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }

                if (state.results.isNotEmpty()) {
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(state.results) { place ->
                            Text(
                                place.label,
                                color = colors.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onChoose(place) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TempUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = state.unit == unit,
                            onClick = { onUnit(unit) },
                            label = { Text(unit.symbol) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
