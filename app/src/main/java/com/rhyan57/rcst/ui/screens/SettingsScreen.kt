package com.rhyan57.rcst.ui.screens

import android.os.Build
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhyan57.rcst.MainViewModel
import com.rhyan57.rcst.ui.theme.AppColors
import com.rhyan57.rcst.ui.theme.Radius
import com.rhyan57.rcst.ui.theme.ThemeMode

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val themeMode       by vm.themeMode.collectAsState()
    val materialYou     by vm.materialYou.collectAsState()
    val homeUrl         by vm.homeUrl.collectAsState()
    val jsEnabled       by vm.javascriptEnabled.collectAsState()
    val desktopSite     by vm.desktopSite.collectAsState()

    var visible         by remember { mutableStateOf(false) }
    var urlDialogOpen   by remember { mutableStateOf(false) }
    var urlDraft        by remember { mutableStateOf(homeUrl) }

    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(homeUrl) { urlDraft = homeUrl }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -40 }
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 56.dp, bottom = 8.dp)
                ) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Preferences & configuration",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, 80)) + slideInVertically(tween(500)) { 40 }
            ) {
                Column {
                    SectionTitle("Appearance")
                    SettingsCard {
                        ThemeSelector(current = themeMode, onSelect = vm::setThemeMode)
                        CardDivider()
                        val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ToggleRow(
                            icon = Icons.Outlined.Palette,
                            title = "Material You",
                            subtitle = if (dynamicSupported) "Use dynamic colors from wallpaper" else "Requires Android 12+",
                            checked = materialYou && dynamicSupported,
                            enabled = dynamicSupported,
                            onToggle = vm::setMaterialYou
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, 160)) + slideInVertically(tween(500)) { 40 }
            ) {
                Column {
                    SectionTitle("Browser")
                    SettingsCard {
                        ClickableRow(
                            icon = Icons.Outlined.Language,
                            title = "Home URL",
                            subtitle = homeUrl,
                            onClick = { urlDialogOpen = true }
                        )
                        CardDivider()
                        ToggleRow(
                            icon = Icons.Outlined.Code,
                            title = "JavaScript",
                            subtitle = "Enable JavaScript on pages",
                            checked = jsEnabled,
                            onToggle = vm::setJavascriptEnabled
                        )
                        CardDivider()
                        ToggleRow(
                            icon = Icons.Outlined.DesktopWindows,
                            title = "Desktop Site",
                            subtitle = "Request desktop version of pages",
                            checked = desktopSite,
                            onToggle = vm::setDesktopSite
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, 240)) + slideInVertically(tween(500)) { 40 }
            ) {
                Column {
                    SectionTitle("About")
                    SettingsCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    "RCST",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Version 1.0 · by rhyan57",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (urlDialogOpen) {
        AlertDialog(
            onDismissRequest = { urlDialogOpen = false },
            title = { Text("Home URL") },
            text = {
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = urlDraft.trim().let {
                        if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
                    }
                    vm.setHomeUrl(url)
                    urlDialogOpen = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { urlDialogOpen = false; urlDraft = homeUrl }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = Radius.Card
        )
    }
}

@Composable
private fun ThemeSelector(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Icon(
                Icons.Outlined.DarkMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                "Theme",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                ThemeChip(
                    label = when (mode) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.AMOLED -> "Amoled"
                        ThemeMode.LIGHT -> "Light"
                    },
                    selected = current == mode,
                    onClick = { onSelect(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = Radius.Card,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { Column(content = content) }
    )
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = AppColors.Divider.copy(alpha = 0.4f)
    )
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggle(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else AppColors.TextMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else AppColors.TextMuted,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { if (enabled) onToggle(it) },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun ClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = AppColors.TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}
