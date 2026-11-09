package dev.ame.polaris.schedule

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ame.polaris.ui.TimeButton
import dev.ame.polaris.ui.rememberCurrentTime
import dev.ame.polaris.util.hhmm
import dev.ame.polaris.util.longDate
import dev.ame.polaris.util.shortDate
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** How tall one hour is on the timeline. The whole day is 24 × this. */
private val HourHeight = 64.dp

/** Space on the left for the "9 AM" labels. */
private val Gutter = 56.dp

/** Blocks are never drawn shorter than this, so even a 5-minute block can be tapped. */
private val MinBlockHeight = 22.dp

@Composable
fun ScheduleScreen(
    dao: ScheduleDao,
    modifier: Modifier = Modifier,
    vm: ScheduleViewModel = viewModel { ScheduleViewModel(dao) },
) {
    val state by vm.state.collectAsState()
    val now by rememberCurrentTime()
    val isWeek = state.view == ScheduleView.Week

    var dialog by remember { mutableStateOf<DialogTarget?>(null) }
    var confirmDelete by remember { mutableStateOf<ScheduleBlock?>(null) }

    // When the day, week or view changes, scroll so the useful part shows: an hour before
    // now when today is on screen, otherwise 7 AM. (Waits until there's room to scroll.)
    val scroll = rememberScrollState()
    val hourPx = with(LocalDensity.current) { HourHeight.toPx() }
    val todayShown = state.days.any { it.first == state.today }
    LaunchedEffect(state.days.first().first, state.view) {
        snapshotFlow { scroll.maxValue }.first { it > 0 }
        val hour = if (todayShown) (now.hour - 1).coerceAtLeast(0) else 7
        scroll.animateScrollTo((hour * hourPx).toInt())
    }

    Column(modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
        // Header: what's shown, and moving between days / weeks
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Schedule",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    when {
                        isWeek -> "Week · ${weekLabel(state.days.map { it.first })}"
                        state.isToday -> "Today · ${longDate(state.date)}"
                        else -> longDate(state.date)
                    },
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = vm::goToToday) { Text("Today") }
            TextButton(onClick = vm::previous) { Text("‹", style = MaterialTheme.typography.titleLarge) }
            TextButton(onClick = vm::next) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !isWeek, onClick = { vm.setView(ScheduleView.Day) }, label = { Text("Day") })
            FilterChip(selected = isWeek, onClick = { vm.setView(ScheduleView.Week) }, label = { Text("Week") })
            Spacer(Modifier.weight(1f))
            Button(onClick = { dialog = DialogTarget.New(LocalTime(9, 0), state.date, repeatOnTappedDay = false) }) {
                Text("Add block")
            }
        }
        Spacer(Modifier.height(8.dp))

        val gridModifier = if (isWeek) Modifier.fillMaxWidth() else Modifier.widthIn(max = 720.dp)
        if (isWeek) {
            WeekHeader(state.days.map { it.first }, state.today, onDayClick = vm::openDay, modifier = gridModifier)
        }
        TimelineGrid(
            columns = state.days,
            today = state.today,
            now = now,
            scroll = scroll,
            onBlockClick = { dialog = DialogTarget.Edit(it) },
            onEmptyTap = { day, time ->
                // In Week view you're usually building your routine, so a block added by
                // tapping repeats on that weekday by default (you can switch it to Once).
                dialog = DialogTarget.New(time, day, repeatOnTappedDay = isWeek)
            },
            modifier = gridModifier.weight(1f),
        )
    }

    dialog?.let { target ->
        val existing = (target as? DialogTarget.Edit)?.block
        val new = target as? DialogTarget.New
        val day = new?.day ?: state.date
        BlockDialog(
            existing = existing,
            initialStart = new?.start ?: LocalTime(9, 0),
            initialDays = if (new?.repeatOnTappedDay == true) setOf(day.dayOfWeek) else emptySet(),
            viewedDate = day,
            onDismiss = { dialog = null },
            onSave = { title, start, end, days ->
                vm.save(existing, title, start, end, days, onDate = day)
                dialog = null
            },
            onDelete = existing?.let { block ->
                {
                    dialog = null
                    // Repeating blocks ask first, since deleting removes every day's copy.
                    if (block.repeats) confirmDelete = block else vm.delete(block.id)
                }
            },
        )
    }

    confirmDelete?.let { block ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${block.title}\"?") },
            text = { Text("It repeats ${repeatLabel(block.days).lowercase()}. This removes it from every day.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(block.id); confirmDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

private sealed interface DialogTarget {
    /**
     * Adding a block on [day], starting at [start] (where you tapped, or 9:00 from the button).
     * [repeatOnTappedDay]: start with "repeats every <that weekday>" selected.
     */
    data class New(val start: LocalTime, val day: LocalDate, val repeatOnTappedDay: Boolean) : DialogTarget
    data class Edit(val block: ScheduleBlock) : DialogTarget
}

// ---------- The timeline ----------

/** Day names and dates above the week's columns. Today is gold; tap a day to open it. */
@Composable
private fun WeekHeader(days: List<LocalDate>, today: LocalDate, onDayClick: (LocalDate) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.padding(start = Gutter, bottom = 4.dp)) {
        days.forEach { day ->
            val isToday = day == today
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onDayClick(day) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    day.dayOfWeek.shortName(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) colors.primary else colors.onSurfaceVariant,
                )
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(if (isToday) colors.primary else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.day.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isToday) FontWeight.Bold else null,
                        color = if (isToday) colors.onPrimary else colors.onBackground,
                    )
                }
            }
        }
    }
}

