package com.shadowbooks.calculator

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.E

/** The fixture transcribes the first visible panel, not the parser's function list. Blank slots matter. */
class BusinessKeypadTest {
    private val pad = (scienceKeys + numberKeys).flatten().associateBy { it.base.text }
    private fun CalculatorState.tap(vararg keys: String) = keys.fold(this) { s, key -> s.press(pad.getValue(key)) }
    private val fresh = CalculatorState()

    @Test fun everyCapAndBothPrintedLegendsAreOnTheReferenceKey() {
        val expected = listOf(
            "SHIFT ALPHA ⇦ ⇨ MODE 2nd",
            "CALC|SOLVE|= ∫dx|d/dx|: ▲ ▼ x⁻¹|x! Logₐx|∑|∏",
            "□/□|mod|÷R √□|³√□ x²|x³ x^□|□√□ Log|10ˣ Ln|e^□",
            "(−)|∠|a °′″|FACT|b hyp|Abs|c Sin|Sin⁻¹|d Cos|Cos⁻¹|e Tan|Tan⁻¹|f",
            "RCL|STO|CLRv ENG|i|Cot (|%|Cot⁻¹ )|,|x S⇄D|aᵇ/c|y M+|M−|m",
            "7|CONST 8|CONV 9||= DEL AC",
            "4|MATRIX 5|VECTOR 6|HELP ×|nPr|GCD ÷|nCr|LCM",
            "1|STAT 2|CMPLX 3|DISTR +|Pol|Ceil −|Rec|Floor",
            "0|COPY|PASTE .|Ran#|RanInt Exp|π|e Ans||PreAns =|History",
        )
        val printed = (scienceKeys + numberKeys).map { row -> row.joinToString(" ") { key ->
            listOf(key.base.text,key.shift?.text.orEmpty(),key.alpha?.text.orEmpty()).joinToString("|").trimEnd('|')
        } }
        assertEquals(expected,printed); assertEquals(50,pad.size)
        assertEquals(22,scienceKeys.flatten().count { it.shift != null }); assertEquals(16,scienceKeys.flatten().count { it.alpha != null })
        for (key in pad.values) {
            assertEquals(key.shift ?: key.base, key.mark(Layer.Shift)); assertEquals(key.alpha ?: key.base, key.mark(Layer.Alpha))
        }
    }

    @Test fun scientificShortcutsUseThePrintedLocationsNotOldConvenienceAliases() {
        val sum = fresh.tap("SHIFT","Logₐx","ALPHA",")","SHIFT",")","1","SHIFT",")","4","=")
        assertEquals("sum(X,1,4)",sum.expression); assertEquals(10.0,sum.answer,0.0)
        val product = fresh.tap("ALPHA","Logₐx","ALPHA",")","SHIFT",")","1","SHIFT",")","4","=")
        assertEquals("prod(X,1,4)",product.expression); assertEquals(24.0,product.answer,0.0)
        assertEquals(5.0,fresh.tap("SHIFT","hyp","(−)","5","=").answer,0.0)
        assertEquals(1.0,fresh.tap("ALPHA","ENG","4","5","=").answer,1e-12)
        assertEquals(135.0,fresh.tap("ALPHA","(","(−)","1","=").answer,1e-12)
        assertEquals(MathIssue.DivisionByZero,fresh.tap("ALPHA","ENG","0","=").error)
        assertEquals(30.0,fresh.tap("2","0","0","×","1","5","SHIFT","(","=").answer,0.0)
        val r = fresh.tap("7","□/□","3","=","SHIFT","S⇄D"); assertEquals("2 1/3",formatNumber(r.answer,r.format))
        assertEquals("7/3",r.tap("SHIFT","S⇄D").let { formatNumber(it.answer,it.format) })
        assertEquals("−2 1/3",formatNumber(-7.0/3,NumberFormat.Mixed))
        assertEquals(MathIssue.Limit,assertThrows(CalculationError::class.java) { calculate("prod(X,1,10002)") }.issue)
    }

