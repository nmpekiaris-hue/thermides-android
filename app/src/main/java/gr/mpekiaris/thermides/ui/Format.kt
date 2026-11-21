package gr.mpekiaris.thermides.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GREEK = Locale("el", "GR")
private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_LONG = DateTimeFormatter.ofPattern("EEEE d MMMM", GREEK)
private val DAY_SHORT = DateTimeFormatter.ofPattern("EEE d/M", GREEK)
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", GREEK)
private val STAMP = DateTimeFormatter.ofPattern("EEE d/M HH:mm", GREEK)

fun hm(millis: Long): String = HM.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

fun hm(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

/** «HH:mm», με «αύριο»/«χθες» όταν η ώρα δεν πέφτει σήμερα. */
fun hmRelative(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val prefix = when (date) {
        today -> ""
        today.plusDays(1) -> "αύριο "
        today.minusDays(1) -> "χθες "
        else -> DAY_SHORT.format(date) + " "
    }
    return prefix + hm(millis)
}

fun duration(millis: Long): String {
    val minutes = (millis / 60_000).coerceAtLeast(0)
    return "${minutes / 60}ω ${minutes % 60}λ"
}

fun dayTitle(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Σήμερα"
    LocalDate.now().minusDays(1) -> "Χθες"
    else -> DAY_LONG.format(date).replaceFirstChar { it.titlecase(GREEK) }
}

fun dayShort(date: LocalDate): String = DAY_SHORT.format(date)

fun weekday(date: LocalDate): String = WEEKDAY.format(date)

fun stamp(instant: Instant): String = STAMP.format(instant.atZone(ZoneId.systemDefault()))

fun kcal(value: Int): String = "%,d".format(GREEK, value)
