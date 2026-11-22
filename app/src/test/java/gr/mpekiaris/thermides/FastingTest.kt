package gr.mpekiaris.thermides

import gr.mpekiaris.thermides.fasting.Fasting
import gr.mpekiaris.thermides.fasting.Fasting.HOUR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class FastingTest {
    private val utc = ZoneOffset.UTC
    private val start = 11 * 60

    private fun h(day: Int, hour: Double) = (day * 24 * HOUR + hour * HOUR).toLong()

    @Test
    fun fixedWindowBoundaries() {
        assertEquals("11:00–17:00", Fasting.windowLabel(start, 18))
        assertEquals("11:00–19:00", Fasting.windowLabel(start, 16))
        assertFalse(Fasting.inWindow(10 * 60 + 59, start, 18))
        assertTrue(Fasting.inWindow(11 * 60, start, 18))
        assertTrue(Fasting.inWindow(17 * 60, start, 18))
        assertFalse(Fasting.inWindow(17 * 60 + 1, start, 18))
    }

    @Test
    fun mealsOutsideWindowAreFlagged() {
        assertTrue(Fasting.outside(h(0, 9.0), start, 18, utc))
        assertFalse(Fasting.outside(h(0, 13.0), start, 18, utc))
        assertTrue(Fasting.outside(h(3, 21.5), start, 18, utc))
        // 16:8 -> 11:00–19:00
        assertFalse(Fasting.outside(h(0, 18.5), start, 16, utc))
    }

    @Test
    fun windowAcrossMidnight() {
        val late = 22 * 60 // 22:00–04:00
        assertTrue(Fasting.inWindow(23 * 60 + 30, late, 18))
        assertTrue(Fasting.inWindow(3 * 60, late, 18))
        assertFalse(Fasting.inWindow(5 * 60, late, 18))
    }

    @Test
    fun statusOpenDuringWindow() {
        assertEquals(Fasting.Status.Open(h(0, 17.0)), Fasting.status(emptyList(), h(0, 12.0), start, 18, utc))
    }

    @Test
    fun statusClosedCountsFromLastMeal() {
        val times = listOf(h(0, 12.0), h(0, 16.0))
        assertEquals(
            Fasting.Status.Closed(since = h(0, 16.0), opensAt = h(1, 11.0)),
            Fasting.status(times, h(0, 20.0), start, 18, utc),
        )
        // το επόμενο πρωί, πριν ανοίξει το παράθυρο
        assertEquals(
            Fasting.Status.Closed(since = h(0, 16.0), opensAt = h(1, 11.0)),
            Fasting.status(times, h(1, 9.0), start, 18, utc),
        )
    }

    @Test
    fun statusClosedWithoutMealsCountsFromWindowClose() {
        assertEquals(
            Fasting.Status.Closed(since = h(0, 17.0), opensAt = h(1, 11.0)),
            Fasting.status(emptyList(), h(1, 9.0), start, 18, utc),
        )
    }

    @Test
    fun lateMealResetsFastingClock() {
        val times = listOf(h(0, 13.0), h(0, 22.0))
        assertEquals(
            Fasting.Status.Closed(since = h(0, 22.0), opensAt = h(1, 11.0)),
            Fasting.status(times, h(1, 9.0), start, 18, utc),
        )
    }
}
