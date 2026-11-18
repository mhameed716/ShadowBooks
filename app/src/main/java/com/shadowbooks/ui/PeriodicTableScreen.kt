package com.shadowbooks.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shadowbooks.audio.LabSoundManager
import com.shadowbooks.audio.LabSoundType
import com.shadowbooks.data.ChemicalElement
import com.shadowbooks.data.ElementCategory
import com.shadowbooks.data.ElementReaction
import com.shadowbooks.data.ElementVisualStyle
import com.shadowbooks.data.PeriodicTableData
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val GROUP_LABELS = listOf(
    "IA", "IIA", "IIIB", "IVB", "VB", "VIB", "VIIB", "VIIIB",
    "VIIIB", "VIIIB", "IB", "IIB", "IIIA", "IVA", "VA", "VIA", "VIIA", "VIIIA",
)

@Composable
fun PeriodicTableScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val soundManager = remember(context) { LabSoundManager(context) }

    DisposableEffect(Unit) {
        onDispose { soundManager.release() }
    }

    var isMuted by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ElementCategory?>(null) }
    var unit1Only by remember { mutableStateOf(false) }
    var isCardListMode by remember { mutableStateOf(false) }
    var tableScale by remember { mutableFloatStateOf(1f) }
    var selectedElement by remember { mutableStateOf<ChemicalElement?>(null) }

    val elements = remember { PeriodicTableData.elements }
    val normalizedQuery = remember(searchQuery) { normalizeSearchInput(searchQuery) }

    val matchingIds = remember(elements, normalizedQuery, selectedCategory, unit1Only) {
        elements.filter { el ->
            val catMatch = selectedCategory == null || el.category == selectedCategory
            val unit1Match = !unit1Only || el.isFirstTransitionSeries
            val qMatch = normalizedQuery.isBlank() ||
                el.arabicName.contains(normalizedQuery, ignoreCase = true) ||
                el.englishName.contains(normalizedQuery, ignoreCase = true) ||
                el.symbol.equals(normalizedQuery, ignoreCase = true) ||
                el.symbol.contains(normalizedQuery, ignoreCase = true) ||
                el.atomicNumber.toString() == normalizedQuery
            catMatch && unit1Match && qMatch
        }.map { it.atomicNumber }.toSet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1020))
            .systemBarsPadding(),
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F182A))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "العودة إلى مختبر الكيمياء",
                    tint = Color(0xFF38E2B5),
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("المختبر", color = Color(0xFF38E2B5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            ) {
                Text(
                    text = "الجدول الدوري التفاعلي",
                    color = Color(0xFFF4F7FC),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${matchingIds.size} / 118 عنصراً • اضغط على أي عنصر",
                    color = Color(0xFF9BAEC4),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }

            IconButton(
                onClick = {
                    isMuted = !isMuted
                    soundManager.isMuted = isMuted
                },
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = if (isMuted) "تشغيل الصوت" else "كتم الصوت",
                    tint = if (isMuted) Color(0xFF7D90A6) else Color(0xFF38E2B5),
                )
            }

            IconButton(onClick = { isCardListMode = !isCardListMode }) {
                Icon(
                    imageVector = if (isCardListMode) Icons.Filled.GridView else Icons.Filled.ViewList,
                    contentDescription = if (isCardListMode) "عرض الجدول" else "عرض القائمة",
                    tint = Color(0xFF65D4FA),
                )
            }
        }

        // Search input + quick controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(
                        text = "ابحث بالاسم، الرمز (Fe)، أو العدد الذري...",
                        color = Color(0xFF7689A0),
                        fontSize = 12.sp,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF38E2B5), modifier = Modifier.size(19.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "مسح", tint = Color(0xFF9BAEC4), modifier = Modifier.size(18.dp))
                        }
                    }
                },
                textStyle = TextStyle(color = Color(0xFFF4F7FC), fontSize = 13.sp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38E2B5),
                    unfocusedBorderColor = Color(0xFF24344D),
                    focusedContainerColor = Color(0xFF111C2E),
                    unfocusedContainerColor = Color(0xFF111C2E),
                ),
            )

            if (!isCardListMode) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = Color(0xFF111C2E),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.border(1.dp, Color(0xFF24344D), RoundedCornerShape(14.dp)),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { tableScale = (tableScale - 0.12f).coerceAtLeast(0.68f) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Filled.ZoomOut, contentDescription = "تصغير الجدول", tint = Color(0xFFB7C6D8), modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { tableScale = (tableScale + 0.12f).coerceAtMost(1.38f) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Filled.ZoomIn, contentDescription = "تكبير الجدول", tint = Color(0xFFB7C6D8), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Category & Unit 1 Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChipPill(
                    label = "الكل (118)",
                    accent = Color(0xFF38E2B5),
                    selected = selectedCategory == null && !unit1Only,
                    onClick = {
                        selectedCategory = null
                        unit1Only = false
                    },
                )
            }
            item {
                FilterChipPill(
                    label = "السلسلة 3d (الوحدة 1)",
                    accent = Color(0xFFFFC86B),
                    selected = unit1Only,
                    onClick = {
                        unit1Only = !unit1Only
                        if (unit1Only) selectedCategory = null
                    },
                )
            }
            items(ElementCategory.entries) { category ->
                FilterChipPill(
                    label = category.arabicTitle,
                    accent = category.accentColor,
                    selected = selectedCategory == category && !unit1Only,
                    onClick = {
                        unit1Only = false
                        selectedCategory = if (selectedCategory == category) null else category
                    },
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Main Content: Either Full 18-Column Periodic Table OR Cards List
        if (isCardListMode) {
            val filteredList = remember(elements, matchingIds) {
                elements.filter { it.atomicNumber in matchingIds }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filteredList, key = { it.atomicNumber }) { el ->
                    ElementListCard(
                        element = el,
                        onClick = {
                            soundManager.play(LabSoundType.SELECT)
                            selectedElement = el
                        },
                    )
                }
            }
        } else {
            PeriodicTableMatrix(
                elements = elements,
                matchingIds = matchingIds,
                scale = tableScale,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onSelectElement = { el ->
                    soundManager.play(LabSoundType.SELECT)
                    selectedElement = el
                },
            )
        }
    }

    selectedElement?.let { element ->
        ElementDetailDialog(
            element = element,
            soundManager = soundManager,
            onDismiss = { selectedElement = null },
        )
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) accent.copy(alpha = 0.22f) else Color(0xFF131D30),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(
                width = 1.dp,
                color = if (selected) accent else Color(0xFF26354E),
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (selected) accent else Color(0xFFA9BAD0),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun PeriodicTableMatrix(
    elements: List<ChemicalElement>,
    matchingIds: Set<Int>,
    scale: Float,
    modifier: Modifier = Modifier,
    onSelectElement: (ChemicalElement) -> Unit,
) {
    val byPos = remember(elements) {
        elements.associateBy { it.gridRow to it.gridCol }
    }
    val cellW: Dp = (58 * scale).dp
    val cellH: Dp = (66 * scale).dp
    val gap: Dp = (4 * scale).dp

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    // Standard IUPAC table layout is physically Left-to-Right (Group 1 left -> Group 18 right)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .background(Color(0xFF080E1B))
                .horizontalScroll(hScroll)
                .verticalScroll(vScroll)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                // Group header row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width((24 * scale).dp))
                    for (col in 1..18) {
                        Column(
                            modifier = Modifier.width(cellW),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = col.toString(),
                                color = Color(0xFF7FDFFF),
                                fontSize = (10 * scale).sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = GROUP_LABELS[col - 1],
                                color = Color(0xFF788CA4),
                                fontSize = (8 * scale).sp,
                            )
                        }
                    }
                }

                // Periods 1..7
                for (row in 1..7) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.toString(),
                            modifier = Modifier.width((24 * scale).dp),
                            color = Color(0xFF788CA4),
                            fontSize = (11 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )

                        for (col in 1..18) {
                            val el = byPos[row to col]
                            when {
                                el != null -> ElementGridCell(
                                    element = el,
                                    isMatched = el.atomicNumber in matchingIds,
                                    width = cellW,
                                    height = cellH,
                                    scale = scale,
                                    onClick = { onSelectElement(el) },
                                )
                                row == 6 && col == 3 -> SeriesPlaceholderCell(
                                    range = "57–71",
                                    label = "لانثانيدات",
                                    accent = ElementCategory.LANTHANIDE.accentColor,
                                    width = cellW,
                                    height = cellH,
                                    scale = scale,
                                )
                                row == 7 && col == 3 -> SeriesPlaceholderCell(
                                    range = "89–103",
                                    label = "أكتينيدات",
                                    accent = ElementCategory.ACTINIDE.accentColor,
                                    width = cellW,
                                    height = cellH,
                                    scale = scale,
                                )
                                else -> Spacer(Modifier.size(cellW, cellH))
                            }
                        }
                    }
                }

                Spacer(Modifier.height((10 * scale).dp))

                // Row 9: Lanthanides (57..71) & Row 10: Actinides (89..103)
                listOf(
                    Triple(9, "57–71", ElementCategory.LANTHANIDE),
                    Triple(10, "89–103", ElementCategory.ACTINIDE),
                ).forEach { (rowIdx, tag, cat) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width((24 * scale).dp))
                        // Columns 1..3 label area
                        Box(
                            modifier = Modifier
                                .width(cellW * 3 + gap * 2)
                                .height(cellH)
                                .clip(RoundedCornerShape((10 * scale).dp))
                                .background(cat.accentColor.copy(alpha = 0.08f))
                                .border(1.dp, cat.accentColor.copy(alpha = 0.3f), RoundedCornerShape((10 * scale).dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${cat.arabicTitle} ($tag)",
                                color = cat.accentColor,
                                fontSize = (11 * scale).sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        for (col in 4..18) {
                            val el = byPos[rowIdx to col]
                            if (el != null) {
                                ElementGridCell(
                                    element = el,
                                    isMatched = el.atomicNumber in matchingIds,
                                    width = cellW,
                                    height = cellH,
                                    scale = scale,
                                    onClick = { onSelectElement(el) },
                                )
                            } else {
                                Spacer(Modifier.size(cellW, cellH))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ElementGridCell(
    element: ChemicalElement,
    isMatched: Boolean,
    width: Dp,
    height: Dp,
    scale: Float,
    onClick: () -> Unit,
) {
    val accent = element.category.accentColor
    val shape = RoundedCornerShape((9 * scale).dp)

    Box(
        modifier = Modifier
            .size(width, height)
            .alpha(if (isMatched) 1f else 0.20f)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.22f),
                        Color(0xFF10192A),
                    ),
                ),
            )
            .border(
                width = if (element.isFirstTransitionSeries) (1.3 * scale).dp else (0.9 * scale).dp,
                color = accent.copy(alpha = if (isMatched) 0.72f else 0.25f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = (4 * scale).dp, vertical = (4 * scale).dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = element.atomicNumber.toString(),
                color = accent,
                fontSize = (9 * scale).sp,
                fontWeight = FontWeight.Bold,
            )
            Box(
                modifier = Modifier
                    .size((6 * scale).dp)
                    .clip(CircleShape)
                    .background(element.sampleColor)
                    .border(0.5.dp, Color.White.copy(alpha = 0.5f), CircleShape),
            )
        }

        Text(
            text = element.symbol,
            modifier = Modifier.align(Alignment.Center),
            color = Color(0xFFF6FAFF),
            fontSize = (16 * scale).sp,
            fontWeight = FontWeight.ExtraBold,
        )

        Text(
            text = element.arabicName,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = Color(0xFFD5E1F0),
            fontSize = (8 * scale).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SeriesPlaceholderCell(
    range: String,
    label: String,
    accent: Color,
    width: Dp,
    height: Dp,
    scale: Float,
) {
    val shape = RoundedCornerShape((9 * scale).dp)
    Column(
        modifier = Modifier
            .size(width, height)
            .clip(shape)
            .background(accent.copy(alpha = 0.09f))
            .border(0.8.dp, accent.copy(alpha = 0.4f), shape)
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(range, color = accent, fontSize = (9 * scale).sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFFBACAE0), fontSize = (7 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ElementListCard(
    element: ChemicalElement,
    onClick: () -> Unit,
) {
    val accent = element.category.accentColor
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF111B2E))
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.16f))
                .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(element.atomicNumber.toString(), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(element.symbol, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(element.arabicName, color = Color(0xFFF4F7FC), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(element.sampleColor)
                        .border(0.6.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${element.category.arabicTitle} • ${element.electronConfig}",
                color = Color(0xFF9BAEC4),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = element.atomicMass,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ElementDetailDialog(
    element: ChemicalElement,
    soundManager: LabSoundManager,
    onDismiss: () -> Unit,
) {
    val accent = element.category.accentColor
    var activeReaction by remember(element.atomicNumber) { mutableStateOf<ElementReaction?>(null) }
    var reactionRunKey by remember(element.atomicNumber) { mutableIntStateOf(0) }

    LaunchedEffect(reactionRunKey) {
        if (reactionRunKey > 0) {
            delay(2300)
            activeReaction = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val shape = RoundedCornerShape(24.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.90f)
                    .shadow(24.dp, shape)
                    .clip(shape)
                    .background(Color(0xFF0C1526))
                    .border(1.2.dp, accent.copy(alpha = 0.55f), shape),
            ) {
                // Dialog Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF121E34))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${element.arabicName} (${element.symbol})",
                            color = Color(0xFFF6FAFF),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "${element.englishName} • ${element.category.arabicTitle}",
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = Color(0xFFB7C6D8))
                    }
                }

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // 1. Real physical appearance & color showcase
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF111D32))
                            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ElementRealSampleCanvas(
                            element = element,
                            modifier = Modifier.size(104.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    color = accent.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text(
                                        text = "الحالة: ${element.state.arabicLabel}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        color = accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(element.sampleColor)
                                        .border(1.dp, Color.White.copy(alpha = 0.65f), CircleShape),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "الشكل الحقيقي واللون:",
                                color = Color(0xFFF3F7FC),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = element.appearanceArabic,
                                color = Color(0xFFBACAE0),
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }

                    // 2. Atomic Numbers Grid (Z, A, Mass, Neutrons)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AtomicStatBox(
                            label = "العدد الذري (Z)",
                            value = element.atomicNumber.toString(),
                            accent = Color(0xFF65D4FA),
                            modifier = Modifier.weight(1f),
                        )
                        AtomicStatBox(
                            label = "العدد الكتلي (A)",
                            value = element.massNumber.toString(),
                            accent = Color(0xFFFFC86B),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AtomicStatBox(
                            label = "الكتلة الذرية",
                            value = element.atomicMass,
                            accent = Color(0xFF38E2B5),
                            modifier = Modifier.weight(1f),
                        )
                        AtomicStatBox(
                            label = "النيوترونات / البروتونات",
                            value = "${element.neutronCount}n • ${element.atomicNumber}p",
                            accent = Color(0xFFB388FF),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // 3. Electron Configuration + Animated Bohr Model
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF111D32))
                            .border(1.dp, Color(0xFF253752), RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "التوزيع الإلكتروني ونموذج الذرة",
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFF3F7FC),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(10.dp))

                        BohrAtomCanvas(
                            element = element,
                            modifier = Modifier.size(152.dp),
                        )

                        Spacer(Modifier.height(10.dp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0A1120))
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = element.electronConfig,
                                    color = accent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                if (element.fullElectronConfig != element.electronConfig) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = element.fullElectronConfig,
                                        color = Color(0xFF9FB2C9),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        val shellNames = listOf("K", "L", "M", "N", "O", "P", "Q")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            element.shells.forEachIndexed { idx, count ->
                                val shellLabel = shellNames.getOrElse(idx) { "n${idx + 1}" }
                                Surface(
                                    color = accent.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text(
                                        text = "$shellLabel = $count",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        color = Color(0xFFE6F0FA),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }

                    // 4. Scientific Explanation
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF111D32))
                            .border(1.dp, Color(0xFF253752), RoundedCornerShape(18.dp))
                            .padding(14.dp),
                    ) {
                        Text(
                            text = "نبذة وشرح عن العنصر",
                            color = Color(0xFFF3F7FC),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = element.summaryArabic,
                            color = Color(0xFFC3D3E6),
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }

                    // 5. Live Reaction Visual Banner (when user plays a reaction)
                    AnimatedVisibility(visible = activeReaction != null) {
                        activeReaction?.let { rx ->
                            LiveReactionSimulationCard(reaction = rx)
                        }
                    }

                    // 6. Chemical Reactions Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF111D32))
                            .border(1.dp, Color(0xFF253752), RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Science, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "ماذا يدخل من تفاعلات",
                                color = Color(0xFFF3F7FC),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        element.reactions.forEach { reaction ->
                            ReactionCardItem(
                                reaction = reaction,
                                onSimulate = {
                                    soundManager.play(reaction.soundType)
                                    activeReaction = reaction
                                    reactionRunKey++
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AtomicStatBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF111D32))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Color(0xFF94A8C0), fontSize = 11.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ElementRealSampleCanvas(
    element: ChemicalElement,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "sample")
    val anim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "sample_anim",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF09101E))
            .border(1.dp, element.sampleColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val c1 = element.sampleColor
            val c2 = element.sampleSecondaryColor

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c1.copy(alpha = 0.24f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.6f,
                ),
                center = center,
                radius = size.minDimension * 0.6f,
            )

            when (element.visualStyle) {
                ElementVisualStyle.METALLIC_INGOT -> {
                    // 3D beveled metallic ingot/bar with specular sheen
                    val topFace = Path().apply {
                        moveTo(w * 0.20f, h * 0.38f)
                        lineTo(w * 0.46f, h * 0.22f)
                        lineTo(w * 0.84f, h * 0.32f)
                        lineTo(w * 0.58f, h * 0.50f)
                        close()
                    }
                    val leftFace = Path().apply {
                        moveTo(w * 0.20f, h * 0.38f)
                        lineTo(w * 0.58f, h * 0.50f)
                        lineTo(w * 0.58f, h * 0.78f)
                        lineTo(w * 0.20f, h * 0.64f)
                        close()
                    }
                    val rightFace = Path().apply {
                        moveTo(w * 0.58f, h * 0.50f)
                        lineTo(w * 0.84f, h * 0.32f)
                        lineTo(w * 0.84f, h * 0.60f)
                        lineTo(w * 0.58f, h * 0.78f)
                        close()
                    }
                    drawPath(topFace, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f), c1)))
                    drawPath(leftFace, Brush.verticalGradient(listOf(c1, c2)))
                    drawPath(rightFace, Brush.verticalGradient(listOf(c1.copy(alpha = 0.75f), c2)))
                    drawPath(topFace, Color.White.copy(alpha = 0.45f), style = Stroke(1.2.dp.toPx()))
                    drawPath(leftFace, Color.White.copy(alpha = 0.25f), style = Stroke(1.dp.toPx()))
                    drawPath(rightFace, Color.White.copy(alpha = 0.25f), style = Stroke(1.dp.toPx()))
                }

                ElementVisualStyle.CRYSTAL_MINERAL -> {
                    // Multi-faceted crystalline mineral cluster
                    val mainCrystal = Path().apply {
                        moveTo(w * 0.50f, h * 0.14f)
                        lineTo(w * 0.76f, h * 0.42f)
                        lineTo(w * 0.68f, h * 0.82f)
                        lineTo(w * 0.32f, h * 0.82f)
                        lineTo(w * 0.24f, h * 0.42f)
                        close()
                    }
                    val facet = Path().apply {
                        moveTo(w * 0.50f, h * 0.14f)
                        lineTo(w * 0.52f, h * 0.56f)
                        lineTo(w * 0.68f, h * 0.82f)
                        lineTo(w * 0.76f, h * 0.42f)
                        close()
                    }
                    drawPath(mainCrystal, Brush.verticalGradient(listOf(c1, c2)))
                    drawPath(facet, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.45f), c1.copy(alpha = 0.3f))))
                    drawPath(mainCrystal, Color.White.copy(alpha = 0.5f), style = Stroke(1.3.dp.toPx()))
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(w * 0.50f, h * 0.14f), Offset(w * 0.52f, h * 0.56f), strokeWidth = 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.35f), Offset(w * 0.24f, h * 0.42f), Offset(w * 0.52f, h * 0.56f), strokeWidth = 1.dp.toPx())
                }

                ElementVisualStyle.LIQUID_VIAL -> {
                    // Glass ampoule/vial with reflective liquid meniscus
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.10f),
                        topLeft = Offset(w * 0.31f, h * 0.16f),
                        size = Size(w * 0.38f, h * 0.68f),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(c1, c2)),
                        topLeft = Offset(w * 0.33f, h * 0.44f),
                        size = Size(w * 0.34f, h * 0.38f),
                        cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx()),
                    )
                    // Meniscus highlight
                    drawOval(
                        color = Color.White.copy(alpha = 0.42f),
                        topLeft = Offset(w * 0.33f, h * 0.41f),
                        size = Size(w * 0.34f, h * 0.08f),
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.55f),
                        topLeft = Offset(w * 0.31f, h * 0.16f),
                        size = Size(w * 0.38f, h * 0.68f),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                        style = Stroke(1.4.dp.toPx()),
                    )
                }

                ElementVisualStyle.GLOWING_GAS -> {
                    // Sealed glowing gas discharge ampoule with rising particles
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(c1.copy(alpha = 0.28f), c2.copy(alpha = 0.52f))),
                        topLeft = Offset(w * 0.28f, h * 0.14f),
                        size = Size(w * 0.44f, h * 0.72f),
                        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                    )
                    drawRoundRect(
                        color = c1.copy(alpha = 0.85f),
                        topLeft = Offset(w * 0.28f, h * 0.14f),
                        size = Size(w * 0.44f, h * 0.72f),
                        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                        style = Stroke(1.6.dp.toPx()),
                    )
                    for (i in 0..5) {
                        val py = h * 0.74f - ((anim + i * 0.17f) % 1f) * h * 0.50f
                        val px = w * (0.38f + ((i * 37) % 24) / 100f)
                        drawCircle(Color.White.copy(alpha = 0.8f), radius = 2.4.dp.toPx(), center = Offset(px, py))
                    }
                }

                ElementVisualStyle.RADIOACTIVE_CORE -> {
                    // Glowing core with pulsing radiation rings
                    val pulseR = w * (0.28f + 0.10f * sin(anim * 2f * PI.toFloat()))
                    drawCircle(c1.copy(alpha = 0.25f), radius = pulseR, center = center, style = Stroke(2.dp.toPx()))
                    drawCircle(c1.copy(alpha = 0.45f), radius = w * 0.22f, center = center, style = Stroke(1.4.dp.toPx()))
                    drawCircle(
                        brush = Brush.radialGradient(listOf(Color.White, c1, c2)),
                        radius = w * 0.16f,
                        center = center,
                    )
                }
            }
        }
    }
}

