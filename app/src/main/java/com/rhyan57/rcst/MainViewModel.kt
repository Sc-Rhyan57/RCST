package com.rhyan57.rcst

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rhyan57.rcst.data.SettingsRepository
import com.rhyan57.rcst.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    val themeMode = repo.themeMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM
    )

    val materialYou = repo.materialYou.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    val homeUrl = repo.homeUrl.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "https://www.google.com"
    )

    val javascriptEnabled = repo.javascriptEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    val desktopSite = repo.desktopSite.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }

    fun setMaterialYou(enabled: Boolean) = viewModelScope.launch { repo.setMaterialYou(enabled) }

    fun setHomeUrl(url: String) = viewModelScope.launch { repo.setHomeUrl(url) }

    fun setJavascriptEnabled(enabled: Boolean) = viewModelScope.launch { repo.setJavascriptEnabled(enabled) }

    fun setDesktopSite(enabled: Boolean) = viewModelScope.launch { repo.setDesktopSite(enabled) }
}
