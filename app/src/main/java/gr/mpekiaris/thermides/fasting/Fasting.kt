package gr.mpekiaris.thermides.fasting

/**
 * Διαλειμματική νηστεία. Καθαρή λογική χωρίς Android, για να δοκιμάζεται με unit tests.
 *
 * Κανόνες:
 * - Το παράθυρο φαγητού ανοίγει με το πρώτο γεύμα μετά από κενό 10+ ωρών.
 * - Δίχτυ ασφαλείας: αν έχουν περάσει 24+ ώρες από το άνοιγμα του παραθύρου, το επόμενο
 *   γεύμα ανοίγει νέο (αλλιώς μια μέρα με συνεχές τσιμπολόγημα θα «κλείδωνε» το παλιό για πάντα).
 * - Γεύμα που απέχει από το άνοιγμα περισσότερο από τη διάρκεια του παραθύρου είναι «εκτός παραθύρου».
 */
object Fasting {
    const val HOUR = 3_600_000L
    const val GAP = 10 * HOUR

    val PLANS = listOf(16, 18, 20, 23)

    fun label(fastHours: Int) = "$fastHours:${24 - fastHours}"

    /** Για κάθε γεύμα (ώρες σε αύξουσα σειρά) η ώρα που άνοιξε το παράθυρό του. */
    fun windowStarts(times: List<Long>): List<Long> {
        val out = ArrayList<Long>(times.size)
        var start = 0L
        var prev = 0L
        times.forEachIndexed { i, t ->
            if (i == 0 || t - prev >= GAP || t - start >= 24 * HOUR) start = t
            out += start
            prev = t
        }
        return out
    }

    /** true για κάθε γεύμα που έπεσε μετά το κλείσιμο του παραθύρου του. */
    fun outside(times: List<Long>, fastHours: Int): List<Boolean> {
        val window = (24 - fastHours) * HOUR
        val starts = windowStarts(times)
        return times.indices.map { times[it] - starts[it] > window }
    }

    sealed interface Status {
        /** Κανένα γεύμα στο ιστορικό. */
        data object NoData : Status

        data class Open(val closesAt: Long) : Status

        data class Closed(val lastMeal: Long, val eatAgainAt: Long) : Status
    }

    fun status(times: List<Long>, now: Long, fastHours: Int): Status {
        val past = times.filter { it <= now }
        if (past.isEmpty()) return Status.NoData
        val closesAt = windowStarts(past).last() + (24 - fastHours) * HOUR
        if (now < closesAt) return Status.Open(closesAt)
        val last = past.last()
        return Status.Closed(lastMeal = last, eatAgainAt = last + fastHours * HOUR)
    }
}
