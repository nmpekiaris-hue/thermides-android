package gr.mpekiaris.thermides

import gr.mpekiaris.thermides.fasting.Fasting
import gr.mpekiaris.thermides.fasting.Fasting.HOUR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastingTest {
    private fun h(day: Int, hour: Double) = (day * 24 * HOUR + hour * HOUR).toLong()

    @Test
    fun windowOpensWithFirstMealAfterLongGap() {
        val times = listOf(h(0, 13.0), h(0, 18.5), h(1, 13.5), h(1, 17.0))
        assertEquals(
            listOf(h(0, 13.0), h(0, 13.0), h(1, 13.5), h(1, 13.5)),
            Fasting.windowStarts(times),
        )
        assertEquals(listOf(false, false, false, false), Fasting.outside(times, 18))
    }

    @Test
    fun lateMealIsOutsideWindow() {
        val times = listOf(h(0, 13.0), h(0, 19.0), h(0, 19.5), h(0, 22.0))
        // 18:6 -> το παράθυρο κλείνει 19:00· ακριβώς στις 19:00 μετράει ακόμη εντός.
        assertEquals(listOf(false, false, true, true), Fasting.outside(times, 18))
        // 16:8 -> κλείνει 21:00
        assertEquals(listOf(false, false, false, true), Fasting.outside(times, 16))
    }

    @Test
    fun shortGapDoesNotOpenNewWindow() {
        // 22:00 εκτός, 07:00 την επομένη (κενό 9 ώρες) -> ακόμη το παλιό παράθυρο
        val times = listOf(h(0, 13.0), h(0, 22.0), h(1, 7.0), h(1, 13.0))
        assertEquals(listOf(false, true, true, false), Fasting.outside(times, 18))
        // στις 13:00 της επομένης έχουν περάσει 24 ώρες από το άνοιγμα -> νέο παράθυρο
        assertEquals(h(1, 13.0), Fasting.windowStarts(times).last())
    }

    @Test
    fun statusOpenThenClosed() {
        val times = listOf(h(0, 13.0), h(0, 18.0))
        assertEquals(Fasting.Status.Open(h(0, 19.0)), Fasting.status(times, h(0, 15.0), 18))
        assertEquals(
            Fasting.Status.Closed(lastMeal = h(0, 18.0), eatAgainAt = h(1, 12.0)),
            Fasting.status(times, h(0, 21.0), 18),
        )
    }

    @Test
    fun statusIgnoresFutureEntriesAndHandlesEmpty() {
        assertTrue(Fasting.status(emptyList(), h(0, 10.0), 18) is Fasting.Status.NoData)
        assertTrue(Fasting.status(listOf(h(0, 13.0)), h(0, 10.0), 18) is Fasting.Status.NoData)
    }
}
