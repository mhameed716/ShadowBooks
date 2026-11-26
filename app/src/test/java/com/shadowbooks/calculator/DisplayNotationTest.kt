package com.shadowbooks.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayNotationTest {
    @Test fun emptyEntryHasOneValidCaretPosition() {
        // The pad opens (and AC/DEL can return) with no entry: the display must not index -1.
        val pretty = prettyExpression("")
        assertEquals("", pretty.text)
        assertEquals(listOf(0), pretty.indexMap.toList())
        assertEquals("", prettyExpression("").text.take(pretty.indexMap[0]))
    }

    @Test fun plainEntriesPrintUnchanged() {
        assertEquals("2+3×4", prettyExpression("2+3×4").text)
        assertEquals("10×sin(30)", prettyExpression("10×sin(30)").text)
        assertEquals("log(100)+ln(5)", prettyExpression("log(100)+ln(5)").text)
    }

    @Test fun rootsPrintWithRadicalSymbols() {
        assertEquals("√(9)", prettyExpression("sqrt(9)").text)
        assertEquals("∛(27)", prettyExpression("cbrt(27)").text)
        val nth = prettyExpression("root(3,27)")
        assertEquals("3√(27)", nth.text)
        assertEquals(listOf(0..0), nth.raised)
    }

    @Test fun fractionsPrintAroundASlantedBar() {
        val plain = prettyExpression("frac(1,2)")
        assertEquals("1⁄2", plain.text)
        assertEquals(listOf(0..0), plain.raised)
        assertEquals(listOf(2..2), plain.lowered)
        // A compound numerator keeps its grouping visible.
        assertEquals("(1+2)⁄3", prettyExpression("frac(1+2,3)").text)
        // The template still being typed prints the bar with both sides pending.
        assertEquals("⁄", prettyExpression("frac(,)").text)
        assertEquals("1⁄", prettyExpression("frac(1,").text)
    }

    @Test fun powersAndLogBasesPrintRaisedAndLowered() {
        val power = prettyExpression("2^(3)")
        assertEquals("23", power.text)
        assertEquals(listOf(1..1), power.raised)
        assertEquals("2(3+1)", prettyExpression("2^(3+1)").text)
        val log = prettyExpression("logb(2,8)")
        assertEquals("log2(8)", log.text)
        assertEquals(listOf(3..3), log.lowered)
        val exp = prettyExpression("exp(2)")
        assertEquals("e2", exp.text)
        assertEquals(listOf(1..1), exp.raised)
    }

    @Test fun dmsAndMixedPrintTheirKeySymbols() {
        assertEquals("1°30′15″", prettyExpression("dms(1,30,15)").text)
        assertEquals("2 1⁄2", prettyExpression("mixed(2,1,2)").text)
    }

    @Test fun caretMapStaysAlignedWithTheStoredEntry() {
        val source = "frac(12,sqrt(9))"
        val pretty = prettyExpression(source)
        assertEquals(source.length + 1, pretty.indexMap.size)
        assertEquals(0, pretty.indexMap[0])
        assertEquals(pretty.text.length, pretty.indexMap[source.length])
        assertTrue((1 until pretty.indexMap.size).all { i -> pretty.indexMap[i - 1] <= pretty.indexMap[i] })
        // The caret at the stored end lands at the printed end; inside "sqrt" it snaps to √.
        val sqrtAt = source.indexOf("sqrt")
        assertEquals(pretty.text.indexOf('√'), pretty.indexMap[sqrtAt])
    }
}
