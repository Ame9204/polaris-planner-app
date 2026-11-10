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
import androidx.compose.material3.Icon
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
import dev.ame.polaris.ui.DateArrows
import dev.ame.polaris.ui.LocalUndo
import dev.ame.polaris.ui.PageHeader
import dev.ame.polaris.ui.PagePadding
import dev.ame.polaris.ui.PolarisIcons
import dev.ame.polaris.ui.TimeButton
import dev.ame.polaris.ui.rememberCurrentTime
import dev.ame.polaris.util.hhmm
import dev.ame.polaris.util.longDate
import dev.ame.polaris.util.WeekOrder
import dev.ame.polaris.util.shortDate
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import dev.ame.polaris.ui.CardAlpha
import dev.ame.polaris.ui.Gold
import dev.ame.polaris.ui.GoldButton
import dev.ame.polaris.calendar.CalendarEvent
import dev.ame.polaris.calendar.EventDao
import dev.ame.polaris.calendar.EventDialog
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke

/** How tall one hour is on the timeline. The whole day is 24 × this. */
private val HourHeight = 64.dp

/** Space on the left for the "9 AM" labels. */
private val Gutter = 56.dp

/** Blocks are never drawn shorter than this, so even a 5-minute block can be tapped. */
private val MinBlockHeight = 22.dp

