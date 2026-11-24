package com.shadowbooks.calculator

import kotlin.math.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class SamplingCatalogTest {
    @Test fun bothConversionDirectionsAgainstReferenceAndAllCodesRoundTrip() {
        assertEquals(20,conversionPairs.size); assertEquals(40,conversionTasks.size)
        assertEquals(12.7,calculate("conv(5,1)"),1e-14); assertEquals(1.0,calculate("conv(2.54,2)"),0.0)
        assertEquals(3.785411784,calculate("conv(1,13)"),0.0); assertEquals(4.54609,calculate("conv(1,15)"),0.0)
        assertEquals(10.0,calculate("conv(36,19)"),1e-14); assertEquals(101325.0,calculate("conv(1,25)"),0.0)
        assertEquals(0.0,calculate("conv(32,37)"),0.0); assertEquals(212.0,calculate("conv(100,38)"),1e-12)
        assertEquals(4.1858,calculate("conv(1,40)"),1e-14)
        for (i in 0..19) for (x in listOf(-17.25,0.0,42.5,3e6)) {
            assertEquals(conversionPairs[i].from,x,convertUnit(convertUnit(x,2*i),2*i+1),max(1e-13,abs(x)*1e-15))
        }
        assertTrue(sheet(WorkGroup.Formula,"eval","conv(10,0)","").problem)
    }
    @Test fun constantsUseDocumentedIdsSignsAndPhysicalRelations() {
        assertEquals(40,physicalConstants.size); assertEquals(40,constantTasks.size)
        assertEquals(299792458.0,calculate("const(28)"),0.0); assertEquals(6.62607015e-34,calculate("const(6)"),0.0)
        assertEquals(6.02214076e23,calculate("const(24)"),0.0); assertTrue(calculate("const(19)") < 0)
        assertEquals(5.670374419e-8,calculate("const(31)"),1e-17); assertEquals(0.02271095464,calculate("const(26)"),1e-11)
        assertEquals(299792458.0,calculate("1/sqrt(const(32)*const(33))"),.1)
        assertTrue(sheet(WorkGroup.Formula,"eval","const(41)","").problem)
    }
    @Test fun tablesAcceptDescendingFractionalRangesAndKeepHolesSeparateFromErrors() {
        val t = sheet(WorkGroup.Table,"table","X²","1/X","0.3 0 -0.1").success()
        assertEquals(5,t.text.lines().size); assertTrue(t.text.lines().last().endsWith("0   |   0   |   —"))
        assertEquals(2,sheet(WorkGroup.Table,"table","X","","1 1 -1").success().text.lines().size)
        for (s in listOf("0 1 0","0 2 -1","0 1000 1","0 1")) assertTrue(s,sheet(WorkGroup.Table,"table","X","",s).problem)
        assertTrue(sheet(WorkGroup.Table,"table","nonexistent(X)","","1 2 1").problem)
    }
    @Test fun overlayCartesianPolarParametricImplicitOnOneCalibratedViewport() {
        val r = sheet(WorkGroup.Graph,"plot","y=X\nr=1\ncos(T);sin(T)\nX²+Y²=1","-2 2","-2 2","0 360").success()
        val p = r.plot!!; assertEquals(4,p.curves.size); assertTrue(p.curves.all { it.segments.size > 80 })
        for ((i,c) in p.curves.withIndex()) for (segment in c.segments) for (point in listOf(segment.from,segment.to)) {
            assertTrue(point.x in 0.0..1.0 && point.y in 0.0..1.0); val x = p.x.at(point.x); val y = p.y.at(point.y)
            if (i == 0) assertEquals(x,y,1e-14) else assertEquals(1.0,hypot(x,y),.001)
        }
        val rad = sheet(WorkGroup.Graph,"plot","r=1","-1 1","-1 1","0 2*pi",ctx = context(AngleUnit.RAD)).success().plot!!
        assertTrue(rad.curves[0].segments.any { it.from.y < .01 }); assertTrue(rad.curves[0].segments.any { it.from.y > .99 })
    }
    @Test fun neverBridgeAPoleAndDontTurnMalformedOrExpensiveGraphsIntoBlankPlots() {
        val pole = .031; val poleX = (pole + 1) / 2
        val plot = sheet(WorkGroup.Graph,"plot","y=1/(X-0.031)","-1 1","-100 100","0 1").success().plot!!
        assertFalse(plot.curves[0].segments.any { it.from.x < poleX && it.to.x > poleX })
        assertTrue(sheet(WorkGroup.Graph,"plot","1/(X-0.031)=0","-1 1","-1 1","0 1").problem)
        assertTrue(sheet(WorkGroup.Graph,"plot","x=1","1 1","-1 1","0 1").problem)
        assertTrue(sheet(WorkGroup.Graph,"plot","y=sin(","-1 1","-1 1","0 1").problem)
        assertTrue(sheet(WorkGroup.Graph,"plot","y=sqrt(-1)","-1 1","-1 1","0 1").problem)
        assertTrue(sheet(WorkGroup.Graph,"plot","y=sum(sum(X,1,10000),1,10000)","-1 1","-1 1","0 1").problem)
    }
    @Test fun handwrittenAndPastedInvalidInputCannotEscapeTheWorksheet() {
        val rng = Random(4793); val alphabet = "123٠;+-i²Y# ,^)(.﷼\u0000 \n"
        for ((group,tasks) in worksheetTasks) for (task in tasks) if (task.fields.isNotEmpty()) repeat(3) {
            val w = Worksheet.initial(group,task.id); val text = buildString { repeat(rng.nextInt(0,48)) { append(alphabet.random(rng)) } }
            val result = w.edit(rng.nextInt(w.values.size), text).run(context()); assertTrue(result.text.isNotEmpty())
        }
    }
}
