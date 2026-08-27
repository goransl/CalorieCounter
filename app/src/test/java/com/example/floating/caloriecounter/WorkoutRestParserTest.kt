package com.example.floating.caloriecounter

import com.example.floating.caloriecounter.Model.parseRestSeconds
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutRestParserTest {

    @Test
    fun parsesCommonRestFormats() {
        assertEquals(90, parseRestSeconds("90"))
        assertEquals(90, parseRestSeconds("1:30"))
        assertEquals(120, parseRestSeconds("2 min"))
        assertEquals(90, parseRestSeconds("1m 30s"))
        assertEquals(90, parseRestSeconds("1,5 minutes"))
    }

    @Test
    fun blankOrUnknownRestHasNoTimerDuration() {
        assertEquals(0, parseRestSeconds(""))
        assertEquals(0, parseRestSeconds("as needed"))
    }
}
