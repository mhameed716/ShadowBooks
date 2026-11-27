package com.shadowbooks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.R

internal enum class HomeTab { Library, StudyResources, Timetable, DevicePdfs, Store }

private val NavBackground = Color(0xFF081322)
private val NavActive = Color(0xFF65D4FA)
private val NavIdle = Color(0xFF8192A8)
private val NavFont = FontFamily(Font(R.font.tajawal_regular), Font(R.font.tajawal_extrabold, FontWeight.ExtraBold))

@Composable
internal fun HomeBottomNavigation(
    selected: HomeTab,
    onLibrary: () -> Unit,
    onStudyResources: () -> Unit,
    onTimetable: () -> Unit,
    onDevicePdfs: () -> Unit,
    onStore: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(NavBackground)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF26374A)))
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            // RTL placement: timetable on the right; device PDFs are the last tab at the far left.
            NavItem("جدول الحصص", selected == HomeTab.Timetable, Icons.Filled.CalendarToday, onTimetable)
            NavItem("المكتبة", selected == HomeTab.Library, Icons.Filled.MenuBook, onLibrary)
            NavItem("ملخصات واختبارات", selected == HomeTab.StudyResources, Icons.Filled.Description, onStudyResources)
            NavItem("المتجر", selected == HomeTab.Store, Icons.Filled.Storefront, onStore)
            NavItem("ملفات الهاتف", selected == HomeTab.DevicePdfs, Icons.Filled.FolderOpen, onDevicePdfs)
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).height(64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) NavActive else NavIdle, modifier = Modifier.size(21.dp))
        Text(label, color = if (selected) NavActive else NavIdle, fontFamily = NavFont, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 9.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        if (selected) Box(Modifier.padding(top = 3.dp).size(width = 20.dp, height = 2.dp).background(NavActive))
    }
}
