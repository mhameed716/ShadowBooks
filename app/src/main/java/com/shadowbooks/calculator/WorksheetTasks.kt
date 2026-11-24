package com.shadowbooks.calculator

import java.math.BigInteger
import kotlin.math.PI
import kotlin.math.acos
import org.apache.commons.math3.complex.Complex

internal fun task(id: String, title: String, hint: String, vararg fields: InputField, run: SheetContext.(List<String>) -> SheetReport) =
    SheetTask(id, title, hint, fields.toList(), run)
private const val rowsHint = "افصل الأعمدة بمسافة، والصفوف بسطر جديد أو بفاصلة منقوطة ؛. يمكن كتابة 1/2 في أي خانة."
private val matA = InputField("A", "1 2; 3 4", true)
private val matB = InputField("B", "2 0; 0 2", true)
private val vecA = InputField("u", "1 2 3"); private val vecB = InputField("v", "4 5 6")
private val scalarK = InputField("k", "2")
private const val vecHint = "اكتب مركّبات كل متجه بالترتيب وافصل بينها بمسافة."
private val matrixTasks = listOf(
    task("product","A × B",rowsHint,matA,matB) { matrixReport(readMatrix(it[0]).multiply(readMatrix(it[1]))) },
    task("add","A + B",rowsHint,matA,matB) { matrixReport(readMatrix(it[0]).add(readMatrix(it[1]))) },
    task("subtract","A − B",rowsHint,matA,matB) { matrixReport(readMatrix(it[0]).subtract(readMatrix(it[1]))) },
    task("scale","k × A",rowsHint,matA,scalarK) { matrixReport(readMatrix(it[0]).scalarMultiply(number(it[1]))) },
    task("det","المحدّد · det(A)",rowsHint,matA) { scalarReport(determinant(readMatrix(it[0]))) },
    task("inverse","المعكوس · A⁻¹",rowsHint,matA) { matrixReport(inverse(readMatrix(it[0]))) },
    task("transpose","المنقول · Aᵀ",rowsHint,matA) { matrixReport(readMatrix(it[0]).transpose()) },
)
private val vectorTasks = listOf(
    task("dot","الضرب القياسي · u · v",vecHint,vecA,vecB) { scalarReport(dot(vector(it[0]), vector(it[1]))) },
    task("cross","الضرب الاتجاهي · u × v",vecHint,vecA,vecB) { vectorReport(cross(vector(it[0]),vector(it[1]))) },
    task("add","u + v",vecHint,vecA,vecB) { val a = vector(it[0]); val b = vector(it[1]); sameDimension(a,b); vectorReport(a.zip(b,Double::plus)) },
    task("subtract","u − v",vecHint,vecA,vecB) { val a = vector(it[0]); val b = vector(it[1]); sameDimension(a,b); vectorReport(a.zip(b,Double::minus)) },
    task("scale","k × u",vecHint,vecA,scalarK) { val k = number(it[1]); vectorReport(vector(it[0]).map { v -> k*v }) },
    task("norm","الطول · |u|",vecHint,vecA) { scalarReport(norm(vector(it[0]))) },
    task("unit","متجه الوحدة",vecHint,vecA) { val a = vector(it[0]); val m = finite(norm(a)); vectorReport(a.map { v -> divide(v,m) }) },
    task("angle","الزاوية بين متجهين",vecHint,vecA,vecB) {
        val a = vector(it[0]); val b = vector(it[1]); val m = finite(norm(a)); val n = finite(norm(b))
        scalarReport(acos(dot(a.map { v -> divide(v,m) }, b.map { v -> divide(v,n) }).coerceIn(-1.0,1.0)) * angle.turn/(2*PI), angle.name)
    },
)
private val equationTasks = listOf(
    task("poly","جذور كثير حدود", "أدخل المعاملات من أعلى درجة إلى الثابت، واكتب صفرًا لكل قوة غائبة. تُعرض الجذور المركّبة أيضًا.",
        InputField("المعاملات", "1 -6 11 -6")) {
        val roots = polynomialRoots(list(it[0],13)); SheetReport(roots.mapIndexed { i,z -> "x${i+1} ≈ ${complexText(z)}" }.joinToString("\n"),
            scalar = roots.singleOrNull()?.takeIf { z -> z.imaginary == 0.0 }?.real)
    },
    task("system","نظام خطّي · ٢ إلى ٤ مجاهيل", "لكل معادلة صفّ: معاملات المجاهيل بالترتيب ثم الطرف الأيمن. $rowsHint",
        InputField("المصفوفة الموسّعة [A | b]", "1 1 5; 1 -1 1", true)) {
        SheetReport(solveSystem(readMatrix(it[0],5)).mapIndexed { i,v -> "x${i+1} = ${fmt(v)}" }.joinToString("\n"))
    },
    task("root","جذر حقيقي قرب قيمة بدء", "اكتب طرفي المعادلة أو اكتب f(X) عندما يكون الطرف الأيمن صفرًا.",
        InputField("المعادلة", "X²=2"),InputField("قيمة البدء", "1")) {
        val eq = it[0].split('='); need(eq.size <= 2, "اكتب معادلة واحدة فقط.")
        val f = if (eq.size == 1) eq[0] else "(${eq[0]})-(${eq[1]})"; scalarReport(number("solve($f,${it[1]})"))
    },
)
private val complexTasks = listOf(
    task("expression","تعبير مركّب", "استخدم i للوحدة التخيلية. تتاح re وim وabs وarg وconj وsqrt والدوال المثلثية والأسّية. تكتب الصيغة القطبية مثل rec(2,30).",
        InputField("z", "(2+3i)*(1-i)")) { complexReport(complexExpression(it[0]),angle) },
    task("pol","من ديكارتي إلى قطبي · Pol", "يُقاس الاتجاه من المحور الحقيقي الموجب وفق وحدة الزاوية المختارة.",
        InputField("x", "3"), InputField("y", "4")) { complexReport(Complex(number(it[0]),number(it[1])),angle).copy(reuse = null) },
    task("rec","من قطبي إلى ديكارتي · Rec", "أدخل نصف القطر والزاوية بوحدة الحاسبة الحالية.",
        InputField("r", "2"), InputField("θ", "90")) { complexReport(rectangular(number(it[0]), number(it[1]), angle),angle).copy(reuse = null) },
)
private val formulaTask = task("eval","تقييم الصيغة", "غيّر قيم التعويض وأعد الحساب دون إعادة كتابة الصيغة. القيم محليّة ولا تغيّر ذاكرة الأزرار.",
    InputField("الصيغة", "A*X²+B"),InputField("قيم التعويض (اختيارية)", "A=2; X=3; B=1", true)) { inputs ->
    val vars = registers.toMutableList(); val bindings = mathText(inputs[1]).trim(); val used = mutableSetOf<String>()
    if (bindings.isNotEmpty()) for (item in bindings.split(';','\n')) {
        val pair = item.split('='); val name = pair[0].trim().uppercase(); val i = registerNames.indexOf(name)
        need(pair.size == 2 && i >= 0 && used.add(name), "اكتب كل متغيّر مرة واحدة مثل X=3; A=2.")
        vars[i] = number(pair[1])
    }
    scalarReport(formula(inputs[0],vars).at())
}
private fun integer(s: String): BigInteger {
    val f = exactCalculation(s); checkDomain(f.denominator == BigInteger.ONE); return f.numerator
}
private val intA = InputField("a", "84"); private val intB = InputField("b", "30")
private val exactTasks = listOf(
    task("fraction","كسر دقيق، مختلط، وعشري دوري", "اكتب 0.(3) للثلث، وmixed(2,1,3) للعدد ٢ وثلث. تعمل العمليات الأربع والقوى الصحيحة على البسط والمقام دون تقريب.",
        InputField("التعبير", "mixed(2,1,3) + 0.1(6)")) {
        val q = exactCalculation(it[0]); val pair = q.numerator.toString() to q.denominator.toString()
        val decimal = approximate(q).takeIf { n -> n.isFinite() && (n != 0.0 || q.numerator.signum() == 0) }
        SheetReport("${mixedText(q)}\n${recurringText(q)}", decimal, reuse = "(${pair.first}/${pair.second})".takeIf { s -> s.length <= MAX_INPUT }, fraction = pair)
    },
    task("factor","العوامل الأولية · FACT", "تحليل عدد صحيح غير صفري حتى 10¹² بالقيمة المطلقة.",InputField("n", "360")) {
        val n = integer(it[0]); checkDomain(n.abs() <= 1_000_000_000_000L.toBigInteger()); SheetReport(primeFactors(n.toLong()))
    },
    task("gcd","القاسم المشترك الأكبر · GCD","أدخل عددين صحيحين.",intA,intB) { SheetReport(integer(it[0]).gcd(integer(it[1])).toString()) },
    task("lcm","المضاعف المشترك الأصغر · LCM","أدخل عددين صحيحين.",intA,intB) {
        val a = integer(it[0]).abs(); val b = integer(it[1]).abs(); val d = a.gcd(b); SheetReport((if (d.signum() == 0) d else a / d * b).toString())
    },
    task("random","عدد عشوائي بين صفر وواحد", "كل ضغطة تولّد قيمة جديدة أصغر من الواحد.") { scalarReport(kotlin.random.Random.nextDouble()) },
    task("randint","عدد صحيح عشوائي", "الحدّان داخلان في الاختيار.",InputField("الحد الأدنى", "1"),InputField("الحد الأعلى", "6")) {
        scalarReport(number("randint(${it[0]},${it[1]})"))
    },
)

internal val worksheetTasks: Map<WorkGroup,List<SheetTask>> by lazy {
    mapOf(WorkGroup.Formula to listOf(formulaTask), WorkGroup.Complex to complexTasks, WorkGroup.Equations to equationTasks,
        WorkGroup.Matrix to matrixTasks, WorkGroup.Vector to vectorTasks, WorkGroup.Statistics to statisticsTasks,
        WorkGroup.Distribution to distributionTasks, WorkGroup.Table to listOf(tableTask), WorkGroup.Graph to listOf(graphTask),
        WorkGroup.Exact to exactTasks, WorkGroup.Constants to constantTasks, WorkGroup.Conversion to conversionTasks)
}
