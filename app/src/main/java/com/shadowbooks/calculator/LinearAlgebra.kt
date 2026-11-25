package com.shadowbooks.calculator

import java.math.BigDecimal
import kotlin.math.*
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.LUDecomposition
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.SingularValueDecomposition

internal fun SheetContext.readMatrix(text: String, maxCols: Int = 4): RealMatrix {
    val rows = mathText(text.trim()).split(';', '\n')
    need(rows.size in 1..4, "أدخل من صفّ واحد إلى أربعة صفوف.")
    val cells = rows.map { list(it, maxCols) }
    need(cells.all { it.size == cells.first().size }, "يجب أن تتساوى أطوال الصفوف.")
    return Array2DRowRealMatrix(cells.map { it.toDoubleArray() }.toTypedArray(), false)
}
private fun RealMatrix.square() = need(isSquare, "تحتاج العملية إلى مصفوفة مربّعة.")
internal fun matrixReport(m: RealMatrix): SheetReport {
    val rows = m.data
    return SheetReport(rows.joinToString("\n") { row -> row.joinToString("   ", "[ ", " ]", transform = ::fmt) },
        reuse = rows.joinToString("; ") { it.joinToString(" ") })
}
/** Scale each equation before testing rank. A change of measurement units must not turn 1e−20 I singular. */
private class Scaled(a: RealMatrix) {
    val scales = a.data.map { row -> row.maxOf { abs(it) }.takeIf { it != 0.0 } ?: 1.0 }
    val matrix = Array2DRowRealMatrix(a.data.mapIndexed { i,row -> DoubleArray(row.size) { row[it] / scales[i] } }.toTypedArray(), false)
    val svd by lazy { SingularValueDecomposition(matrix) }
}
internal fun determinant(a: RealMatrix): Double {
    a.square(); val s = Scaled(a)
    val d = LUDecomposition(s.matrix, Double.MIN_VALUE).determinant
    val product = s.scales.fold(BigDecimal.valueOf(d)) { v, k -> v * BigDecimal.valueOf(k) }
    val result = finite(product.toDouble()); if (result == 0.0 && product.signum() != 0) fail(MathIssue.Limit)
    return result
}
internal fun inverse(a: RealMatrix): RealMatrix {
    a.square(); val s = Scaled(a)
    need(s.svd.rank == a.columnDimension, "المصفوفة مفردة أو قريبة جدًا من التفرد؛ لا معكوس عدديًا.")
    val r = LUDecomposition(s.matrix, Double.MIN_VALUE).solver.inverse
    for (j in s.scales.indices) for (i in s.scales.indices) r.setEntry(i, j, finite(r.getEntry(i,j) / s.scales[j]))
    return r
}
internal fun solveSystem(augmented: RealMatrix): DoubleArray {
    val n = augmented.rowDimension
    need(n in 2..4 && augmented.columnDimension == n + 1, "لكل مجهول صفّ، وآخر عمود للطرف الأيمن (من ٢ إلى ٤ مجاهيل).")
    val s = Scaled(augmented.getSubMatrix(0, n-1, 0, n-1))
    val b = ArrayRealVector(DoubleArray(n) { finite(augmented.getEntry(it,n) / s.scales[it]) }, false)
    val x = if (s.svd.rank == n) LUDecomposition(s.matrix, Double.MIN_VALUE).solver.solve(b) else s.svd.solver.solve(b)
    val normX = x.toArray().maxOf { abs(finite(it)) }
    for (i in 0 until n) {
        var y = 0.0; var rowNorm = 0.0
        for (j in 0 until n) { val a = s.matrix.getEntry(i,j); y += a * x.getEntry(j); rowNorm += abs(a) }
        val tolerance = 1e-10 * abs(b.getEntry(i)) + (1e-10 * rowNorm) * normX
        if (abs(finite(y) - b.getEntry(i)) > tolerance) {
            if (s.svd.rank < n) throw InputError("النظام غير متّسق؛ لا يوجد حلّ.") else fail(MathIssue.Limit)
        }
    }
    need(s.svd.rank == n, "النظام لا يحدّد حلًا وحيدًا؛ توجد حلول غير محدودة ضمن الدقّة العددية.")
    return x.toArray()
}
internal fun SheetContext.vector(text: String): List<Double> = list(text, 3).also {
    need(it.size in 2..3, "أدخل مركّبتين أو ثلاثًا لكل متجه.")
}
internal fun sameDimension(a: List<Double>, b: List<Double>) = need(a.size == b.size, "يجب أن يتساوى بُعدا المتجهين.")
internal fun norm(a: List<Double>) = a.fold(0.0, ::hypot)
internal fun vectorReport(a: List<Double>): SheetReport = SheetReport(a.joinToString(", ", "(", ")", transform = ::fmt), reuse = a.joinToString(" "))
internal fun dot(a: List<Double>, b: List<Double>): Double { sameDimension(a,b); return finite(a.indices.sumOf { a[it] * b[it] }) }
internal fun cross(a: List<Double>, b: List<Double>): List<Double> {
    need(a.size == 3 && b.size == 3, "الضرب الاتجاهي يحتاج إلى متجهين ثلاثيّي الأبعاد.")
    return (0..2).map { i -> val j = (i+1)%3; val k = (i+2)%3; finite(a[j]*b[k] - a[k]*b[j]) }
}