/**
 * Midnight to midnight, drawn to scale (a block's height is its length), with one column
 * per day: one in Day view, seven in Week view. Everything is placed with offsets inside
 * one tall box that scrolls.
 */
@Composable
private fun TimelineGrid(
    columns: List<Pair<LocalDate, List<ScheduleBlock>>>,
    today: LocalDate,
    now: LocalTime,
    scroll: ScrollState,
    onBlockClick: (ScheduleBlock) -> Unit,
    onEmptyTap: (LocalDate, LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val placed = remember(columns) { columns.map { (_, blocks) -> layoutLanes(blocks) } }
    // pointerInput below is set up once per day; this makes it always call the latest onEmptyTap.
    val latestOnEmptyTap by rememberUpdatedState(onEmptyTap)

    Box(modifier.verticalScroll(scroll).padding(vertical = 10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(HourHeight * 24)) {
            val columnWidth = (maxWidth - Gutter) / columns.size
            val compact = columns.size > 1

            // Hour lines and labels
            for (hour in 0 until 24) {
                val y = HourHeight * hour
                Text(
                    hourLabel(hour),
                    modifier = Modifier.offset(y = y - 7.dp).width(Gutter - 10.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Box(Modifier.offset(x = Gutter, y = y).width(maxWidth - Gutter).height(1.dp).background(colors.surface))
            }

            columns.forEachIndexed { index, (day, _) ->
                val x = Gutter + columnWidth * index

                // Thin line between days
                if (index > 0) {
                    Box(Modifier.offset(x = x).width(1.dp).fillMaxHeight().background(colors.surface))
                }

                // Empty space: tap to add a block on this day at that time (snapped to the half hour)
                Box(
                    Modifier
                        .offset(x = x)
                        .width(columnWidth)
                        .fillMaxHeight()
                        .pointerInput(day) {
                            detectTapGestures { pos -> latestOnEmptyTap(day, timeAtOffset(pos.y, HourHeight.toPx())) }
                        }
                )

                // Blocks (drawn after the tap area, so tapping a block opens the block instead)
                val nowHere = now.takeIf { day == today }
                placed[index].forEach { p ->
                    BlockBox(
                        placed = p,
                        columnX = x,
                        columnWidth = columnWidth,
                        compact = compact,
                        isNow = nowHere != null && p.block.isHappeningAt(nowHere),
                        isPast = day < today || (nowHere != null && p.block.end <= nowHere),
                        onClick = { onBlockClick(p.block) },
                    )
                }

                // The "now" line, only across today's column
                if (nowHere != null) {
                    val y = HourHeight * (minuteOfDay(nowHere) / 60f)
                    Box(
                        Modifier.offset(x = x - 5.dp, y = y - 5.dp).size(10.dp)
                            .clip(CircleShape).background(colors.primary)
                    )
                    Box(Modifier.offset(x = x, y = y - 1.dp).width(columnWidth).height(2.dp).background(colors.primary))
                }
            }
        }
    }
}

@Composable
private fun BlockBox(
    placed: PlacedBlock,
    columnX: Dp,
    columnWidth: Dp,
    compact: Boolean,
    isNow: Boolean,
    isPast: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val block = placed.block
    val shape = RoundedCornerShape(8.dp)

    val top = HourHeight * (minuteOfDay(block.start) / 60f)
    val height = (HourHeight * (minutesBetween(block.start, block.end) / 60f)).coerceAtLeast(MinBlockHeight)
    val laneWidth = columnWidth / placed.laneCount

    Row(
        Modifier
            .offset(x = columnX + laneWidth * placed.lane + 1.dp, y = top + 1.dp)
            .width(laneWidth - 2.dp)
            .height(height - 2.dp)
            .alpha(if (isPast) 0.55f else 1f) // finished blocks fade back
            .clip(shape)
            .background(colors.surface)
            .then(if (isNow) Modifier.border(2.dp, colors.primary, shape) else Modifier)
            .clickable(onClick = onClick)
    ) {
        // Gold strip down the left edge
        Box(Modifier.width(if (compact) 3.dp else 4.dp).fillMaxHeight().background(colors.primary))
        Column(Modifier.padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 3.dp)) {
            Text(
                block.title,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Only show details when the block is tall enough to fit them.
            // Week view's narrow columns just get the start time.
            if (height >= 44.dp) {
                Text(
                    if (compact) hhmm(block.start) else buildString {
                        append("${hhmm(block.start)}–${hhmm(block.end)}")
                        if (block.repeats) append(" · ${repeatLabel(block.days)}")
                        if (isNow) append(" · Now")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isNow) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------- Add / edit dialog ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockDialog(
    existing: ScheduleBlock?,
    initialStart: LocalTime,
    initialDays: Set<DayOfWeek>,
    viewedDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (title: String, start: LocalTime, end: LocalTime, days: Set<DayOfWeek>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var start by remember { mutableStateOf(existing?.start ?: initialStart) }
    var end by remember { mutableStateOf(existing?.end ?: initialStart.oneHourLater()) }
    var days by remember { mutableStateOf(existing?.let { daysFromBits(it.days) } ?: initialDays) }
    val valid = title.isNotBlank() && end > start

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New block" else "Edit block") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("e.g. Gym, Deep work, Class") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeButton("Start", start, onPick = {
                        start = it
                        if (end <= it) end = it.oneHourLater()
                    })
                    TimeButton("End", end, onPick = { end = it })
                }

                Text("Repeat", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = days.isEmpty(), onClick = { days = emptySet() }, label = { Text("Once") })
                    FilterChip(selected = days == AllDays, onClick = { days = AllDays }, label = { Text("Every day") })
                    FilterChip(selected = days == Weekdays, onClick = { days = Weekdays }, label = { Text("Weekdays") })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(day.shortName().take(2)) },
                        )
                    }
                }

                val hint = when {
                    end <= start -> "End must be after start."
                    days.isEmpty() -> "Only on ${shortDate(existing?.date ?: viewedDate)}."
                    else -> "Repeats ${repeatLabel(days.toBits()).lowercase()}."
                }
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (end <= start) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, start, end, days) }, enabled = valid) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
