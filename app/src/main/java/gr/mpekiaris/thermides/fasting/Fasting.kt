package gr.mpekiaris.thermides.fasting

import java.time.Instant
import java.time.ZoneId

/**
 * Διαλειμματική νηστεία με ΣΤΑΘΕΡΟ παράθυρο φαγητού κάθε μέρα (προεπιλογή 11:00–17:00 για 18:6).
 * Καθαρή λογική χωρίς Android, για να δοκιμάζεται με unit tests.
 *
 * Το παράθυρο ξεκινά στο [startMinute] (λεπτά από τα μεσάνυχτα) και κρατά 24 − ώρες νηστείας.
 * Γεύμα εκτός αυτών των ωρών είναι «εκτός παραθύρου».
 */
object Fasting {
    const val HOUR = 3_600_000L
    const val DEFAULT_START = 11 * 60

    val PLANS = listOf(16, 18, 20, 23)

    fun label(fastHours: Int) = "$fastHours:${24 - fastHours}"

    fun windowMinutes(fastHours: Int) = (24 - fastHours) * 60

    fun endMinute(startMinute: Int, fastHours: Int) = (startMinute + windowMinutes(fastHours)) % 1440

    fun clock(minuteOfDay: Int) = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    /** π.χ. «11:00–17:00». */
    fun windowLabel(startMinute: Int, fastHours: Int) =
        "${clock(startMinute)}–${clock(endMinute(startMinute, fastHours))}"

    /** Το κλείσιμο μετράει εντός: γεύμα ακριβώς στις 17:00 είναι μέσα, στις 17:01 έξω. */
    fun inWindow(minuteOfDay: Int, startMinute: Int, fastHours: Int): Boolean =
        Math.floorMod(minuteOfDay - startMinute, 1440) <= windowMinutes(fastHours)

    fun outside(time: Long, startMinute: Int, fastHours: Int, zone: ZoneId): Boolean {
        val t = Instant.ofEpochMilli(time).atZone(zone).toLocalTime()
        return !inWindow(t.hour * 60 + t.minute, startMinute, fastHours)
    }

    sealed interface Status {
        data class Open(val closesAt: Long) : Status

        /** [since]: από πότε νηστεύεις (τελευταίο γεύμα, ή το κλείσιμο του παραθύρου αν δεν έφαγες). */
        data class Closed(val since: Long, val opensAt: Long) : Status
    }

    fun status(times: List<Long>, now: Long, startMinute: Int, fastHours: Int, zone: ZoneId): Status {
        val nowZ = Instant.ofEpochMilli(now).atZone(zone)
        var open = nowZ.toLocalDate().atTime(startMinute / 60, startMinute % 60).atZone(zone)
        if (open.isAfter(nowZ)) open = open.minusDays(1)
        val close = open.plusMinutes(windowMinutes(fastHours).toLong())
        if (nowZ.isBefore(close)) return Status.Open(close.toInstant().toEpochMilli())

        val openMs = open.toInstant().toEpochMilli()
        val lastMeal = times.filter { it <= now }.maxOrNull()
        val since = if (lastMeal != null && lastMeal >= openMs) lastMeal else close.toInstant().toEpochMilli()
        return Status.Closed(since = since, opensAt = open.plusDays(1).toInstant().toEpochMilli())
    }
}
