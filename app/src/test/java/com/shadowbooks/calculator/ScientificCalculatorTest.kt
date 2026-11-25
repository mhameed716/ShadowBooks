package com.shadowbooks.calculator

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class ScientificCalculatorTest {
    private val keys = (scienceKeys + numberKeys).flatten().associateBy { it.base.action }
    private fun CalculatorState.tap(vararg labels: String) = labels.fold(this) { s, label -> s.press(keys.getValue(label)) }
    private fun checkMath(expression: String, expected: Double, unit: AngleUnit = AngleUnit.DEG) =
        assertEquals(expression, expected, calculate(expression, unit), 1e-9)

    @Test fun actualKeySequenceFromTheBookExample() {
        val s = CalculatorState().tap("1", "0", "×", "sin", "3", "0", ")", "=")
        assertEquals("10×sin(30)", s.expression); assertTrue(s.complete); assertNull(s.error)
        assertEquals("5", formatNumber(s.answer)); assertEquals(listOf(s.expression), s.history)
    }

    @Test fun realExpressionPrecedenceAndPostfix() {
        mapOf("2+3*4" to 14.0, "2^3^2" to 512.0, "−2^2" to -4.0, "2^−3" to .125,
            "2(3+4)(5−2)" to 42.0, "3²^3" to 729.0, "2⁻¹" to .5, "5! / (3!×2!)" to 10.0,
            "200×15%" to 30.0, "1÷2ᴇ3" to .0005, "1E−4×2" to .0002).forEach { (s, n) -> checkMath(s, n) }
    }

    @Test fun angleUnitsAndRealFunctions() {
        checkMath("sin(π/6)", .5, AngleUnit.RAD); checkMath("atan(1)", 45.0)
        checkMath("cos(100)", 0.0, AngleUnit.GRAD); checkMath("sin(−270)", 1.0)
        checkMath("tan(π)", 0.0, AngleUnit.RAD); checkMath("asin(1)", 100.0, AngleUnit.GRAD)
        checkMath("atanh(tanh(1))", 1.0); checkMath("acosh(cosh(2))", 2.0); checkMath("asinh(sinh(−1))", -1.0)
        checkMath("sqrt(9)+cbrt(−8)+root(3,−27)", -2.0); checkMath("logb(2,8)+ln(e)+log(100)", 6.0)
        checkMath("ncr(8,3)−npr(5,2)", 36.0); checkMath("dms(−1,30,0)", -1.5)
    }

    @Test fun numericalCalculusAndSolverBindXLocally() {
        checkMath("int(X²,0,3)", 9.0); checkMath("int(X,2,−2)", 0.0)
        checkMath("int(sin(X),0,π)", 2.0, AngleUnit.RAD)
        checkMath("diff(X³,2)", 12.0); checkMath("solve(X²−2,1)", sqrt(2.0))
        val vars = registerNames.map { if (it == "X") 9.0 else 0.0 }
        assertEquals(15.0, calculate("X+diff(X²,3)", registers = vars), 1e-8)
    }

    @Test fun mathematicalAndSyntaxErrorsNeverBecomeAnAnswer() {
        fun bad(s: String, issue: MathIssue) {
            assertEquals(s, issue, assertThrows(CalculationError::class.java) { calculate(s) }.issue)
        }
        for (s in listOf("tan(90)", "sqrt(−1)", "root(2,−4)", "ln(0)", "171!", "1.2!", "logb(1,2)", "ncr(2,5)",
            "1E100000", "solve(1E−14,2)")) bad(s, MathIssue.Domain)
        bad("1÷0", MathIssue.DivisionByZero); bad("0⁻¹", MathIssue.DivisionByZero)
        for (s in listOf("", "1+", "2ᴇ−", "1.2.3", "2 3", "sin(90", "root(2)", "sin(1,2)", "foo(3)", "int(foo(X),0,0)")) bad(s, MathIssue.Syntax)
        bad("(".repeat(40) + "1" + ")".repeat(40), MathIssue.Limit)
        bad("9".repeat(MAX_INPUT + 1), MathIssue.Limit)
        bad("int(int(int(int(X,0,1),0,1),0,1),0,1)", MathIssue.Limit)
    }

    @Test fun anglesFractionsAndEngineeringFormatNeverRoundAns() {
        assertEquals("1/3", formatNumber(1.0/3, NumberFormat.Fraction))
        assertEquals("−5/8", formatNumber(-.625, NumberFormat.Fraction))
        assertFalse(formatNumber(PI, NumberFormat.Fraction).contains('/'))
        assertEquals("12.3E-6", formatNumber(.0000123, NumberFormat.Engineering))
        assertEquals("−1° 30′ 0″", formatNumber(-1.5, NumberFormat.Dms))
        assertEquals("2° 0′ 0″", formatNumber(1.99999999999, NumberFormat.Dms))
        val s = CalculatorState().tap("1", "a/b", "3", "=", "S⇄D")
        assertEquals(NumberFormat.Fraction, s.format); assertEquals(1.0/3, s.answer, 0.0)
        assertEquals(1.0, s.tap("×", "3", "=").answer, 1e-12)
    }

    @Test fun editRecallAndRecoverWithoutLosingTheLastAnswer() {
        val s = CalculatorState().tap("2", "+", "3", "=", "×", "4", "=")
        assertEquals(20.0, s.answer, 0.0)
        val err = s.tap("÷", "0", "="); assertEquals(MathIssue.DivisionByZero, err.error); assertEquals(20.0, err.answer, 0.0)
        assertEquals(10.0, err.tap("DEL", "2", "=").answer, 0.0)
        val draft = s.tap("AC", "9", "▲", "▲")
        assertEquals("2+3", draft.expression); assertEquals("9", draft.tap("▼", "▼").expression)
        assertEquals(123.0, s.tap("AC", "1", "3", "←", "2", "=").answer, 0.0)
        assertEquals(20.0, s.tap("AC", "Ans", "=").answer, 0.0)
        assertEquals(0.0, s.tap("AC", "=").answer, 0.0)
    }

    @Test fun shiftedKeysVariablesAndMemoryAreFunctional() {
        val s = CalculatorState().tap("1", "2", "M+", "SHIFT", "RCL").chooseRegister("A")
        assertEquals(12.0, s.registers[0], 0.0); assertEquals(12.0, s.registers.last(), 0.0)
        val used = s.tap("AC", "ALPHA", "(−)", "+", "RCL").chooseRegister("M").tap("=")
        assertEquals(24.0, used.answer, 0.0); assertEquals(Layer.Base, used.layer)
        assertEquals(3.0, s.tap("AC", "SHIFT", "√", "2", "7", ")", "=").answer, 1e-12)
        assertEquals(.0005, s.tap("AC", "1", "÷", "2", "Exp", "3", "=").answer, 0.0)
        assertEquals(8.0, s.tap("AC", "∫dx", "ALPHA", ")", "SHIFT", ")", "0", "SHIFT", ")", "4", ")", "=").answer, 1e-10)
    }

    @Test fun equalsCompletesTrailingParenthesesButDoesNotInventArguments() {
        val s = CalculatorState().tap("2", "×", "sin", "3", "0", "=")
        assertEquals("2×sin(30)", s.expression); assertEquals(1.0, s.answer, 1e-12)
        assertEquals(s.expression.length, s.cursor)
        assertEquals(MathIssue.Syntax, s.tap("√", "=").error)
        assertEquals(MathIssue.Syntax, s.tap("logₐx", "2", "=").error)
    }

    @Test fun keypadHasBothCompleteBanksAndOneShotModifiers() {
        assertEquals(List(5) { 6 }, scienceKeys.map { it.size })
        assertEquals(List(4) { 5 }, numberKeys.map { it.size })
        assertEquals(50, keys.size)
        val base = CalculatorState()
        assertEquals(CalcMenu.Mode, base.tap("MODE").menu)
        assertEquals(CalcMenu.Tools, base.tap("2nd").menu)
        assertEquals(CalcMenu.Hyperbolic, base.tap("hyp").menu)
        assertEquals(Layer.Base, base.tap("SHIFT", "SHIFT").layer)
        assertEquals("asin(", base.tap("SHIFT", "sin").expression)
        assertEquals("X", base.tap("ALPHA", ")").expression)
        assertEquals(Layer.Base, base.tap("ALPHA", ")").layer)
        assertEquals(2.0, base.tap("logₐx", "2", "SHIFT", ")", "4", "=").answer, 1e-12)
    }

    @Test fun boundedHistoryInputAndRegisterE() {
        var s = CalculatorState()
        repeat(25) { i -> s = s.command("AC").command(i.toString()).command("=") }
        assertEquals(HISTORY_LIMIT, s.history.size)
        s = s.command("STO").chooseRegister("E").tap("AC", "2", "ALPHA", "cos", "+", "2", "=")
        assertEquals("2×E+2", s.expression); assertEquals(50.0, s.answer, 0.0)
        s = s.command("AC"); repeat(MAX_INPUT) { s = s.command("1") }; s = s.command("1")
        assertEquals(MAX_INPUT, s.expression.length); assertEquals(MathIssue.Limit, s.error)
    }
}
