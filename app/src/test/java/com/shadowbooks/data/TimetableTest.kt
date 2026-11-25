package com.shadowbooks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TimetableTest {
    @Test fun schoolWeekHasSixDaysFromSaturdayThroughThursday() {
        assertEquals(listOf("السبت", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس"), SchoolWeek.days)
        assertEquals(0, SchoolWeek.indexForCalendarDay(java.util.Calendar.SATURDAY))
        assertEquals(1, SchoolWeek.indexForCalendarDay(java.util.Calendar.SUNDAY))
        assertEquals(5, SchoolWeek.indexForCalendarDay(java.util.Calendar.THURSDAY))
        assertNull(SchoolWeek.indexForCalendarDay(java.util.Calendar.FRIDAY))
    }

    @Test fun sixLessonWindowsIncludeBreakAndNextStart() {
        val day = BellSchedule(firstStartMinute = 7 * 60, lessonMinutes = 45, passingMinutes = 5)
        assertEquals(LessonWindow(0, 420, 465, 470), day.window(0))
        assertEquals(LessonWindow(4, 620, 665, 670), day.window(4))
        assertEquals(LessonWindow(5, 670, 715, null), day.window(5))
    }

    @Test fun lessonBoundariesAndPassingTimeAreUnambiguous() {
        val day = BellSchedule()
        assertNull(day.currentPeriod(419))
        assertEquals(0, day.currentPeriod(420))
        assertEquals(0, day.currentPeriod(464))
        assertNull(day.currentPeriod(465))
        assertNull(day.currentPeriod(469))
        assertEquals(1, day.currentPeriod(470))
        assertNull(day.currentPeriod(715))
    }

    @Test fun detailsPointToCurrentOrNextLesson() {
        val day = BellSchedule()
        assertEquals(0, day.relevantPeriod(360))
        assertEquals(0, day.relevantPeriod(420))
        assertEquals(1, day.relevantPeriod(465))
        assertEquals(5, day.relevantPeriod(900))
    }

    @Test fun schedulesCannotRollTheSixthLessonIntoTomorrow() {
        assertThrows(IllegalArgumentException::class.java) { BellSchedule(23 * 60, 45, 5) }
    }

    @Test fun clockParsingAndFormattingRejectBadInput() {
        assertEquals(7 * 60 + 5, BellSchedule.parseClock("07:05"))
        assertEquals(1439, BellSchedule.parseClock("23:59"))
        assertNull(BellSchedule.parseClock("24:00"))
        assertNull(BellSchedule.parseClock("07:60"))
        assertNull(BellSchedule.parseClock("7"))
        assertEquals("07:05", BellSchedule.formatClock(425))
        assertEquals("00:05", BellSchedule.formatClock(1445))
    }
}
