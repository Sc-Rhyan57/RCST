package com.rhyan57.rcst.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.rhyan57.rcst.MainViewModel
import com.rhyan57.rcst.ui.components.BottomBar

@Composable
fun MainScreen(vm: MainViewModel) {
    var selectedTab  by remember { mutableIntStateOf(0) }
    var isCollapsed  by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> HomeScreen(vm = vm, onScrolled = { isCollapsed = it })
                1 -> SettingsScreen(vm = vm)
            }
        }
        BottomBar(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
                isCollapsed = false
            },
            isCollapsed = isCollapsed
        )
    }
}
