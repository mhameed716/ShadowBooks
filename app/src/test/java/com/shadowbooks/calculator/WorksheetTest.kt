package com.shadowbooks.calculator

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

internal fun context(unit: AngleUnit = AngleUnit.DEG) = SheetContext(unit, 0.0, List(registerNames.size) { 0.0 })
internal fun sheet(group: WorkGroup, id: String, vararg values: String, ctx: SheetContext = context()): SheetReport {
    val w = Worksheet.initial(group,id); assertEquals(id,w.taskId)
    return w.copy(values = values.toList()).run(ctx)
}
internal fun SheetReport.success(): SheetReport = also { assertFalse(text,problem) }

class WorksheetTest {
    @Test fun everyModeAndEveryListedOperationExecutesItsEditableExample() {
        assertEquals(WorkGroup.entries.toSet(), worksheetTasks.keys)
        worksheetTasks.forEach { (group, tasks) ->
            assertTrue(tasks.isNotEmpty()); assertEquals(tasks.size,tasks.map { it.id }.distinct().size)
            for (t in tasks) {
                val w = CalculatorState().command("MODE").openSheet(group).worksheet!!.select(t.id).request()
                assertEquals(t.id,w.taskId); val result = w.run(context()); assertFalse("$group/${t.id}: ${result.text}",result.problem)
                assertTrue(result.text.isNotBlank()); assertFalse(result.text.contains("NaN"))
            }
        }
    }
    @Test fun localBindingsRegistersAndScalarHandoffAreExplicit() {
        val start = CalculatorState().command("7").command("STO").chooseRegister("A").command("=")
            .command("AC").command("A").command("×").command("X").command("+").command("Ans")
        val s = start.command("CALC"); val w = s.worksheet!!
        assertTrue(s.sheetVisible); assertEquals("A×X+Ans",w.values[0]); assertTrue(w.values[1].contains("A=7.0"))
        val draft = w.edit(1,"A=٢; X=۳").request(); val r = draft.run(SheetContext(s.unit,s.answer,s.registers)).success()
        assertEquals(13.0,r.scalar!!,0.0); val done = s.copy(worksheet = draft.copy(report = r))
        assertEquals(7.0,done.answer,0.0); assertEquals(7.0,done.registers[0],0.0)
        assertEquals(draft.values,done.copy(sheetVisible = false).openSheet(WorkGroup.Formula).worksheet!!.values)
        val pad = done.useSheetAnswer(); assertFalse(pad.sheetVisible); assertTrue(pad.complete)
        assertEquals(26.0,pad.command("×").command("2").command("=").answer,0.0)
        assertNull(done.chooseAngle(AngleUnit.RAD).worksheet!!.report)
        assertNull(done.copy(sheetVisible = false).command("AC").command("9").command("=").worksheet!!.report)
        assertTrue(sheet(WorkGroup.Formula,"eval","A","A=1; A=3").problem)
    }
    @Test fun editingOrSelectingNeverLeavesAnOldResultUnderNewInputs() {
        val w = Worksheet.initial(WorkGroup.Matrix,"add").edit(0,"2 3; 4 5").request(); val done = w.copy(report = w.run(context()))
        val next = done.select("inverse"); assertEquals(listOf("2 3; 4 5"),next.values); assertFalse(next.submitted); assertNull(next.report)
        assertNull(done.edit(1,"1").report); assertEquals(done,done.edit(1,"1".repeat(FIELD_LIMIT+1)))
        val origin = CalculatorState().command("1").openSheet(WorkGroup.Matrix).copy(worksheet = done)
        assertEquals(origin,origin.useSheetAnswer()); assertEquals("1",origin.expression)
    }
    @Test fun aLateWorkerCannotReplaceNewInputsOrAnEditedAngle() {
        val start = CalculatorState().openSheet(WorkGroup.Formula); val initial = start.worksheet!!
        val s = start.copy(worksheet = initial.request()); val request = s.pendingWork()!!; val result = request.run()
        val changed = s.copy(worksheet = initial.edit(1,"X=9").request())
        assertSame(changed,changed.finishWork(request,result)); assertNull(changed.worksheet!!.report)
        val angle = s.chooseAngle(AngleUnit.RAD); assertSame(angle,angle.finishWork(request,result))
        val done = s.finishWork(request,result); assertSame(result,done.worksheet!!.report); assertNull(done.pendingWork())
        assertSame(done,done.finishWork(request,result))
    }
    @Test fun complexArithmeticPowersRootsFunctionsAndAngleModes() {
        fun z(s: String, re: Double, im: Double, angle: AngleUnit = AngleUnit.DEG) {
            val v = context(angle).complexExpression(s); assertEquals(s,re,v.real,1e-11); assertEquals(s,im,v.imaginary,1e-11)
        }
        z("(2+3i)*(1-i)",5.0,1.0); z("(1+i)/(1-i)",0.0,1.0); z("sqrt(-4)",0.0,2.0); z("i^(-3)",0.0,1.0)
        z("2∠100",0.0,2.0,AngleUnit.GRAD); z("rec(3,pi)",-3.0,0.0,AngleUnit.RAD)
        z("conj(2-3i)+abs(3+4i)",7.0,3.0); z("arg(-1-i)",-135.0,0.0); z("im(i*i)+re(i²)",-1.0,0.0)
        z("exp(ln(2+3i))",2.0,3.0); z("sin(30)",.5,0.0)
        z("root(2,-4)",0.0,2.0); z("logb(2,8)",3.0,0.0); z("sinh(asinh(1+i))",1.0,1.0)
        val r = sheet(WorkGroup.Complex,"expression","sqrt(-1)").success(); assertNull(r.scalar); z(r.reuse!!,0.0,1.0)
        assertTrue(sheet(WorkGroup.Complex,"expression","1/(1+i-i-1)").problem)
        assertTrue(sheet(WorkGroup.Complex,"expression","arg(0)").problem)
        assertTrue(sheet(WorkGroup.Complex,"pol","0","0").success().text.contains("—"))
    }
    @Test fun quadraticCubicQuarticComplexMultiplicityAndConditioning() {
        val r = polynomialRoots(listOf(1.0,-6.0,11.0,-6.0)); r.forEachIndexed { i,z -> assertEquals(i+1.0,z.real,1e-9); assertEquals(0.0,z.imaginary,1e-9) }
        val circle = polynomialRoots(listOf(1.0,0.0,0.0,0.0,1.0)); assertEquals(4,circle.size)
        for (z in circle) assertEquals(0.0,z.pow(4.0).add(1.0).abs(),1e-9)
        val repeated = polynomialRoots(listOf(1.0,-4.0,6.0,-4.0,1.0)); assertEquals(4,repeated.size)
        repeated.forEach { assertEquals(0.0,it.subtract(1.0).abs(),2e-4) }
        val near = polynomialRoots(listOf(1.0,1e12,1.0)); assertEquals(-1e-12,near.last().real,1e-26)
        assertEquals(3,polynomialRoots(listOf(1.0,0.0,0.0,0.0)).count { it.abs() == 0.0 })
        val imag = polynomialRoots(listOf(3e-250,0.0,3e-250)); assertEquals(-1.0,imag[0].imaginary,1e-12)
        assertTrue(sheet(WorkGroup.Equations,"poly","0 1 2").problem)
    }
    @Test fun exactRationalsMixedAndRecurringDoNotRoundTripThroughDouble() {
        fun value(s: String, n: String, d: String) {
            val r = sheet(WorkGroup.Exact,"fraction",s).success(); assertEquals(n to d,r.fraction)
        }
        value("999999999999999999999999 + 1", "1000000000000000000000000","1")
        value("1/3+1/6","1","2"); value("mixed(-2,1,3)+0.1(6)","-13","6")
        value("0.(03)","1","33"); value("0.9(9)","1","1"); value("0.00(6)","1","150")
        value("2⁻¹+2^-2", "3","4"); value("1.20E-2", "3","250"); value("10!/9!", "10","1")
        value("ncr(100,50)", "100891344545564193334812497256","1"); value("npr(5,3)","60","1")
        assertEquals("−2  1/3",mixedText(exactCalculation("-7/3"))); assertEquals("0.0(3)",recurringText(exactCalculation("1/30")))
        assertEquals("0.125",recurringText(exactCalculation("1/8"))); assertTrue(recurringText(exactCalculation("1/983")).endsWith("…"))
        for (bad in listOf("1/0","0⁻¹","2^1000000000","1E2147483647","1.4!","mixed(2,3,2)"))
            assertTrue(bad,sheet(WorkGroup.Exact,"fraction",bad).problem)
    }
    @Test fun numberTheoryRandomAndSharedTypedNotation() {
        assertEquals("−1 × 2^3 × 3^2 × 5",primeFactors(-360)); assertEquals("999983",primeFactors(999983))
        assertEquals("6",sheet(WorkGroup.Exact,"gcd","-84","30").success().text); assertEquals("420",sheet(WorkGroup.Exact,"lcm","84","30").success().text)
        assertEquals("0",sheet(WorkGroup.Exact,"lcm","0","0").text)
        assertEquals(7.0, calculate("gcd(-٢١،28)"),0.0); assertEquals(30.0,calculate("sum(X²,1,4)"),0.0)
        assertEquals(2.5,calculate("mixed(2,1,3)+0.1(6)"),0.0); assertEquals(2.125,calculate("۲٫۱۲۵"),0.0)
        assertEquals(5.0,calculate("polr(3,4)"),0.0); assertEquals(135.0,calculate("pola(-1,1)"),1e-12)
        assertEquals(-2.0,calculate("recx(2,180)"),0.0); assertEquals(2.0,calculate("recy(2,90)"),0.0)
        repeat(100) { assertTrue(calculate("rand()") in 0.0..<1.0); assertTrue(calculate("randint(-3,1)") in -3.0..1.0) }
        assertEquals(4.0,calculate("randint(4,4)"),0.0); assertTrue(sheet(WorkGroup.Exact,"randint","3.5","4").problem)
    }
}