    @Test fun templatesAcceptTheNumeratorDegreeOrIndexFirst() {
        val fraction = fresh.tap("□/□","1","+","2","⇨","2","+","3","=")
        assertEquals("frac(1+2,2+3)",fraction.expression); assertNull(fraction.error); assertEquals(.6,fraction.answer,0.0)
        assertEquals(MathIssue.Syntax,fresh.tap("□/□","2","=").error)
        val dms = fresh.tap("°′″","(−)","1","⇨","3","0","⇨","0","=","°′″")
        assertEquals(-1.5,dms.answer,0.0); assertEquals("−1° 30′ 0″",formatNumber(dms.answer,dms.format))
        assertEquals(NumberFormat.Decimal,dms.tap("°′″").format)
        val examples = mapOf(
            "√□ 9 =" to 3.0, "SHIFT √□ 8 =" to 2.0, "2 x^□ 4 =" to 16.0,
            "SHIFT x^□ 3 SHIFT ) 2 7 =" to 3.0, "4 x⁻¹ =" to .25, "4 SHIFT x⁻¹ =" to 24.0,
            "2 SHIFT x² =" to 8.0, "Log 1 0 0 =" to 2.0, "Ln ALPHA Exp =" to 1.0,
            "SHIFT Log 2 =" to 100.0, "SHIFT Ln 1 =" to E, "SHIFT ∫dx ALPHA ) x² SHIFT ) 3 =" to 6.0,
            "SHIFT Sin . 5 =" to 30.0, "SHIFT Cos . 5 =" to 60.0, "SHIFT Tan 1 =" to 45.0,
        )
        examples.forEach { (sequence,n) -> val s = fresh.tap(*sequence.split(' ').toTypedArray())
            assertNull(sequence,s.error); assertEquals(sequence,n,s.answer,1e-9)
        }
    }

    @Test fun eachRedVariableIsItsOwnRegisterAndMinusMemorySubtracts() {
        val values = registerNames.indices.map { 10.0 + it }; val s = fresh.copy(registers = values)
        val labels = listOf("(−)","°′″","hyp","Sin","Cos","Tan",")","S⇄D","M+")
        labels.forEachIndexed { i, key -> val value = s.tap("ALPHA",key,"=")
            assertEquals(registerNames[i],value.expression); assertEquals(values[i],value.answer,0.0)
        }
        val m = fresh.tap("7","M+","AC","2","SHIFT","M+","AC","ALPHA","M+","=")
        assertEquals(5.0,m.answer,0.0)
    }

    @Test fun divisionWithRemainderAndModuloAreDistinctFromPercentAndFraction() {
        val q = fresh.tap("1","7","ALPHA","□/□","5","=")
        assertTrue(q.complete); assertEquals(3.0,q.answer,0.0); assertEquals(2L,q.remainder)
        assertEquals(6.0,q.tap("×","2","=").answer,0.0); assertNull(q.tap("AC").remainder)
        assertEquals(3.0,fresh.tap("(−)","1","7","SHIFT","□/□","5","=").answer,0.0)
        assertEquals(2.0,fresh.tap("SHIFT","□/□","1","7","SHIFT",")","5","=").answer,0.0)
        assertEquals(MathIssue.DivisionByZero,fresh.tap("1","ALPHA","□/□","0","=").error)
        assertEquals(MathIssue.Domain,fresh.tap("1",".","2","ALPHA","□/□","2","=").error)
        assertEquals(MathIssue.Syntax,fresh.tap("7","ALPHA","□/□","=").error)
    }

    @Test fun samePrintedLetterOrEqualsCanHaveDifferentActions() {
        val s = fresh.tap("7","SHIFT","RCL").chooseRegister("E").tap("AC")
        assertEquals(7.0,s.tap("ALPHA","Cos","=").answer,0.0); assertEquals(E,s.tap("ALPHA","Exp","=").answer,0.0)
        for (key in listOf("CALC","9")) {
            val e = fresh.tap("ALPHA",")","x²","ALPHA",key,"4"); assertEquals("X²=4",e.expression); assertFalse(e.complete)
            val w = e.tap("SHIFT","CALC").worksheet!!; assertEquals("root",w.taskId); assertEquals("X²=4",w.values[0])
            assertEquals(2.0,w.run(context()).scalar!!,1e-12)
        }
        val cleared = s.tap("ALPHA","RCL"); assertEquals(CalcMenu.ClearVariables,cleared.menu); assertEquals(7.0,cleared.registers[4],0.0)
        val reset = cleared.clearVariables(); assertTrue(reset.registers.all { it == 0.0 }); assertEquals(s.history,reset.history)
        assertNull(reset.menu); assertEquals(Layer.Base,reset.layer)
    }

    @Test fun imaginaryPolarAndFactorKeysOperateOnTheActualEntry() {
        val imaginary = fresh.tap("3","+","4","SHIFT","ENG","=")
        assertEquals("3+4i",imaginary.worksheet!!.values.first()); val result = imaginary.pendingWork()!!.run()
        assertTrue(result.text.startsWith("3 + 4i")); assertFalse(result.problem); assertNull(result.scalar)
        val polar = fresh.tap("2","SHIFT","(−)","9","0","=").pendingWork()!!.run(); assertTrue(polar.text.startsWith("2i"))
        val product = fresh.tap("SHIFT","ENG","SHIFT","ENG","=").pendingWork()!!.run(); assertEquals(-1.0,product.scalar!!,0.0)
        val factors = fresh.tap("3","6","0","=","SHIFT","°′″"); assertEquals("factor",factors.worksheet!!.taskId)
        assertEquals("2^3 × 3^2 × 5",factors.pendingWork()!!.run().text)
        assertEquals(360.0,factors.answer,0.0); assertEquals("360",factors.expression)
    }

