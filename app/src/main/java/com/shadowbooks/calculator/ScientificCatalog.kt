package com.shadowbooks.calculator

import kotlin.math.PI
import kotlin.math.pow

internal data class PhysicalConstant(val symbol: String, val name: String, val unit: String, val value: Double)
private const val h = 6.62607015e-34
private const val charge = 1.602176634e-19
private const val light = 299792458.0
private const val boltzmann = 1.380649e-23
private const val avogadro = 6.02214076e23
private const val gas = boltzmann * avogadro

/** ES PLUS identifier order; values from CODATA 2022 / SI definitions, not a 2019 ROM. */
internal val physicalConstants = listOf(
    PhysicalConstant("mₚ", "كتلة البروتون", "kg", 1.67262192595e-27),
    PhysicalConstant("mₙ", "كتلة النيوترون", "kg", 1.67492750056e-27),
    PhysicalConstant("mₑ", "كتلة الإلكترون", "kg", 9.1093837139e-31),
    PhysicalConstant("mμ", "كتلة الميون", "kg", 1.883531627e-28),
    PhysicalConstant("a₀", "نصف قطر بور", "m", 5.29177210544e-11),
    PhysicalConstant("h", "ثابت بلانك", "J·s", h),
    PhysicalConstant("μN", "المغنطون النووي", "J/T", 5.0507837393e-27),
    PhysicalConstant("μB", "مغنطون بور", "J/T", 9.2740100657e-24),
    PhysicalConstant("ℏ", "ثابت بلانك المخفّض", "J·s", h / (2 * PI)),
    PhysicalConstant("α", "ثابت البنية الدقيقة", "1", 7.2973525643e-3),
    PhysicalConstant("rₑ", "نصف قطر الإلكترون الكلاسيكي", "m", 2.8179403205e-15),
    PhysicalConstant("λC", "طول موجة كومبتون للإلكترون", "m", 2.42631023538e-12),
    PhysicalConstant("γₚ", "النسبة الجيرومغناطيسية للبروتون", "s⁻¹/T", 2.6752218708e8),
    PhysicalConstant("λCₚ", "طول موجة كومبتون للبروتون", "m", 1.32140985360e-15),
    PhysicalConstant("λCₙ", "طول موجة كومبتون للنيوترون", "m", 1.31959090382e-15),
    PhysicalConstant("R∞", "ثابت ريدبرغ", "m⁻¹", 10973731.568157),
    PhysicalConstant("u", "وحدة الكتلة الذرية", "kg", 1.66053906892e-27),
    PhysicalConstant("μₚ", "العزم المغناطيسي للبروتون", "J/T", 1.41060679545e-26),
    PhysicalConstant("μₑ", "العزم المغناطيسي للإلكترون", "J/T", -9.2847646917e-24),
    PhysicalConstant("μₙ", "العزم المغناطيسي للنيوترون", "J/T", -9.6623653e-27),
    PhysicalConstant("μμ", "العزم المغناطيسي للميون", "J/T", -4.49044830e-26),
    PhysicalConstant("F", "ثابت فاراداي", "C/mol", charge * avogadro),
    PhysicalConstant("e", "الشحنة الأولية", "C", charge),
    PhysicalConstant("Nₐ", "ثابت أفوغادرو", "mol⁻¹", avogadro),
    PhysicalConstant("k", "ثابت بولتزمان", "J/K", boltzmann),
    PhysicalConstant("Vₘ", "الحجم المولي للغاز المثالي (273.15 K، 100 kPa)", "m³/mol", gas * 273.15 / 1e5),
    PhysicalConstant("R", "ثابت الغازات", "J/(mol·K)", gas),
    PhysicalConstant("c₀", "سرعة الضوء في الفراغ", "m/s", light),
    PhysicalConstant("c₁", "ثابت الإشعاع الأول", "W·m²", 2 * PI * h * light * light),
    PhysicalConstant("c₂", "ثابت الإشعاع الثاني", "m·K", h * light / boltzmann),
    PhysicalConstant("σ", "ثابت ستيفان–بولتزمان", "W/(m²·K⁴)", 2 * PI.pow(5) * boltzmann.pow(4) / (15 * h.pow(3) * light * light)),
    PhysicalConstant("ε₀", "سماحية الفراغ", "F/m", 8.8541878188e-12),
    PhysicalConstant("μ₀", "نفاذية الفراغ", "N/A²", 1.25663706127e-6),
    PhysicalConstant("Φ₀", "كمّ الفيض المغناطيسي", "Wb", h / (2 * charge)),
    PhysicalConstant("g₀", "تسارع الجاذبية القياسي", "m/s²", 9.80665),
    PhysicalConstant("G₀", "كمّ الناقلية", "S", 2 * charge * charge / h),
    PhysicalConstant("Z₀", "معاوقة الفراغ", "Ω", 376.730313412),
    PhysicalConstant("T₀", "صفر السلم المئوي بالكلفن", "K", 273.15),
    PhysicalConstant("G", "ثابت الجذب العام", "m³/(kg·s²)", 6.67430e-11),
    PhysicalConstant("atm", "الضغط الجوي القياسي", "Pa", 101325.0),
)

internal data class UnitPair(val from: String, val to: String, val factor: Double, val subtract: Double = 0.0)
internal val conversionPairs = listOf(
    UnitPair("in", "cm", 2.54), UnitPair("ft", "m", 0.3048), UnitPair("yd", "m", 0.9144), UnitPair("mi", "km", 1.609344),
    UnitPair("nmi", "m", 1852.0), UnitPair("acre", "m²", 4046.8564224), UnitPair("gal US", "L", 3.785411784), UnitPair("gal UK", "L", 4.54609),
    UnitPair("pc", "km", 648000 / PI * 149597870.7), UnitPair("km/h", "m/s", 1 / 3.6), UnitPair("oz", "g", 28.349523125),
    UnitPair("lb", "kg", 0.45359237), UnitPair("atm", "Pa", 101325.0), UnitPair("mmHg", "Pa", 133.322387415),
    UnitPair("hp", "kW", 0.7456998715822702), UnitPair("kgf/cm²", "Pa", 98066.5), UnitPair("kgf·m", "J", 9.80665),
    UnitPair("psi", "kPa", 6.894757293168361), UnitPair("°F", "°C", 5.0 / 9, 32.0), UnitPair("J", "cal₁₅", 1 / 4.18580),
)

internal fun catalogIndex(code: Double): Int {
    checkDomain(code in 1.0..40.0 && code == code.toInt().toDouble()); return code.toInt() - 1
}
/** Index is zero-based here; keypad CONV numbers are one-based, odd forward / even reverse. */
internal fun convertUnit(value: Double, index: Int): Double {
    val p = conversionPairs[index / 2]
    return finite(if (index % 2 == 0) (value - p.subtract) * p.factor else value / p.factor + p.subtract)
}
