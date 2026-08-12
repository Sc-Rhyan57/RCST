package com.rhyan57.rcst.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rhyan57.rcst.MainViewModel
import com.rhyan57.rcst.ui.theme.AppColors

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HomeScreen(vm: MainViewModel, onScrolled: (Boolean) -> Unit) {
    val homeUrl         by vm.homeUrl.collectAsState()
    val jsEnabled       by vm.javascriptEnabled.collectAsState()
    val desktopSite     by vm.desktopSite.collectAsState()

    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack        by remember { mutableStateOf(false) }
    var canGoForward     by remember { mutableStateOf(false) }
    var isLoading        by remember { mutableStateOf(true) }
    var progress         by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { webView?.goBack() },
                    enabled = canGoBack
                ) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else AppColors.TextMuted
                    )
                }
                IconButton(
                    onClick = { webView?.goForward() },
                    enabled = canGoForward
                ) {
                    Icon(
                        Icons.Outlined.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else AppColors.TextMuted
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { webView?.reload() }) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Reload",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
                                onScrolled(false)
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                isLoading = false
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
                            }

                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                view.loadUrl(request.url.toString())
                                return true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        settings.apply {
                            javaScriptEnabled = jsEnabled
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            if (desktopSite) {
                                userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
                            }
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        loadUrl(homeUrl)
                        webView = this
                    }
                },
                update = { view ->
                    view.settings.javaScriptEnabled = jsEnabled
                    if (desktopSite) {
                        view.settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
                    } else {
                        view.settings.userAgentString = null
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(
                visible = isLoading && progress < 30,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
