package com.rhyan57.rcst.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhyan57.rcst.ui.theme.AppColors

@Composable
fun BottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isCollapsed: Boolean
) {
    val barHeight by animateDpAsState(
        targetValue = if (isCollapsed) 60.dp else 82.dp,
        animationSpec = tween(300),
        label = "bar_height"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(AppColors.Divider.copy(alpha = 0.4f))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .height(barHeight)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavTabItem(
                    icon = Icons.Outlined.Home,
                    label = "Home",
                    selected = selectedTab == 0,
                    showLabel = !isCollapsed,
                    onClick = { onTabSelected(0) }
                )
                NavTabItem(
                    icon = Icons.Outlined.Settings,
                    label = "Settings",
                    selected = selectedTab == 1,
                    showLabel = !isCollapsed,
                    onClick = { onTabSelected(1) }
                )
            }

            if (!isCollapsed) {
                Spacer(Modifier.height(2.dp))
                Footer()
            }
        }
    }
}

@Composable
private fun NavTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else AppColors.TextMuted
    IconButton(onClick = onClick, modifier = Modifier.width(100.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            if (showLabel) {
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    fontSize = 10.sp,
                    color = color,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