@Composable
fun ScheduleScreen(
    dao: ScheduleDao,
    eventDao: EventDao,
    modifier: Modifier = Modifier,
    vm: ScheduleViewModel = viewModel { ScheduleViewModel(dao, eventDao) },
) {
    val state by vm.state.collectAsState()
    val now by rememberCurrentTime()
    val isWeek = state.view == ScheduleView.Week

    var dialog by remember { mutableStateOf<DialogTarget?>(null) }
    var seriesChange by remember { mutableStateOf<SeriesChange?>(null) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    val undo = LocalUndo.current

    // When the day, week or view changes, scroll so the useful part shows: an hour before
    // now when today is on screen, otherwise 7 AM. (Waits until there's room to scroll.)
    val scroll = rememberScrollState()
    val hourPx = with(LocalDensity.current) { HourHeight.toPx() }
    val todayShown = state.days.any { it.date == state.today }
    LaunchedEffect(state.days.first().date, state.view) {
        snapshotFlow { scroll.maxValue }.first { it > 0 }
        val hour = if (todayShown) (now.hour - 1).coerceAtLeast(0) else 7
        scroll.animateScrollTo((hour * hourPx).toInt())
    }

    Column(modifier.fillMaxSize().padding(start = PagePadding, end = PagePadding, top = PagePadding)) {
        // Header: what's shown, and moving between days / weeks
        PageHeader(
            "Schedule",
            subtitle = when {
                isWeek -> "Week · ${weekLabel(state.days.map { it.date })}"
                state.isToday -> "Today · ${longDate(state.date)}"
                else -> longDate(state.date)
            },
        ) {
            DateArrows(onToday = vm::goToToday, onPrevious = vm::previous, onNext = vm::next)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !isWeek, onClick = { vm.setView(ScheduleView.Day) }, label = { Text("Day") })
            FilterChip(selected = isWeek, onClick = { vm.setView(ScheduleView.Week) }, label = { Text("Week") })
            Spacer(Modifier.weight(1f))
            GoldButton(onClick = { dialog = DialogTarget.New(LocalTime(9, 0), state.date, repeatOnTappedDay = false) }) {
                Icon(PolarisIcons.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Add block")
            }
        }
        Spacer(Modifier.height(8.dp))

        val gridModifier = if (isWeek) Modifier.fillMaxWidth() else Modifier.widthIn(max = 720.dp)
        if (isWeek) {
            WeekHeader(state.days.map { it.date }, state.today, onDayClick = vm::openDay, modifier = gridModifier)
        }
        // All-day events don't have a place on the timeline, so they get a strip above it
        if (state.days.any { it.allDayEvents.isNotEmpty() }) {
            AllDayStrip(state.days, onEventClick = { editingEvent = it }, modifier = gridModifier)
        }
        TimelineGrid(
            columns = state.days,
            today = state.today,
            now = now,
            scroll = scroll,
            onBlockClick = { block, day -> dialog = DialogTarget.Edit(block, day) },
            onEventClick = { editingEvent = it },
            onEmptyTap = { day, time ->
                // In Week view you're usually building your routine, so a block added by
                // tapping repeats on that weekday by default (you can switch it to Once).
                dialog = DialogTarget.New(time, day, repeatOnTappedDay = isWeek)
            },
            modifier = gridModifier.weight(1f),
        )
    }

    dialog?.let { target ->
        val edit = target as? DialogTarget.Edit
        val existing = edit?.block
        val new = target as? DialogTarget.New
        val day = new?.day ?: edit?.day ?: state.date
        BlockDialog(
            existing = existing,
            initialStart = new?.start ?: LocalTime(9, 0),
            initialDays = if (new?.repeatOnTappedDay == true) setOf(day.dayOfWeek) else emptySet(),
            viewedDate = day,
            onDismiss = { dialog = null },
            onSave = { title, start, end, days, remind ->
                dialog = null
                if (existing != null && existing.repeats) {
                    // A repeating block: ask whether this is for this day, the rest, or all days
                    seriesChange = SeriesChange.Save(existing, day, BlockFields(title, start, end, days.toBits(), remind))
                } else {
                    vm.save(existing, title, start, end, days, onDate = day, remindMinutesBefore = remind)
                }
            },
            onDelete = existing?.let { block ->
                {
                    dialog = null
                    if (block.repeats) {
                        seriesChange = SeriesChange.Delete(block, day)
                    } else {
                        vm.delete(block.id)
                        undo.offer("Block deleted") { vm.restore(block) }
                    }
                }
            },
        )
    }

    seriesChange?.let { change ->
        SeriesScopeDialog(
            deleting = change is SeriesChange.Delete,
            day = change.day,
            onDismiss = { seriesChange = null },
            onPick = { scope ->
                seriesChange = null
                when (change) {
                    is SeriesChange.Save -> vm.saveSeries(change.block, change.day, scope, change.fields)
                    is SeriesChange.Delete -> {
                        vm.deleteSeries(change.block, change.day, scope)
                        val what = when (scope) {
                            SeriesScope.ThisDay -> "for ${shortDate(change.day)}"
                            SeriesScope.Following -> "from ${shortDate(change.day)} on"
                            SeriesScope.All -> "on all days"
                        }
                        // Undo puts the block back exactly as it was (series dates and all)
                        undo.offer("Deleted \"${change.block.title}\" $what") { vm.restore(change.block) }
                    }
                }
            },
        )
    }

    editingEvent?.let { event ->
        EditEventDialog(event, vm, onDone = { editingEvent = null })
    }
}

@Composable
private fun EditEventDialog(
    event: CalendarEvent,
    vm: ScheduleViewModel,
    onDone: () -> Unit,
) {
    val undo = LocalUndo.current
    EventDialog(
        existing = event,
        date = event.date,
        onDismiss = onDone,
        onSave = { title, date, start, end, endDate ->
            vm.saveEvent(event, title, date, start, end, endDate)
            onDone()
        },
        onDelete = {
            onDone()
            vm.deleteEvent(event.id)
            undo.offer("Event deleted") { vm.restoreEvent(event) }
        },
    )
}

private sealed interface DialogTarget {
    /**
     * Adding a block on [day], starting at [start] (where you tapped, or 9:00 from the button).
     * [repeatOnTappedDay]: start with "repeats every <that weekday>" selected.
     */
    data class New(val start: LocalTime, val day: LocalDate, val repeatOnTappedDay: Boolean) : DialogTarget
    /** Editing [block], opened from [day] (matters for "this day only" on repeating blocks). */
    data class Edit(val block: ScheduleBlock, val day: LocalDate) : DialogTarget
}

/** A change to a repeating block, waiting for "this day / following / all days". */
private sealed interface SeriesChange {
    val block: ScheduleBlock
    val day: LocalDate

    data class Save(override val block: ScheduleBlock, override val day: LocalDate, val fields: BlockFields) : SeriesChange
    data class Delete(override val block: ScheduleBlock, override val day: LocalDate) : SeriesChange
}

/** "Change / delete repeating block: This day only · This and following days · All days". */
@Composable
private fun SeriesScopeDialog(
    deleting: Boolean,
    day: LocalDate,
    onDismiss: () -> Unit,
    onPick: (SeriesScope) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (deleting) "Delete repeating block" else "Change repeating block") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This block repeats. Which days should this apply to?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                ScopeOption("This day only", shortDate(day), onClick = { onPick(SeriesScope.ThisDay) })
                ScopeOption("This and following days", "from ${shortDate(day)} on", onClick = { onPick(SeriesScope.Following) })
                // Deleting every day is the big one, so it's red
                ScopeOption("All days", "the whole series", onClick = { onPick(SeriesScope.All) }, danger = deleting)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One choice in the "which days?" dialog, drawn as an outlined button with an arrow. */
@Composable
private fun ScopeOption(label: String, detail: String, onClick: () -> Unit, danger: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val accent = if (danger) colors.error else colors.onSurface
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = 0.06f))
            .border(1.dp, accent.copy(alpha = if (danger) 0.7f else 0.28f), shape)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = accent)
            Text(detail.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (danger) accent else colors.onSurfaceVariant)
        }
        Icon(PolarisIcons.ChevronRight, contentDescription = null, tint = accent)
    }
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
                    Modifier.size(28.dp).clip(CircleShape).then(if (isToday) Modifier.background(Gold.Shine) else Modifier),
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

