package com.shadowbooks.data

import androidx.compose.ui.graphics.Color
import com.shadowbooks.audio.LabSoundType

enum class ElementCategory(
    val arabicTitle: String,
    val accentColor: Color,
) {
    NONMETAL("لافلز تفاعلي", Color(0xFF4CD995)),
    NOBLE_GAS("غاز نبيل", Color(0xFFB388FF)),
    ALKALI_METAL("فلز قلوي", Color(0xFFFF6E6E)),
    ALKALINE_EARTH("فلز قلوي أرضي", Color(0xFFFFB258)),
    METALLOID("شبه فلز", Color(0xFF38E2B5)),
    HALOGEN("هالوجين", Color(0xFF65D4FA)),
    POST_TRANSITION("فلز بعد انتقالي", Color(0xFF7FA8D9)),
    TRANSITION_METAL("عنصر انتقالي", Color(0xFFFFC86B)),
    LANTHANIDE("لانثانيد", Color(0xFFF48FB1)),
    ACTINIDE("أكتينيد", Color(0xFFFF8A65)),
}

enum class MatterState(val arabicLabel: String) {
    SOLID("صلب"),
    LIQUID("سائل"),
    GAS("غاز"),
}

enum class ElementVisualStyle {
    METALLIC_INGOT,
    CRYSTAL_MINERAL,
    LIQUID_VIAL,
    GLOWING_GAS,
    RADIOACTIVE_CORE,
}

data class ElementReaction(
    val title: String,
    val equation: String,
    val effectSummary: String,
    val soundType: LabSoundType,
    val effectColor: Color,
)

data class ChemicalElement(
    val atomicNumber: Int,
    val symbol: String,
    val arabicName: String,
    val englishName: String,
    val massNumber: Int,
    val atomicMass: String,
    val electronConfig: String,
    val fullElectronConfig: String,
    val shells: List<Int>,
    val category: ElementCategory,
    val gridRow: Int,
    val gridCol: Int,
    val state: MatterState,
    val visualStyle: ElementVisualStyle,
    val sampleColor: Color,
    val sampleSecondaryColor: Color,
    val appearanceArabic: String,
    val summaryArabic: String,
    val reactions: List<ElementReaction>,
) {
    val neutronCount: Int
        get() = (massNumber - atomicNumber).coerceAtLeast(0)

    val isFirstTransitionSeries: Boolean
        get() = atomicNumber in 21..30
}
