package com.shadowbooks.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.shadowbooks.R
import com.shadowbooks.data.BellSchedule
import com.shadowbooks.data.SchoolWeek
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val ScheduleNight = Color(0xFF071326)
private val ScheduleSurface = Color(0xFF101E32)
private val ScheduleLine = Color(0xFF30445D)
private val ScheduleCyan = Color(0xFF65D4FA)
private val ScheduleGold = Color(0xFFFFC86B)
private val ScheduleGreen = Color(0xFF65E1B0)
private val ScheduleRose = Color(0xFFFF9CA8)
private val ScheduleFont = FontFamily(Font(R.font.tajawal_regular), Font(R.font.tajawal_extrabold, FontWeight.ExtraBold))
private val Weekdays = SchoolWeek.days
private val PeriodNames = listOf("الأولى", "الثانية", "الثالثة", "الرابعة", "الخامسة", "السادسة")
private const val PreferenceFile = "shadowbooks_timetable"
private const val DayWidth = 54
private const val RowHeight = 64

private object TimetablePreferences {
    fun open(context: Context) = context.getSharedPreferences(PreferenceFile, Context.MODE_PRIVATE)

    fun schedule(prefs: SharedPreferences): BellSchedule = BellSchedule(
        firstStartMinute = prefs.getInt("first_start", 7 * 60),
        lessonMinutes = prefs.getInt("lesson_minutes", 45),
        passingMinutes = prefs.getInt("passing_minutes", 5),
    )

    fun saveSchedule(prefs: SharedPreferences, value: BellSchedule) {
        prefs.edit()
            .putInt("first_start", value.firstStartMinute)
            .putInt("lesson_minutes", value.lessonMinutes)
            .putInt("passing_minutes", value.passingMinutes)
            .apply()
    }

    fun subject(prefs: SharedPreferences, day: Int, period: Int) =
        prefs.getString("subject-$day-$period", "").orEmpty()

    fun saveSubject(prefs: SharedPreferences, day: Int, period: Int, value: String) {
        val editor = prefs.edit()
        val key = "subject-$day-$period"
        if (value.isBlank()) editor.remove(key) else editor.putString(key, value.trim())
        editor.apply()
    }

    fun completion(prefs: SharedPreferences, date: String, day: Int, period: Int) =
        prefs.getString("done-$date-$day-$period", null)

    fun saveCompletion(prefs: SharedPreferences, date: String, day: Int, period: Int, value: String) {
        prefs.edit().putString("done-$date-$day-$period", value).apply()
    }

    fun clearCompletion(prefs: SharedPreferences, date: String, day: Int, period: Int) {
        prefs.edit().remove("done-$date-$day-$period").apply()
    }
}

