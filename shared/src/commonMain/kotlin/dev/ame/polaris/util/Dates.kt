package dev.ame.polaris.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Small helpers shared by every page.

/** Today's date in the device's time zone. */
fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** Milliseconds since 1970, for `createdAt` fields. */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** A random unique id, safe to create offline on any device. */
@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()

/** "NOVEMBER" -> "November" */
fun String.titleCase(): String = lowercase().replaceFirstChar { it.uppercase() }

/** "12 Nov" */
fun shortDate(date: LocalDate): String = "${date.day} ${date.month.name.take(3).titleCase()}"

/** "Thursday, 12 November" */
fun longDate(date: LocalDate): String =
    "${date.dayOfWeek.name.titleCase()}, ${date.day} ${date.month.name.titleCase()}"

/** "09:05" */
fun hhmm(time: LocalTime): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