@Composable
private fun BohrAtomCanvas(
    element: ChemicalElement,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "bohr")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "bohr_angle",
    )
    val accent = element.category.accentColor

    Canvas(modifier = modifier) {
        val shellCount = element.shells.size.coerceAtLeast(1)
        val maxR = size.minDimension * 0.46f
        val minR = size.minDimension * 0.15f
        val step = if (shellCount > 1) (maxR - minR) / (shellCount - 1) else 0f

        // Nucleus
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White, accent)),
            radius = size.minDimension * 0.085f,
            center = center,
        )

        element.shells.forEachIndexed { shellIdx, electrons ->
            val r = minR + shellIdx * step
            drawCircle(
                color = accent.copy(alpha = 0.30f),
                radius = r,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            val speedFactor = if (shellIdx % 2 == 0) 1f else -0.8f
            val baseDeg = angle * speedFactor + shellIdx * 35f
            for (e in 0 until electrons) {
                val deg = baseDeg + e * (360f / electrons)
                val rad = deg * PI.toFloat() / 180f
                val ex = center.x + r * cos(rad)
                val ey = center.y + r * sin(rad)
                drawCircle(
                    color = Color(0xFF7BE4FF),
                    radius = 2.6.dp.toPx(),
                    center = Offset(ex, ey),
                )
            }
        }
    }
}

