package com.shadowbooks.calculator

import kotlin.math.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class LinearStatisticsTest {
    @Test fun matrixResultsCanBeUsedAgainWithoutFormattingLoss() {
        val ctx = context(); val a = ctx.readMatrix("١ ٢؛ ٣ ٤")
        assertEquals(-2.0, determinant(a),1e-14); assertEquals(0.0,determinant(ctx.readMatrix("1 2;2 4")),0.0)
        val inv = sheet(WorkGroup.Matrix,"inverse","1 2;3 4").success()
        val identity = a.multiply(ctx.readMatrix(inv.reuse!!)); assertEquals(1.0,identity.getEntry(0,0),1e-13); assertEquals(0.0,identity.getEntry(0,1),1e-13)
        assertEquals(2e-40,determinant(ctx.readMatrix("1e-20 0;0 2e-20")),1e-54)
        assertEquals(1e20,inverse(ctx.readMatrix("1e-20 0;0 2e-20")).getEntry(0,0),1e6)
        val p = sheet(WorkGroup.Matrix,"product","1 2 3;4 5 6","1 0;0 1;1 1").success(); assertEquals(11.0,ctx.readMatrix(p.reuse!!).getEntry(1,1),0.0)
        assertTrue(sheet(WorkGroup.Matrix,"inverse","1 2;2 4").problem); assertTrue(sheet(WorkGroup.Matrix,"add","1 2","1;2").problem)
        assertTrue(sheet(WorkGroup.Matrix,"det","1 2;3").problem); assertTrue(sheet(WorkGroup.Matrix,"det","1,,2;3,4").problem)
        assertTrue(sheet(WorkGroup.Matrix,"det","1,2,;3,4").problem); assertEquals(6.0,determinant(ctx.readMatrix("logb(2, 8), 2;0,2")),0.0)
    }
    @Test fun systemsHaveDistinctUniqueInconsistentAndInfiniteOutcomes() {
        val ctx = context()
        assertArrayEquals(doubleArrayOf(3.0,2.0), solveSystem(ctx.readMatrix("1 1 5;1 -1 1",5)),1e-12)
        assertArrayEquals(doubleArrayOf(2.0,3.0,-1.0), solveSystem(ctx.readMatrix("2 1 -1 8;-3 -1 2 -11;-2 1 2 -3",5)),1e-11)
        assertArrayEquals(doubleArrayOf(1.0,-2.0,3.0,4.0), solveSystem(ctx.readMatrix("0 1 0 0 -2;1e-25 0 0 0 1e-25;0 0 0 1 4;0 0 3 0 9",5)),1e-12)
        val a = sheet(WorkGroup.Equations,"system","1 2 3;2 4 6"); val b = sheet(WorkGroup.Equations,"system","1 2 3;2 4 7")
        assertTrue(a.problem && b.problem); assertTrue(a.text.contains("غير محدودة")); assertTrue(b.text.contains("لا يوجد حلّ"))
        assertTrue(sheet(WorkGroup.Equations,"system","1 2;3 4").problem)
    }
    @Test fun randomWellConditionedSystemsRecoverTheirKnownSolutions() {
        val rng = Random(445); val ctx = context()
        for (n in 2..4) repeat(50) {
            val x = DoubleArray(n) { rng.nextInt(-30,31).toDouble() }
            val rows = (0 until n).joinToString(";") { i ->
                val row = DoubleArray(n) { j -> if (i == j) 25.0 else rng.nextDouble(-2.0,2.0) }
                row.joinToString(" ") + " " + row.indices.sumOf { row[it]*x[it] }
            }
            assertArrayEquals(x,solveSystem(ctx.readMatrix(rows,5)),1e-10)
        }
    }
    @Test fun vectorsEnforceDimensionsAndProduceAnglesInTheSelectedUnit() {
        val u = listOf(1.0,2.0,3.0); val v = listOf(4.0,5.0,6.0)
        assertEquals(32.0,dot(u,v),0.0); assertEquals(listOf(-3.0,6.0,-3.0),cross(u,v)); assertEquals(0.0,dot(u,cross(u,v)),0.0)
        assertEquals(5.0,sheet(WorkGroup.Vector,"norm","3 4").scalar!!,0.0)
        assertEquals(100.0,sheet(WorkGroup.Vector,"angle","1 0","0 1",ctx = context(AngleUnit.GRAD)).scalar!!,1e-12)
        assertTrue(sheet(WorkGroup.Vector,"angle","0 0","1 0").problem); assertTrue(sheet(WorkGroup.Vector,"cross","1 0","0 1").problem)
        assertTrue(sheet(WorkGroup.Vector,"dot","1 2 3","1 2").problem)
    }
    @Test fun weightedStatisticsUsesSampleVsPopulationAndOrderStatistics() {
        val r = describe(listOf(1.0,3.0,9.0),listOf(2.0,2.0,0.0)); assertTrue(r.text.contains("n = 4\nΣx = 8\nΣx² = 20\nx̄ = 2\nσx = 1\nsx = 1.15470053838")); assertTrue(r.text.contains("Med = 2"))
        assertTrue(describe(listOf(5.0)).text.contains("sx = —")); assertTrue(sheet(WorkGroup.Statistics,"summary","2 3","0 0").problem)
        assertTrue(sheet(WorkGroup.Statistics,"summary","2 3","1").problem); assertTrue(sheet(WorkGroup.Statistics,"summary","2 3","1 -1").problem)
        assertTrue(sheet(WorkGroup.Statistics,"paired","1 2 3","4 5 6").text.startsWith("Σxy = 32"))
    }
    @Test fun regressionsRecoverTheirEquationsAndRejectSingularModels() {
        val xs = (1..6).map(Int::toDouble)
        val equations = mapOf<Regression,(Double)->Double>(Regression.Linear to { 1+2*it },Regression.Quadratic to { 1+2*it-3*it*it },
            Regression.Exp to { 3*exp(.5*it) },Regression.Geometric to { 3*1.2.pow(it) },Regression.Log to { 1+4*ln(it) },
            Regression.Inverse to { 10-3/it },Regression.Power to { 3*it.pow(-2.5) })
        for ((kind, f) in equations) { val r = regression(kind,xs,xs.map(f),7.0); assertEquals(kind.name,f(7.0),r.scalar!!,1e-10); assertTrue(r.text.contains("R² = 1")) }
        assertTrue(sheet(WorkGroup.Statistics,"Quadratic","1 1 2","2 3 4","").problem)
        assertTrue(sheet(WorkGroup.Statistics,"Power","-1 2","2 3","").problem)
        assertTrue(sheet(WorkGroup.Statistics,"Linear","1 2","2","").problem)
    }
    @Test fun distributionsAgainstClosedFormsAndReferenceQuantiles() {
        fun v(law: Law, op: Probability, p: List<Double>, x: Double, b: Double? = null) = distribution(law,op,p,x,b).scalar!!
        assertEquals(.375,v(Law.Binomial,Probability.Density,listOf(4.0,.5),2.0),1e-14)
        assertEquals(.6875,v(Law.Binomial,Probability.Cdf,listOf(4.0,.5),2.0),1e-14)
        assertEquals(.875,v(Law.Binomial,Probability.Interval,listOf(4.0,.5),1.0,3.0),1e-14)
        assertEquals(3.0,v(Law.Binomial,Probability.Quantile,listOf(4.0,.5),.8),0.0)
        assertEquals(2*exp(-2.0),v(Law.Poisson,Probability.Density,listOf(2.0),1.0),1e-14)
        assertEquals(3*exp(-2.0),v(Law.Poisson,Probability.Cdf,listOf(2.0),1.0),1e-13)
        assertEquals(.5,v(Law.Normal,Probability.Cdf,listOf(7.0,2.0),7.0),1e-14)
        assertEquals(1/sqrt(2*PI),v(Law.Normal,Probability.Density,listOf(0.0,1.0),0.0),1e-14)
        assertEquals(.682689492137086,v(Law.Normal,Probability.Interval,listOf(0.0,1.0),-1.0,1.0),1e-14)
        assertEquals(1.95996398454,v(Law.Normal,Probability.Quantile,listOf(0.0,1.0),.975),1e-10)
        assertEquals(1-exp(-2.0),v(Law.ChiSquare,Probability.Cdf,listOf(2.0),4.0),1e-13)
        assertEquals(.5,v(Law.ChiSquare,Probability.Density,listOf(2.0),0.0),0.0)
        assertEquals(-2*ln(.05),v(Law.ChiSquare,Probability.Quantile,listOf(2.0),.95),1e-10)
    }
    @Test fun rareDiscreteEventsAreNotErasedBySubtractingRoundedCdfs() {
        // Independently summed binomial coefficients / Poisson series.
        assertEquals(6.103231678042162e-19,distribution(Law.Binomial,Probability.Interval,listOf(100.0,.01),19.0,20.0).scalar!!,1e-31)
        assertEquals(2.4944165604029597e-20,distribution(Law.Binomial,Probability.Cdf,listOf(100.0,.99),80.0,null).scalar!!,1e-32)
        assertEquals(1.2795512789131271e-22,distribution(Law.Poisson,Probability.Interval,listOf(2.0),28.0,31.0).scalar!!,1e-34)
        assertEquals(1.0-1e-11,distribution(Law.Binomial,Probability.Cdf,listOf(1e6,1e-17),0.0,null).scalar!!,1e-15)
        assertEquals(0.0,distribution(Law.Binomial,Probability.Interval,listOf(20.0,.4),.2,.7).scalar!!,0.0)
    }
    @Test fun distributionBoundsAndTailsAreNotMadeUp() {
        assertEquals(1.0,sheet(WorkGroup.Distribution,"Poisson-Density","0","0").scalar!!,0.0)
        assertEquals(0.0,sheet(WorkGroup.Distribution,"Binomial-Density","4","0.5","1.5").scalar!!,0.0)
        assertEquals("+∞",sheet(WorkGroup.Distribution,"Poisson-Quantile","2","1").text)
        assertEquals("−∞",sheet(WorkGroup.Distribution,"Normal-Quantile","0","1","0").text)
        assertEquals("+∞",sheet(WorkGroup.Distribution,"ChiSquare-Density","1","0").text)
        assertTrue(sheet(WorkGroup.Distribution,"Binomial-Cdf","4","1.1","2").problem)
        assertTrue(sheet(WorkGroup.Distribution,"Normal-Interval","0","1","3","1").problem)
        assertTrue(sheet(WorkGroup.Distribution,"Normal-Cdf","0","0","1").problem)
        val tiny = distribution(Law.Normal,Probability.Interval,listOf(0.0,1.0),9.0,10.0).scalar!!
        assertEquals(1.1285122074236e-19,tiny,1e-31)
    }
}
