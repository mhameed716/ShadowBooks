package com.shadowbooks.calculator

private val xSeries = InputField("قيم X", "1 2 3 4", true)
private val ySeries = InputField("قيم Y", "3 5 7 9", true)
private const val seriesHint = "افصل القيم بمسافات أو فواصل."
internal val statisticsTasks = listOf(
    task("summary","إحصاء متغيّر واحد", "$seriesHint التكرارات اختيارية. σ للمجتمع وs للعيّنة.", xSeries,
        InputField("التكرارات (اختيارية)", "", true)) { describe(list(it[0]), if (it[1].isBlank()) emptyList() else list(it[1])) },
    task("paired","إحصاء متغيّرين", "$seriesHint يجب أن تقابل كل قيمة X قيمة Y.", xSeries, ySeries) {
        val x = list(it[0]); val y = list(it[1]); need(x.size == y.size, "عدد قيم X لا يساوي عدد قيم Y.")
        SheetReport("Σxy = ${fmt(dot(x,y))}\nX:\n${describe(x).text}\nY:\n${describe(y).text}")
    },
) + Regression.entries.map { model ->
    task(model.name,"انحدار · ${model.equation}", "$seriesHint للتنبّؤ، أدخل قيمة إضافية لـ X. في النماذج المحوّلة يخصّ r البيانات المحوّلة وR² قيم Y الأصلية.",
        xSeries, ySeries, InputField("X للتنبّؤ (اختياري)", "5")) {
        regression(model,list(it[0]),list(it[1]),it[2].takeIf(String::isNotBlank)?.let(::number))
    }
}
internal val distributionTasks = Law.entries.flatMap { law ->
    val params = when (law) {
        Law.Binomial -> listOf(InputField("عدد المحاولات n", "10"), InputField("احتمال النجاح p", "0.5"))
        Law.Poisson -> listOf(InputField("المتوسط λ", "3"))
        Law.Normal -> listOf(InputField("المتوسط μ", "0"), InputField("الانحراف المعياري σ", "1"))
        Law.ChiSquare -> listOf(InputField("درجات الحرية ν", "2"))
    }
    Probability.entries.map { op ->
        val fields = params + when (op) {
            Probability.Interval -> listOf(InputField("الحد الأدنى", "1"),InputField("الحد الأعلى", "3"))
            Probability.Quantile -> listOf(InputField("الاحتمال التراكمي p", "0.95"))
            else -> listOf(InputField("x", "2"))
        }
        val discrete = law == Law.Binomial || law == Law.Poisson
        val label = if (op != Probability.Density) op.title else if (discrete) "P(K = k)" else "الكثافة f(x)"
        SheetTask("${law.name}-${op.name}","${law.title} · $label", if (discrete) "في التوزيع المتقطّع يشمل المجالُ الحدّين الصحيحين."
            else "الكثافة عند نقطة ليست احتمالًا. لحساب احتمال، اختر المجال أو التراكمي.", fields) {
            val v = it.map(::number); distribution(law,op,v.take(params.size),v[params.size],v.getOrNull(params.size+1))
        }
    }
}
internal val tableTask = task("table","جدول دالة أو دالتين", "تُقبل خطوة سالبة. الشرطة تعني أن الدالة غير معرّفة عند تلك النقطة. الحدّ ٨٠ صفًا.",
    InputField("f(X)", "X²"),InputField("g(X) (اختيارية)", "1/X"),InputField("البداية، النهاية، الخطوة", "-2 2 1")) {
    table(it[0],it[1],list(it[2],3))
}
internal val graphTask = task("plot","رسم حتى أربعة منحنيات معًا", "لكل منحنى سطر: y=X² للديكارتي، r=2 للقطبي، cos(T);sin(T) للبارامتري، وX²+Y²=4 للضمني. مجال T للقطبي والبارامتري؛ الزوايا بوحدة الحاسبة.",
    InputField("المنحنيات", "y=X²\nX²+Y²=4", true),InputField("مجال X", "-5 5"),InputField("مجال Y", "-5 5"),InputField("مجال T", "0 360")) {
    graph(it[0],it[1],it[2],it[3])
}
private fun code(index: Int) = (index+1).toString().padStart(2,'0')
internal val constantTasks = physicalConstants.mapIndexed { i,c ->
    task("const${code(i)}", "${code(i)} · ${c.symbol} · ${c.name}", "${c.name}. يمكن إدخاله في صيغة الأزرار عبر const(${i+1}). القيم بوحدات SI وفق CODATA 2022.") {
        scalarReport(c.value, c.unit)
    }
}
internal val conversionTasks = conversionPairs.flatMapIndexed { i,p -> (0..1).map { inverse ->
    val index = i*2 + inverse; val from = if (inverse == 0) p.from else p.to; val to = if (inverse == 0) p.to else p.from
    task("conv${code(index)}", "${code(index)} · $from → $to", "التحويل من $from إلى $to. متاح داخل الصيغ عبر conv(X,${index+1}).",
        InputField("القيمة", "1")) { scalarReport(convertUnit(number(it[0]), index), to) }
} }