@Composable
private fun ReactionCardItem(
    reaction: ElementReaction,
    onSimulate: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF0B1322))
            .border(1.dp, reaction.effectColor.copy(alpha = 0.35f), shape)
            .padding(12.dp),
    ) {
        Text(
            text = reaction.title,
            color = reaction.effectColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF142038))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = reaction.equation,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFF6FAFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = reaction.effectSummary,
            color = Color(0xFFB5C7DC),
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onSimulate,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = reaction.effectColor.copy(alpha = 0.22f),
                contentColor = reaction.effectColor,
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text("تشغيل التفاعل بالصوت", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LiveReactionSimulationCard(reaction: ElementReaction) {
    val transition = rememberInfiniteTransition(label = "rx_sim")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "rx_phase",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(reaction.effectColor.copy(alpha = 0.14f))
            .border(1.2.dp, reaction.effectColor.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(56.dp)) {
            val w = size.width
            val h = size.height
            // Mini animated test tube
            drawRoundRect(
                color = Color.White.copy(alpha = 0.14f),
                topLeft = Offset(w * 0.30f, h * 0.12f),
                size = Size(w * 0.40f, h * 0.76f),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            )
            drawRoundRect(
                color = reaction.effectColor.copy(alpha = 0.65f),
                topLeft = Offset(w * 0.32f, h * 0.45f),
                size = Size(w * 0.36f, h * 0.40f),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
            )
            for (i in 0..4) {
                val y = h * 0.75f - ((phase + i * 0.21f) % 1f) * h * 0.52f
                val x = w * (0.40f + (i % 3) * 0.09f)
                drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.5.dp.toPx(), center = Offset(x, y))
            }
            drawRoundRect(
                color = Color.White.copy(alpha = 0.65f),
                topLeft = Offset(w * 0.30f, h * 0.12f),
                size = Size(w * 0.40f, h * 0.76f),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "جارٍ محاكاة: ${reaction.title}",
                color = reaction.effectColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = reaction.effectSummary,
                color = Color(0xFFEAF3FC),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

private fun normalizeSearchInput(raw: String): String {
    val arabicDigits = "٠١٢٣٤٥٦٧٨٩"
    val persianDigits = "۰۱۲۳۴۵۶۷۸۹"
    return buildString {
        raw.trim().forEach { ch ->
            val ai = arabicDigits.indexOf(ch)
            val pi = persianDigits.indexOf(ch)
            when {
                ai >= 0 -> append(('0'.code + ai).toChar())
                pi >= 0 -> append(('0'.code + pi).toChar())
                else -> append(ch)
            }
        }
    }
}