/** Each day's all-day events, in line with the timeline's columns. Tap one to edit it. */
@Composable
private fun AllDayStrip(days: List<ScheduleDay>, onEventClick: (CalendarEvent) -> Unit, modifier: Modifier) {
    val compact = days.size > 1
    Row(modifier.padding(start = Gutter, bottom = 6.dp)) {
        days.forEach { day ->
            Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                day.allDayEvents.take(2).forEach { event ->
                    Text(
                        event.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .dashedGoldOutline(RoundedCornerShape(6.dp))
                            .clickable { onEventClick(event) }
                            .padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 3.dp),
                    )
                }
                val more = day.allDayEvents.size - 2
                if (more > 0) {
                    Text("+$more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    columns: List<ScheduleDay>,
    today: LocalDate,
    now: LocalTime,
    scroll: ScrollState,
    onBlockClick: (ScheduleBlock, LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onEmptyTap: (LocalDate, LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Blocks and timed events share one layout, so overlapping ones sit side by side
    val placed = remember(columns) { columns.map { layoutEntries(it.entries) } }
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

            columns.forEachIndexed { index, column ->
                val day = column.date
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

                // Blocks and events (drawn after the tap area, so tapping one opens it instead)
                val nowHere = now.takeIf { day == today }
                placed[index].forEach { p ->
                    val entry = p.item
                    val isNow = nowHere != null && nowHere >= entry.start && nowHere < entry.end
                    val isPast = day < today || (nowHere != null && entry.end <= nowHere)
                    when (entry) {
                        is TimelineEntry.Block -> BlockBox(
                            block = entry.block,
                            lane = p.lane,
                            laneCount = p.laneCount,
                            columnX = x,
                            columnWidth = columnWidth,
                            compact = compact,
                            isNow = isNow,
                            isPast = isPast,
                            onClick = { onBlockClick(entry.block, day) },
                        )
                        is TimelineEntry.Event -> EventBox(
                            entry = entry,
                            lane = p.lane,
                            laneCount = p.laneCount,
                            columnX = x,
                            columnWidth = columnWidth,
                            compact = compact,
                            isPast = isPast,
                            onClick = { onEventClick(entry.event) },
                        )
                    }
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
    block: ScheduleBlock,
    lane: Int,
    laneCount: Int,
    columnX: Dp,
    columnWidth: Dp,
    compact: Boolean,
    isNow: Boolean,
    isPast: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)

    val top = HourHeight * (minuteOfDay(block.start) / 60f)
    val height = (HourHeight * (minutesBetween(block.start, block.end) / 60f)).coerceAtLeast(MinBlockHeight)
    val laneWidth = columnWidth / laneCount

    Row(
        Modifier
            .offset(x = columnX + laneWidth * lane + 1.dp, y = top + 1.dp)
            .width(laneWidth - 2.dp)
            .height(height - 2.dp)
            .alpha(if (isPast) 0.55f else 1f) // finished blocks fade back
            .clip(shape)
            .background(colors.surface.copy(alpha = CardAlpha))
            .border(if (isNow) 2.dp else 1.dp, Gold.Edge, shape)
            .clickable(onClick = onClick)
    ) {
        // Gold strip down the left edge
        Box(Modifier.width(if (compact) 3.dp else 4.dp).fillMaxHeight().background(Gold.Shine))
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

/**
 * A calendar event on the timeline: no fill, just a dashed gold outline, so it reads as
 * "something happening" rather than one of your own blocks.
 */
@Composable
private fun EventBox(
    entry: TimelineEntry.Event,
    lane: Int,
    laneCount: Int,
    columnX: Dp,
    columnWidth: Dp,
    compact: Boolean,
    isPast: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val top = HourHeight * (minuteOfDay(entry.start) / 60f)
    val height = (HourHeight * (minutesBetween(entry.start, entry.end) / 60f)).coerceAtLeast(MinBlockHeight)
    val laneWidth = columnWidth / laneCount

    Column(
        Modifier
            .offset(x = columnX + laneWidth * lane + 1.dp, y = top + 1.dp)
            .width(laneWidth - 2.dp)
            .height(height - 2.dp)
            .alpha(if (isPast) 0.55f else 1f)
            .clip(shape)
            .dashedGoldOutline(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!compact) {
                Icon(PolarisIcons.Calendar, contentDescription = "Event", tint = colors.primary, modifier = Modifier.size(14.dp).padding(end = 4.dp))
            }
            Text(
                entry.event.title,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (height >= 44.dp) {
            Text(
                if (compact) hhmm(entry.start) else "${hhmm(entry.start)}–${hhmm(entry.end)} · Event",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A dashed gold outline (Modifier.border can't do dashes, so it's drawn by hand). */
@Composable
private fun Modifier.dashedGoldOutline(shape: RoundedCornerShape): Modifier {
    // Read the colour here; drawBehind runs while drawing, outside composition.
    val color = MaterialTheme.colorScheme.primary
    return drawBehind {
    val stroke = 1.5.dp.toPx()
    val radius = shape.topStart.toPx(size, this)
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
    }
}

// ---------- Add / edit dialog ----------

/** Reminder options for a block: minutes before it starts (null = off). */
private val ReminderChoices = listOf(
    null to "Off",
    0 to "At start",
    5 to "5 min before",
    10 to "10 min",
    15 to "15 min",
    30 to "30 min",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockDialog(
    existing: ScheduleBlock?,
    initialStart: LocalTime,
    initialDays: Set<DayOfWeek>,
    viewedDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (title: String, start: LocalTime, end: LocalTime, days: Set<DayOfWeek>, remindMinutesBefore: Int?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var start by remember { mutableStateOf(existing?.start ?: initialStart) }
    var end by remember { mutableStateOf(existing?.end ?: initialStart.oneHourLater()) }
    var days by remember { mutableStateOf(existing?.let { daysFromBits(it.days) } ?: initialDays) }
    var remind by remember { mutableStateOf(if (existing != null) existing.remindMinutesBefore else 0) }
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
                    WeekOrder.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(day.shortName().take(2)) },
                        )
                    }
                }

                Text("Reminder", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReminderChoices.forEach { (minutes, label) ->
                        FilterChip(selected = remind == minutes, onClick = { remind = minutes }, label = { Text(label) })
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
            TextButton(onClick = { onSave(title, start, end, days, remind) }, enabled = valid) { Text("Save") }
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
