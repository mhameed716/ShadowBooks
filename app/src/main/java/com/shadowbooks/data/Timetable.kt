package com.shadowbooks.data

import java.util.Calendar

/** Yemen's school week, in the same Saturday-to-Thursday order used by the timetable. */
object SchoolWeek {
    val days = listOf("السبت", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس")

    fun indexForCalendarDay(dayOfWeek: Int): Int? = when (dayOfWeek) {
        Calendar.SATURDAY -> 0
        Calendar.SUNDAY -> 1
        Calendar.MONDAY -> 2
        Calendar.TUESDAY -> 3
        Calendar.WEDNESDAY -> 4
        Calendar.THURSDAY -> 5
        else -> null // Friday
    }
}

/** Bell times are local wall-clock minutes. The first bell, lesson length, and passing time are editable. */
data class BellSchedule(
    val firstStartMinute: Int = 7 * 60,
    val lessonMinutes: Int = 45,
    val passingMinutes: Int = 5,
) {
    init {
        require(firstStartMinute in 0..1439)
        require(lessonMinutes in 1..180)
        require(passingMinutes in 0..120)
        require(firstStartMinute + (PERIOD_COUNT - 1) * (lessonMinutes + passingMinutes) + lessonMinutes < 1440)
    }

    fun window(period: Int): LessonWindow {
        require(period in 0 until PERIOD_COUNT)
        val start = firstStartMinute + period * (lessonMinutes + passingMinutes)
        val end = start + lessonMinutes
        val next = if (period < PERIOD_COUNT - 1) end + passingMinutes else null
        return LessonWindow(period, start, end, next)
    }

    /** The index of the lesson currently in progress, or null during breaks/outside school. */
    fun currentPeriod(minuteOfDay: Int): Int? {
        require(minuteOfDay in 0..1439)
        return (0 until PERIOD_COUNT).firstOrNull { period ->
            val lesson = window(period)
            minuteOfDay >= lesson.startMinute && minuteOfDay < lesson.endMinute
        }
    }

    /** Current lesson, next lesson during a break, first lesson before school, last after school. */
    fun relevantPeriod(minuteOfDay: Int): Int {
        require(minuteOfDay in 0..1439)
        return (0 until PERIOD_COUNT).firstOrNull { minuteOfDay < window(it).endMinute } ?: PERIOD_COUNT - 1
    }

    companion object {
        const val PERIOD_COUNT = 6

        fun parseClock(value: String): Int? {
            val pieces = value.trim().split(':')
            if (pieces.size != 2) return null
            val hour = pieces[0].toIntOrNull() ?: return null
            val minute = pieces[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour * 60 + minute
        }

        fun formatClock(minuteOfDay: Int): String {
            val normalized = ((minuteOfDay % 1440) + 1440) % 1440
            val hour = normalized / 60
            val minute = normalized % 60
            return "%02d:%02d".format(java.util.Locale.US, hour, minute)
        }
    }
}

data class LessonWindow(
    val period: Int,
    val startMinute: Int,
    val endMinute: Int,
    val nextStartMinute: Int?,
)