@Composable
fun TimetableScreen(onLibrary: () -> Unit, onStudyResources: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { TimetablePreferences.open(context) }
    var schedule by remember(preferences) { mutableStateOf(TimetablePreferences.schedule(preferences)) }
    var subjects by remember(preferences) {
        mutableStateOf(List(Weekdays.size) { day -> List(BellSchedule.PERIOD_COUNT) { period ->
            TimetablePreferences.subject(preferences, day, period)
        } })
    }
    var clockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                clockMillis = System.currentTimeMillis()
                delay(1000L - (clockMillis % 1000L))
            }
        }
    }
    val now = remember(clockMillis / 1000L) { Date(clockMillis) }
    val clockText = remember(clockMillis / 1000L) { SimpleDateFormat("HH:mm:ss", Locale.US).format(now) }
    val dateText = remember(clockMillis / 60_000L) {
        SimpleDateFormat("EEEE، d MMMM", Locale.forLanguageTag("ar-YE")).format(now)
    }
    val calendar = remember(clockMillis / 60_000L) { Calendar.getInstance().apply { timeInMillis = clockMillis } }
    val currentDay = SchoolWeek.indexForCalendarDay(calendar.get(Calendar.DAY_OF_WEEK))
    val minuteNow = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    val activePeriod = schedule.currentPeriod(minuteNow)
    val detailsPeriod = schedule.relevantPeriod(minuteNow)
    val dateKey = remember(clockMillis / 60_000L) { SimpleDateFormat("yyyyMMdd", Locale.US).format(now) }

    var detailsOpen by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var editCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editSubject by remember { mutableStateOf("") }
    var completionCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var completionRevision by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1930), ScheduleNight, Color(0xFF050D18))))
            .safeDrawingPadding(),
    ) {
        ScheduleHeader(
            clockText = clockText,
            dateText = dateText,
            onDetails = { detailsOpen = !detailsOpen },
            detailsOpen = detailsOpen,
            schedule = schedule,
            detailsPeriod = detailsPeriod,
            activePeriod = activePeriod,
            onSettings = { detailsOpen = false; settingsOpen = true },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Filled.School, contentDescription = null, tint = ScheduleCyan, modifier = Modifier.size(15.dp))
            Text(
                "اضغط على أي مربع لكتابة اسم الحصة أو تعديله",
                color = Color(0xFFAFBED0), fontFamily = ScheduleFont, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 9.dp),
            contentPadding = PaddingValues(top = 5.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item(key = "period-heading") { PeriodHeading() }
            itemsIndexed(Weekdays, key = { index, _ -> index }) { day, label ->
                ScheduleDayRow(
                    day = day,
                    label = label,
                    subjects = subjects[day],
                    currentDay = currentDay == day,
                    activePeriod = if (currentDay == day) activePeriod else null,
                    minuteNow = minuteNow,
                    dateKey = dateKey,
                    schedule = schedule,
                    preferences = preferences,
                    completionRevision = completionRevision,
                    onCellClick = { period ->
                        val subject = subjects[day][period]
                        val endedToday = currentDay == day && minuteNow >= schedule.window(period).endMinute
                        if (subject.isNotBlank() && endedToday) {
                            completionCell = day to period
                        } else {
                            editSubject = subject
                            editCell = day to period
                        }
                    },
                )
            }
        }
        HomeBottomNavigation(
            selected = HomeTab.Timetable,
            onLibrary = onLibrary,
            onStudyResources = onStudyResources,
            onTimetable = {},
        )
    }

    editCell?.let { (day, period) ->
        AlertDialog(
            onDismissRequest = { editCell = null },
            containerColor = Color(0xFF13233A),
            title = { Text("${Weekdays[day]} · الحصة ${PeriodNames[period]}", color = Color.White, fontFamily = ScheduleFont, fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(
                    value = editSubject,
                    onValueChange = { if (it.length <= 40) editSubject = it },
                    singleLine = true,
                    label = { Text("اسم الحصة", fontFamily = ScheduleFont) },
                    placeholder = { Text("مثال: رياضيات", fontFamily = ScheduleFont) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val clean = editSubject.trim().take(40)
                    TimetablePreferences.saveSubject(preferences, day, period, clean)
                    val updated = subjects.mapIndexed { d, row ->
                        if (d != day) row else row.mapIndexed { p, value -> if (p == period) clean else value }
                    }
                    subjects = updated
                    if (currentDay == day) TimetablePreferences.clearCompletion(preferences, dateKey, day, period)
                    completionRevision++
                    editCell = null
                }) { Text("حفظ", color = ScheduleCyan, fontFamily = ScheduleFont) }
            },
            dismissButton = {
                Row {
                    if (editSubject.isNotBlank()) TextButton(onClick = {
                        editSubject = ""
                    }) { Text("مسح", color = ScheduleRose, fontFamily = ScheduleFont) }
                    TextButton(onClick = { editCell = null }) { Text("إلغاء", color = Color(0xFFB7C3D2), fontFamily = ScheduleFont) }
                }
            },
        )
    }

    completionCell?.let { (day, period) ->
        val subject = subjects[day][period]
        val existing = remember(completionRevision, dateKey, day, period) {
            TimetablePreferences.completion(preferences, dateKey, day, period)
        }
        AlertDialog(
            onDismissRequest = { completionCell = null },
            containerColor = Color(0xFF13233A),
            icon = { Icon(Icons.Filled.Check, contentDescription = null, tint = ScheduleGold) },
            title = { Text("انتهت الحصة", color = Color.White, fontFamily = ScheduleFont, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(subject, color = ScheduleCyan, fontFamily = ScheduleFont, fontWeight = FontWeight.Bold)
                    Text("هل درست هذه الحصة؟", color = Color(0xFFD5DFEA), fontFamily = ScheduleFont)
                    if (existing != null) Text("يمكنك تغيير اختيارك.", color = Color(0xFF8D9DB1), fontFamily = ScheduleFont, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        TimetablePreferences.saveCompletion(preferences, dateKey, day, period, "studied")
                        completionRevision++
                        completionCell = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17443E), contentColor = ScheduleGreen),
                ) { Text("درستها", fontFamily = ScheduleFont, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    OutlinedButton(
                        onClick = {
                            TimetablePreferences.saveCompletion(preferences, dateKey, day, period, "missed")
                            completionRevision++
                            completionCell = null
                        },
                    ) { Text("لم أدرسها", color = ScheduleRose, fontFamily = ScheduleFont) }
                    TextButton(onClick = { completionCell = null }) { Text("إلغاء", color = Color(0xFFB7C3D2), fontFamily = ScheduleFont) }
                }
            },
        )
    }

    if (settingsOpen) {
        BellScheduleDialog(
            initial = schedule,
            onDismiss = { settingsOpen = false },
            onSave = { updated ->
                TimetablePreferences.saveSchedule(preferences, updated)
                schedule = updated
                settingsOpen = false
            },
        )
    }
}

