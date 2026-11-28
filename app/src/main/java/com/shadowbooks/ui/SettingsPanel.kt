package com.shadowbooks.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.R
import com.shadowbooks.reminder.StudyReminder
import com.shadowbooks.store.TrashBin
import com.shadowbooks.store.TrashItem
import java.util.Locale

private val SettingsCard = Color(0xFF13233A)
private val SettingsCyan = Color(0xFF65D4FA)
private val SettingsMuted = Color(0xFF9DADC0)
private val SettingsDanger = Color(0xFFFF9D9D)
private val SettingsFont = FontFamily(
    Font(R.font.tajawal_regular),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
)

/**
 * The settings gear: it turns for three seconds, rests for a beat, then
 * turns again — a small living sign that settings live here, on every screen.
 */
@Composable
fun SpinningGearButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "settings-gear")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                0f at 0 with LinearEasing
                360f at 3000 with LinearEasing
                360f at 4000
            },
        ),
        label = "settings-gear-angle",
    )
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.Filled.Settings,
            contentDescription = "الإعدادات",
            tint = SettingsCyan,
                modifier = Modifier.size(22.dp).rotate(angle),
        )
    }
}

/** The settings popup. Its first citizen is the trash bin (سلة المحذوفات). */
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showTrash by remember { mutableStateOf(false) }
    var reminderOn by remember { mutableStateOf(StudyReminder.isEnabled(context)) }
    var reminderHour by remember { mutableStateOf(StudyReminder.hour(context)) }
    var reminderMinute by remember { mutableStateOf(StudyReminder.minute(context)) }
    val trashVersion = TrashBin.version
    val trashCount = remember(trashVersion) { TrashBin.count(context) }
    val trashBytes = remember(trashVersion) { TrashBin.totalBytes(context) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(18.dp))
                .background(SettingsCard)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                "الإعدادات",
                color = Color.White,
                fontFamily = SettingsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
            Spacer(Modifier.height(6.dp))
            SettingsRow(
                title = "سلة المحذوفات",
                detail = when {
                    trashCount == 0 -> "فارغة"
                    else -> "$trashCount ملف · ${formatSettingsSize(trashBytes)}"
                },
            ) {
                showTrash = true
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "تذكير المذاكرة اليومي",
                        color = Color.White,
                        fontFamily = SettingsFont,
                        fontSize = 14.sp,
                    )
                    Text(
                        if (reminderOn) {
                            "الساعة " + StudyReminder.formatTime(reminderHour, reminderMinute)
                        } else {
                            "متوقف"
                        },
                        color = SettingsMuted,
                        fontFamily = SettingsFont,
                        fontSize = 11.sp,
                    )
                }
                if (reminderOn) {
                    TextButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                reminderHour = h
                                reminderMinute = m
                                StudyReminder.setTime(context, h, m)
                            },
                            reminderHour,
                            reminderMinute,
                            false, // 12-hour clock (ص/م)
                        ).show()
                    }) {
                        Text("الوقت", color = SettingsCyan, fontFamily = SettingsFont, fontSize = 13.sp)
                    }
                }
                Switch(
                    checked = reminderOn,
                    onCheckedChange = { enabled ->
                        reminderOn = enabled
                        StudyReminder.setEnabled(context, enabled)
                        if (enabled &&
                            Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
            SettingsRow(
                title = "إعادة قفل بوابة القناة الآن",
                detail = "سيُطلب إدخال الكود عند الدخول القادم",
            ) {
                lockChannelGate(context)
                onDismiss()
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("إغلاق", color = SettingsCyan, fontFamily = SettingsFont, fontSize = 13.sp)
                }
            }
        }
    }

    if (showTrash) {
        TrashDialog(onDismiss = { showTrash = false })
    }
}

@Composable
private fun SettingsRow(title: String, detail: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                title,
                color = Color.White,
                fontFamily = SettingsFont,
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                detail,
                color = SettingsMuted,
                fontFamily = SettingsFont,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TrashDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = TrashBin.version
    val items = remember(version) { TrashBin.items(context) }
    var emptyConfirm by remember { mutableStateOf(false) }
    var foreverItem by remember { mutableStateOf<TrashItem?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SettingsCard,
        title = {
            Text(
                "سلة المحذوفات",
                color = Color.White,
                fontFamily = SettingsFont,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    "المحذوفات تبقى هنا شهرًا ثم تُمسح نهائيًا وحدها. يمكنك استرجاع أي ملف إلى مكانه.",
                    color = SettingsMuted,
                    fontFamily = SettingsFont,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                if (items.isEmpty()) {
                    Text(
                        "السلة فارغة.",
                        color = Color.White,
                        fontFamily = SettingsFont,
                        fontSize = 14.sp,
                    )
                } else {
                    Column(
                        Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items.forEach { item ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.fileName.removeSuffix(".pdf").replace('_', ' '),
                                        color = Color.White,
                                        fontFamily = SettingsFont,
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                    )
                                    Text(
                                        "${formatSettingsSize(item.size)} · ${trashAge(item.deletedAt)}",
                                        color = SettingsMuted,
                                        fontFamily = SettingsFont,
                                        fontSize = 11.sp,
                                    )
                                }
                                IconButton(onClick = { TrashBin.restore(context, item) }) {
                                    Icon(
                                        Icons.Filled.Restore,
                                        contentDescription = "استرجاع",
                                        tint = SettingsCyan,
                                    )
                                }
                                IconButton(onClick = { foreverItem = item }) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "حذف نهائي",
                                        tint = SettingsDanger,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (items.isNotEmpty()) {
                    TextButton(onClick = { emptyConfirm = true }) {
                        Text("إفراغ السلة", color = SettingsDanger, fontFamily = SettingsFont)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("إغلاق", color = SettingsCyan, fontFamily = SettingsFont)
                }
            }
        },
    )

    if (emptyConfirm) {
        ConfirmTwiceDialog(
            what = "كل ما في سلة المحذوفات (${items.size} ملف)",
            note = "مسح نهائي لا يمكن التراجع عنه.",
            onDismiss = { emptyConfirm = false },
            onConfirmed = {
                emptyConfirm = false
                TrashBin.emptyAll(context)
            },
        )
    }
    foreverItem?.let { item ->
        ConfirmTwiceDialog(
            what = item.fileName.removeSuffix(".pdf").replace('_', ' '),
            note = "مسح نهائي من السلة لا يمكن التراجع عنه.",
            onDismiss = { foreverItem = null },
            onConfirmed = {
                foreverItem = null
                TrashBin.deleteForever(context, item)
            },
        )
    }
}

private fun trashAge(deletedAt: Long): String {
    val days = ((System.currentTimeMillis() - deletedAt) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "اليوم"
        days == 1 -> "قبل يوم"
        else -> "قبل $days يوم"
    }
}

private fun formatSettingsSize(bytes: Long): String {
    val megabytes = bytes / 1_048_576.0
    return if (megabytes < 1024.0) {
        String.format(Locale.US, "%.1f ميجا", megabytes)
    } else {
        String.format(Locale.US, "%.2f جيجا", megabytes / 1024.0)
    }
}
