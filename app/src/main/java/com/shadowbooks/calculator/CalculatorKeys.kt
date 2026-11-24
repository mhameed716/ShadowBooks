package com.shadowbooks.calculator

/** Ink and command are deliberately separate: e above Cos is register E; e above Exp is Euler's number.
 * Likewise the red = enters a relation, whereas the white = runs the calculation. */
internal data class KeyMark(val text: String, val action: String = text)
internal data class CalcKey(val base: KeyMark, val shift: KeyMark? = null, val alpha: KeyMark? = null) {
    fun mark(layer: Layer): KeyMark = when (layer) {
        Layer.Base -> base; Layer.Shift -> shift ?: base; Layer.Alpha -> alpha ?: base
    }
}
private fun m(text: String, action: String = text) = KeyMark(text, action)
private fun k(text: String, shift: KeyMark? = null, alpha: KeyMark? = null, action: String = text) = CalcKey(m(text, action), shift, alpha)

// Main panel of com.nstudio.calc.casio.business 4.4.2, read left to right in either orientation.
// A null legend means no ink in the reference, not an omitted key. See docs/calculator-ar.md.
internal val scienceKeys = listOf(
    listOf(k("SHIFT"), k("ALPHA"), k("⇦", action = "←"), k("⇨", action = "→"), k("MODE"), k("2nd")),
    listOf(k("CALC", m("SOLVE", "solveEquation"), m("=", "relation")), k("∫dx", m("d/dx"), m(":")), k("▲"), k("▼"),
        k("x⁻¹", m("x!")), k("Logₐx", m("∑", "sum"), m("∏", "prod"), "logₐx")),
    listOf(k("□/□", m("mod"), m("÷R"), "a/b"), k("√□", m("³√□", "∛"), action = "√"), k("x²", m("x³")),
        k("x^□", m("□√□", "ⁿ√"), action = "xʸ"), k("Log", m("10ˣ"), action = "log"), k("Ln", m("e^□", "eˣ"), action = "ln")),
    listOf(k("(−)", m("∠"), m("a", "A")), k("°′″", m("FACT"), m("b", "B")), k("hyp", m("Abs", "abs"), m("c", "C")),
        k("Sin", m("Sin⁻¹", "asin"), m("d", "D"), "sin"), k("Cos", m("Cos⁻¹", "acos"), m("e", "E"), "cos"),
        k("Tan", m("Tan⁻¹", "atan"), m("f", "F"), "tan")),
    listOf(k("RCL", m("STO"), m("CLRv")), k("ENG", m("i"), m("Cot", "cot")), k("(", m("%"), m("Cot⁻¹", "acot")),
        k(")", m(","), m("x", "X")), k("S⇄D", m("aᵇ/c", "mixedFormat"), m("y", "Y")), k("M+", m("M−"), m("m", "M"))),
)
internal val numberKeys = listOf(
    listOf(k("7", m("CONST")), k("8", m("CONV")), k("9", alpha = m("=", "relation")), k("DEL"), k("AC")),
    listOf(k("4", m("MATRIX")), k("5", m("VECTOR")), k("6", m("HELP")), k("×", m("nPr"), m("GCD", "gcd")), k("÷", m("nCr"), m("LCM", "lcm"))),
    listOf(k("1", m("STAT")), k("2", m("CMPLX")), k("3", m("DISTR")), k("+", m("Pol"), m("Ceil", "ceil")), k("−", m("Rec"), m("Floor", "floor"))),
    listOf(k("0", m("COPY"), m("PASTE")), k(".", m("Ran#", "rand"), m("RanInt", "randint")), k("Exp", m("π"), m("e")),
        k("Ans", alpha = m("PreAns")), k("=", m("History"))),
)