@Composable
private fun ScheduleHeader(
    clockText: String,
    dateText: String,
    onDetails: () -> Unit,
    detailsOpen: Boolean,
    schedule: BellSchedule,
    detailsPeriod: Int,
    activePeriod: Int?,
    onSettings: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(146.dp)) {
        ImageLogo(Modifier.align(Alignment.TopStart).padding(top = 11.dp, start = 14.dp))
        Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = onDetails, modifier = Modifier.padding(start = 3.dp, top = 1.dp)) {
                Icon(Icons.Filled.AccessTime, contentDescription = "بيانات الحصص", tint = ScheduleCyan, modifier = Modifier.size(24.dp))
            }
            DropdownMenu(
                expanded = detailsOpen,
                onDismissRequest = onDetails,
                modifier = Modifier.width(278.dp),
                properties = PopupProperties(focusable = true),
            ) {
                val window = schedule.window(detailsPeriod)
                Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (activePeriod == detailsPeriod) "الحصة الجارية الآن" else "بيانات الحصة ${PeriodNames[detailsPeriod]}",
                        color = Color.White, fontFamily = ScheduleFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                    )
                    Text("تبدأ: ${BellSchedule.formatClock(window.startMinute)}", color = Color(0xFFD5DFEA), fontFamily = ScheduleFont, fontSize = 13.sp)
                    Text("مدة الحصة: ${schedule.lessonMinutes.arabicDigits()} دقيقة", color = Color(0xFFD5DFEA), fontFamily = ScheduleFont, fontSize = 13.sp)
                    Text("تنتهي: ${BellSchedule.formatClock(window.endMinute)}", color = ScheduleGold, fontFamily = ScheduleFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        window.nextStartMinute?.let { "الحصة التالية تبدأ: ${BellSchedule.formatClock(it)}" } ?: "هذه آخر حصة في اليوم",
                        color = ScheduleCyan, fontFamily = ScheduleFont, fontSize = 12.sp,
                    )
                    TextButton(onClick = onSettings) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = ScheduleCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("تعديل أوقات الدوام", color = ScheduleCyan, fontFamily = ScheduleFont)
                    }
                }
            }
        }
        Column(
            Modifier.align(Alignment.Center).padding(top = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("جدول الحصص", color = Color.White, fontFamily = ScheduleFont, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            Spacer(Modifier.height(2.dp))
            Text(clockText, color = ScheduleCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = 1.4.sp)
            Text(dateText, color = Color(0xFFB8C8DA), fontFamily = ScheduleFont, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ImageLogo(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.library_wordmark),
        contentDescription = "SHADOWBOOKS",
        modifier = modifier.width(147.dp).height(29.dp),
    )
}

@Composable
private fun PeriodHeading() {
    Row(
        Modifier.fillMaxWidth().height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(DayWidth.dp).height(35.dp).clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF11263C)).border(1.dp, ScheduleLine, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("اليوم", color = Color(0xFF98ADC4), fontFamily = ScheduleFont, fontSize = 10.sp)
        }
        PeriodNames.forEachIndexed { index, name ->
            Box(
                Modifier.weight(1f).height(35.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF12253A)).border(1.dp, if (index == 0) ScheduleCyan.copy(alpha = .55f) else ScheduleLine, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(name, color = if (index == 0) ScheduleCyan else Color(0xFFD0DCE8), fontFamily = ScheduleFont, fontWeight = FontWeight.Bold, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 11.sp)
            }
        }
    }
}