    @Test fun statementsStepAndPreAnsDoesNotChangeWhenOnlyTheFormatChanges() {
        val a = fresh.tap("4","ALPHA","∫dx","Ans","×","3","="); assertEquals("4:Ans×3",a.expression)
        assertEquals(4.0,a.answer,0.0); assertEquals(0,a.statement)
        val b = a.tap("="); assertEquals(1,b.statement); assertEquals(12.0,b.answer,0.0); assertEquals(4.0,b.previousAnswer,0.0)
        val c = b.tap("ENG","=","ALPHA","Ans","="); assertEquals("PreAns",c.expression); assertEquals(4.0,c.answer,0.0); assertEquals(12.0,c.previousAnswer,0.0)
        val fail = fresh.tap("2","ALPHA","∫dx","1","÷","0","=","="); assertEquals(MathIssue.DivisionByZero,fail.error)
        assertEquals(2.0,fail.answer,0.0); assertEquals(MathIssue.DivisionByZero,fail.tap("=").error)
        assertEquals(MathIssue.Syntax,fresh.tap("1","ALPHA","∫dx","=").error)
        val hist = b.tap("AC","9","SHIFT","="); assertEquals(CalcMenu.History,hist.menu)
        assertEquals(listOf("4:Ans×3"),hist.history); val recalled = hist.chooseHistory(0)
        assertEquals("4:Ans×3",recalled.expression); assertEquals("9",recalled.tap("▼").expression)
    }

    @Test fun printedNumberShortcutsAccessWorksheetsDirectlyAndRunMath() {
        val groups = mapOf("7" to WorkGroup.Constants,"8" to WorkGroup.Conversion,"4" to WorkGroup.Matrix,"5" to WorkGroup.Vector,
            "1" to WorkGroup.Statistics,"2" to WorkGroup.Complex,"3" to WorkGroup.Distribution)
        for ((key,group) in groups) {
            val s = fresh.tap("5","+","SHIFT",key); assertTrue(s.sheetVisible); assertEquals(group,s.worksheet!!.group)
            assertEquals("5+",s.expression); assertNull(s.menu); assertEquals(Layer.Base,s.layer)
        }
        assertEquals("pol",fresh.tap("SHIFT","+").worksheet!!.taskId); assertEquals("rec",fresh.tap("SHIFT","−").worksheet!!.taskId)
        assertEquals(CalcMenu.Help,fresh.tap("SHIFT","6").menu)
        assertEquals(20.0,fresh.tap("5","SHIFT","×","2","=").answer,0.0); assertEquals(10.0,fresh.tap("5","SHIFT","÷","2","=").answer,0.0)
        assertEquals(6.0,fresh.tap("1","8","ALPHA","×","2","4","=").answer,0.0); assertEquals(72.0,fresh.tap("1","8","ALPHA","÷","2","4","=").answer,0.0)
        assertEquals(3.0,fresh.tap("ALPHA","+","2",".","1","=").answer,0.0); assertEquals(2.0,fresh.tap("ALPHA","−","2",".","1","=").answer,0.0)
        assertTrue(fresh.tap("SHIFT",".","=").answer in 0.0..<1.0); assertEquals(4.0,fresh.tap("ALPHA",".","4","SHIFT",")","4","=").answer,0.0)
    }

    @Test fun clipboardIsExplicitBoundedAndPastesTextNotCalculatorCommands() {
        var buffer = "untouched"; var reads = 0; var writes = 0
        fun CalculatorState.key(text: String) = press(pad.getValue(text), { reads++; buffer }, { writes++; buffer = it })
        val s = fresh.tap("1","÷","3","=").key("SHIFT").key("0")
        assertEquals(0,reads); assertEquals(1,writes); assertEquals((1.0/3).toString(),buffer)
        assertEquals(1.0/3,s.key("ALPHA").key("0").key("=").answer,0.0); assertEquals(1,reads)
        buffer = "٧"; val edited = fresh.tap("1","3","⇦").key("ALPHA").key("0").tap("="); assertEquals(173.0,edited.answer,0.0)
        buffer = "AC"; assertEquals("AC",s.key("ALPHA").key("0").expression)
        buffer = "1".repeat(MAX_INPUT+1); val limit = s.key("ALPHA").key("0"); assertEquals(MathIssue.Limit,limit.error); assertEquals(s.expression,limit.expression)
    }
}