@Composable
private fun ScheduleDayRow(
    day: Int,
    label: String,
    subjects: List<String>,
    currentDay: Boolean,
    activePeriod: Int?,
    minuteNow: Int,
    dateKey: String,
    schedule: BellSchedule,
    preferences: SharedPreferences,
    completionRevision: Int,
    onCellClick: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(RowHeight.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.width(DayWidth.dp).height(RowHeight.dp).clip(RoundedCornerShape(9.dp))
                .background(if (currentDay) Color(0xFF123C55) else Color(0xFF122238))
                .border(1.dp, if (currentDay) ScheduleCyan.copy(alpha = .75f) else ScheduleLine, RoundedCornerShape(9.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, color = if (currentDay) ScheduleCyan else Color(0xFFCFD9E5), fontFamily = ScheduleFont, fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 12.sp)
            if (currentDay) Text("اليوم", color = ScheduleCyan, fontFamily = ScheduleFont, fontSize = 8.sp)
        }
        subjects.forEachIndexed { period, subject ->
            val window = schedule.window(period)
            val ended = currentDay && subject.isNotBlank() && window.endMinute <= minuteNow
            val isActive = currentDay && activePeriod == period
            val completion = remember(completionRevision, dateKey, day, period, schedule) {
                if (ended) TimetablePreferences.completion(preferences, dateKey, day, period) else null
            }
            val background = when (completion) {
                "studied" -> Color(0xFF123D39)
                "missed" -> Color(0xFF432B38)
                else -> if (ended) Color(0xFF493D28) else if (isActive) Color(0xFF10445B) else ScheduleSurface
            }
            val outline = when (completion) {
                "studied" -> ScheduleGreen
                "missed" -> ScheduleRose
                else -> if (ended) ScheduleGold else if (isActive) ScheduleCyan else ScheduleLine
            }
            val statusText = when (completion) {
                "studied" -> "درستها"
                "missed" -> "لم أدرس"
                else -> if (ended) "انتهت" else if (isActive) "الآن" else ""
            }
            Surface(
                modifier = Modifier.weight(1f).height(RowHeight.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(role = Role.Button, onClick = { onCellClick(period) }),
                shape = RoundedCornerShape(9.dp),
                color = background,
                border = androidx.compose.foundation.BorderStroke(if (isActive || completion != null) 1.4.dp else 0.8.dp, outline.copy(alpha = if (subject.isBlank()) .60f else .95f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (subject.isBlank()) {
                        Icon(Icons.Filled.Add, contentDescription = "إضافة حصة ${PeriodNames[period]} يوم $label", tint = Color(0xFF7890AA), modifier = Modifier.size(17.dp))
                    } else {
                        Text(subject, color = Color(0xFFF1F6FC), fontFamily = ScheduleFont, fontWeight = if (isActive || completion != null) FontWeight.Bold else FontWeight.Medium, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                    if (statusText.isNotEmpty()) {
                        Text(statusText, color = outline, fontFamily = ScheduleFont, fontWeight = FontWeight.Bold, fontSize = 7.sp, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun BellScheduleDialog(initial: BellSchedule, onDismiss: () -> Unit, onSave: (BellSchedule) -> Unit) {
    var startText by remember(initial) { mutableStateOf(BellSchedule.formatClock(initial.firstStartMinute)) }
    var lessonText by remember(initial) { mutableStateOf(initial.lessonMinutes.toString()) }
    var passingText by remember(initial) { mutableStateOf(initial.passingMinutes.toString()) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF13233A),
        icon = { Icon(Icons.Filled.Schedule, contentDescription = null, tint = ScheduleCyan) },
        title = { Text("أوقات الحصص", color = Color.White, fontFamily = ScheduleFont, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(startText, { startText = it.take(5); invalid = false }, label = { Text("بداية الحصة الأولى · ساعة:دقيقة", fontFamily = ScheduleFont) }, singleLine = true, keyboardOptions = KeyboardOptions.Default)
                OutlinedTextField(lessonText, { lessonText = it.filter(Char::isDigit).take(3); invalid = false }, label = { Text("مدة الحصة بالدقائق", fontFamily = ScheduleFont) }, singleLine = true, keyboardOptions = KeyboardOptions.Default)
                OutlinedTextField(passingText, { passingText = it.filter(Char::isDigit).take(3); invalid = false }, label = { Text("الاستراحة بين الحصص بالدقائق", fontFamily = ScheduleFont) }, singleLine = true, keyboardOptions = KeyboardOptions.Default)
                if (invalid) Text("تحقق من الساعة ومن مدة الحصة والاستراحة.", color = ScheduleRose, fontFamily = ScheduleFont, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = BellSchedule.parseClock(startText)
                val lesson = lessonText.toIntOrNull()
                val passing = passingText.toIntOrNull()
                if (start == null || lesson == null || lesson !in 20..180 || passing == null || passing !in 0..120 ||
                    start + 5 * (lesson + passing) + lesson >= 1440
                ) {
                    invalid = true
                } else {
                    onSave(BellSchedule(start, lesson, passing))
                }
            }) { Text("حفظ", color = ScheduleCyan, fontFamily = ScheduleFont) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = Color(0xFFB7C3D2), fontFamily = ScheduleFont) } },
    )
}

private fun Int.arabicDigits(): String = toString().map { ('٠'.code + it.digitToInt()).toChar() }.joinToString("")
