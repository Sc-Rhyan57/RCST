package com.rhyan57.rcst.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rhyan57.rcst.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private object DC {
    val Bg        = Color(0xFF0E0F13)
    val Surface   = Color(0xFF17181C)
    val Card      = Color(0xFF1E1F26)
    val CardAlt   = Color(0xFF22232B)
    val Border    = Color(0xFF2C2D35)
    val Primary   = Color(0xFF5865F2)
    val Success   = Color(0xFF23A55A)
    val Warning   = Color(0xFFFAA61A)
    val Error     = Color(0xFFED4245)
    val White     = Color(0xFFF2F3F5)
    val SubText   = Color(0xFFB5BAC1)
    val Muted     = Color(0xFF72767D)
    val OrbViolet = Color(0xFFB675F0)
    val Teal      = Color(0xFF43B581)
}

data class RequestLog(
    val id: Long,
    val timestamp: String,
    val type: String,
    val method: String,
    val url: String,
    val headers: String,
    val body: String,
    val status: Int,
    val responseBody: String
)

class WebHookInterface(private val onLog: (String) -> Unit) {
    @android.webkit.JavascriptInterface
    fun onRequest(data: String) {
        onLog(data)
    }
}

private fun parseHtml(code: String): AnnotatedString {
    if (code.isBlank()) return AnnotatedString(code)
    return buildAnnotatedString {
        val tagRegex = Regex("(<\\!--.*?-->)|(<\\/?[a-zA-Z0-9]+)|(\\/?>)|([a-zA-Z-]+=)|(\".*?\")")
        var lastIndex = 0
        tagRegex.findAll(code).forEach { match ->
            append(code.substring(lastIndex, match.range.first))
            when {
                match.value.startsWith("<!--") -> withStyle(SpanStyle(color = Color(0xFF6A9955))) { append(match.value) }
                match.value.startsWith("<") || match.value.startsWith("/") -> withStyle(SpanStyle(color = Color(0xFF569CD6))) { append(match.value) }
                match.value.endsWith("=") -> withStyle(SpanStyle(color = Color(0xFF9CDCFE))) { append(match.value) }
                match.value.startsWith("\"") -> withStyle(SpanStyle(color = Color(0xFFCE9178))) { append(match.value) }
                else -> withStyle(SpanStyle(color = Color(0xFFD4D4D4))) { append(match.value) }
            }
            lastIndex = match.range.last + 1
        }
        append(code.substring(lastIndex))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HomeScreen(vm: MainViewModel, onScrolled: (Boolean) -> Unit) {
    val homeUrl     by vm.homeUrl.collectAsState()
    val jsEnabled   by vm.javascriptEnabled.collectAsState()
    val desktopSite by vm.desktopSite.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var webView      by remember { mutableStateOf<WebView?>(null) }
    var canGoBack    by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading    by remember { mutableStateOf(true) }
    var progress     by remember { mutableStateOf(0) }

    val prefs = context.getSharedPreferences("rcst_prefs", Context.MODE_PRIVATE)
    var monitorEnabled by remember { mutableStateOf(prefs.getBoolean("monitor_enabled", true)) }

    var requestLogs by remember { mutableStateOf(mutableListOf<RequestLog>()) }
    var logIdCounter by remember { mutableStateOf(0L) }
    var showWebTools by remember { mutableStateOf(false) }
    var toolsTab by remember { mutableIntStateOf(0) }
    var jsInput by remember { mutableStateOf("") }
    var hooksInput by remember { mutableStateOf("") }
    var htmlInject by remember { mutableStateOf("") }

    val pendingRequests = remember { mutableMapOf<String, RequestLog>() }
    val cacheFile = remember { File(context.cacheDir, "rcst_session.json") }

    LaunchedEffect(Unit) {
        if (cacheFile.exists()) {
            try {
                val arr = JSONArray(cacheFile.readText())
                val list = mutableListOf<RequestLog>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(RequestLog(
                        o.getLong("id"),
                        o.optString("timestamp"),
                        o.optString("type"),
                        o.optString("method"),
                        o.optString("url"),
                        o.optString("headers"),
                        o.optString("body"),
                        o.optInt("status"),
                        o.optString("responseBody")
                    ))
                }
                requestLogs = list
                logIdCounter = list.maxOfOrNull { it.id } ?: 0L
            } catch (_: Exception) {}
        }
        val hookPrefs = context.getSharedPreferences("rcst_hooks", Context.MODE_PRIVATE)
        hooksInput = hookPrefs.getString("hooks", "") ?: ""
        htmlInject = hookPrefs.getString("html", "") ?: ""
    }

    fun saveLogsCache() {
        scope.launch(Dispatchers.IO) {
            try {
                val arr = JSONArray()
                requestLogs.takeLast(200).forEach { log ->
                    val o = JSONObject()
                    o.put("id", log.id)
                    o.put("timestamp", log.timestamp)
                    o.put("type", log.type)
                    o.put("method", log.method)
                    o.put("url", log.url)
                    o.put("headers", log.headers)
                    o.put("body", log.body)
                    o.put("status", log.status)
                    o.put("responseBody", log.responseBody)
                    arr.put(o)
                }
                cacheFile.writeText(arr.toString())
            } catch (_: Exception) {}
        }
    }

    val hookJs = remember(hooksInput) {
        """
        (function(){
            if(window.__rcst_hooked) return;
            window.__rcst_hooked=true;
            window.cloneref=function(o){return o?JSON.parse(JSON.stringify(o)):o;};
            window.__rcst_hooks=[];
            window.addHook=function(f){window.__rcst_hooks.push(f);};
            var h=window.__rcst_hooks;
            function applyHooks(d){h.forEach(function(f){try{f(d)}catch(e){}});return d;}
            function log(d){try{AndroidWebInterface.onRequest(JSON.stringify(d))}catch(e){}}
            
            var oO=XMLHttpRequest.prototype.open,oS=XMLHttpRequest.prototype.send,oH=XMLHttpRequest.prototype.setRequestHeader;
            XMLHttpRequest.prototype.open=function(m,u){this.__m=m;this.__u=u;this.__h={};return oO.apply(this,arguments)};
            XMLHttpRequest.prototype.setRequestHeader=function(n,v){this.__h[n]=v};
            XMLHttpRequest.prototype.send=function(b){
                var self=this;
                var d={type:'xhr',method:this.__m,url:this.__u,headers:this.__h,body:b?String(b):''};
                applyHooks(d);
                for(var k in d.headers){oH.call(this,k,d.headers[k])}
                log(d);
                this.addEventListener('load',function(){log({type:'xhr_res',method:self.__m,url:self.__u,headers:'',body:'',status:self.status,responseBody:self.responseText?self.responseText.substring(0,5000):''})});
                return oS.call(this,d.body);
            };
            
            var oF=window.fetch;
            window.fetch=function(i,init){
                var u=typeof i==='string'?i:(i&&i.url)||'';
                var m=(init&&init.method)||'GET';
                var hd={};
                var rh=(init&&init.headers)||(i&&i.headers);
                if(rh){if(rh instanceof Headers){rh.forEach(function(v,k){hd[k]=v})}else if(typeof rh==='object'){for(var k in rh){hd[k]=rh[k]}}}
                var b=(init&&init.body)?String(init.body):'';
                var d={type:'fetch',method:m,url:u,headers:JSON.stringify(hd),body:b};
                applyHooks(d);
                if(init){init.headers=hd;init.body=d.body}
                log(d);
                return oF.apply(this,arguments).then(function(r){r.clone().text().then(function(t){log({type:'fetch_res',method:m,url:u,headers:'',body:'',status:r.status,responseBody:t.substring(0,5000)})});return r});
            };
            
            var oW=window.WebSocket;
            window.WebSocket=function(u,p){
                log({type:'ws',method:'WS',url:u,headers:'',body:''});
                var w=p?new oW(u,p):new oW(u);
                var oSws=w.send.bind(w);
                w.send=function(data){log({type:'ws_send',method:'WS',url:u,headers:'',body:data?String(data):''});return oSws(data)};
                return w;
            };
            window.WebSocket.prototype=oW.prototype;
            
            try{
                var userHooks=window.__rcst_user_hooks;
                if(userHooks){eval(userHooks)}
            }catch(e){}
        })();
        """
    }

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
                IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
                IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                    Icon(
                        Icons.Outlined.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showWebTools = true }) {
                    Icon(Icons.Outlined.Code, contentDescription = "Tools", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Reload", tint = MaterialTheme.colorScheme.onSurface)
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
                                
                                if (monitorEnabled) {
                                    view.evaluateJavascript("window.__rcst_user_hooks = `$hooksInput`;", null)
                                    view.evaluateJavascript(hookJs, null)
                                }
                                if (htmlInject.isNotEmpty()) {
                                    view.evaluateJavascript("window.__rcst_html_inject = `$htmlInject`; try { eval(window.__rcst_html_inject); } catch(e) {}", null)
                                }
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
                        
                        if (monitorEnabled) {
                            addJavascriptInterface(WebHookInterface { dataStr ->
                                try {
                                    val json = JSONObject(dataStr)
                                    val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
                                    val type = json.optString("type")
                                    val url = json.optString("url")
                                    val method = json.optString("method")
                                    val headers = json.optString("headers")
                                    val body = json.optString("body")
                                    val status = json.optInt("status", 0)
                                    val resBody = json.optString("responseBody")
                                    
                                    val key = "$method:$url"
                                    
                                    if (type == "xhr_res" || type == "fetch_res") {
                                        val pending = pendingRequests.remove(key)
                                        val finalLog = pending?.copy(
                                            status = status,
                                            responseBody = resBody
                                        ) ?: RequestLog(++logIdCounter, ts, type, method, url, headers, body, status, resBody)
                                        
                                        requestLogs = requestLogs.toMutableList().also { it.add(0, finalLog) }
                                        if (requestLogs.size > 200) requestLogs.removeAt(requestLogs.size - 1)
                                        saveLogsCache()
                                    } else {
                                        val reqLog = RequestLog(++logIdCounter, ts, type, method, url, headers, body, 0, "")
                                        pendingRequests[key] = reqLog
                                        requestLogs = requestLogs.toMutableList().also { it.add(0, reqLog) }
                                        if (requestLogs.size > 200) requestLogs.removeAt(requestLogs.size - 1)
                                        saveLogsCache()
                                    }
                                } catch (_: Exception) {}
                            }, "AndroidWebInterface")
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

            if (isLoading && progress < 30) {
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

    if (showWebTools) {
        WebToolsDialog(
            onDismiss = { showWebTools = false },
            webView = webView,
            requestLogs = requestLogs,
            toolsTab = toolsTab,
            onTabChange = { toolsTab = it },
            jsInput = jsInput,
            onJsInputChange = { jsInput = it },
            hooksInput = hooksInput,
            onHooksChange = { hooksInput = it },
            applyHooks = {
                context.getSharedPreferences("rcst_hooks", Context.MODE_PRIVATE).edit().putString("hooks", hooksInput).apply()
                webView?.evaluateJavascript("window.__rcst_user_hooks = `$hooksInput`;", null)
                webView?.reload()
            },
            htmlInject = htmlInject,
            onHtmlChange = { htmlInject = it },
            applyHtml = {
                context.getSharedPreferences("rcst_hooks", Context.MODE_PRIVATE).edit().putString("html", htmlInject).apply()
                webView?.evaluateJavascript("try { eval(`$htmlInject`); } catch(e) {}", null)
            },
            context = context
        )
    }
}

@Composable
private fun WebToolsDialog(
    onDismiss: () -> Unit,
    webView: WebView?,
    requestLogs: List<RequestLog>,
    toolsTab: Int,
    onTabChange: (Int) -> Unit,
    jsInput: String,
    onJsInputChange: (String) -> Unit,
    hooksInput: String,
    onHooksChange: (String) -> Unit,
    applyHooks: () -> Unit,
    htmlInject: String,
    onHtmlChange: (String) -> Unit,
    applyHtml: () -> Unit,
    context: Context
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(DC.Bg)) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(DC.Surface).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = DC.White) }
                    Text("Web Tools", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DC.White, modifier = Modifier.weight(1f))
                }
                
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TabButton("Monitor", toolsTab == 0) { onTabChange(0) }
                    TabButton("Source", toolsTab == 1) { onTabChange(1) }
                    TabButton("JS Exec", toolsTab == 2) { onTabChange(2) }
                    TabButton("Hooks", toolsTab == 3) { onTabChange(3) }
                }
                
                when (toolsTab) {
                    0 -> MonitorTab(requestLogs, context)
                    1 -> SourceTab(webView, htmlInject, onHtmlChange, applyHtml)
                    2 -> JsExecTab(webView, jsInput, onJsInputChange)
                    3 -> HooksTab(hooksInput, onHooksChange, applyHooks)
                }
            }
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) DC.Primary.copy(0.2f) else DC.Card).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, fontSize = 11.sp, color = if (selected) DC.Primary else DC.Muted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun MonitorTab(requestLogs: List<RequestLog>, context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    
    if (requestLogs.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("No requests intercepted yet", color = DC.Muted)
        }
        return
    }
    
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(requestLogs) { log ->
            var expanded by remember { mutableStateOf(false) }
            val typeColor = when (log.type) {
                "fetch", "xhr" -> DC.Primary
                "ws", "ws_send" -> DC.OrbViolet
                else -> DC.Muted
            }
            val statusColor = when {
                log.status in 200..299 -> DC.Success
                log.status in 400..599 -> DC.Error
                log.status == 0 -> DC.Muted
                else -> DC.Warning
            }
            
            Card(
                colors = CardDefaults.cardColors(containerColor = DC.Card),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.background(typeColor.copy(0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(log.type.uppercase(), fontSize = 9.sp, color = typeColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(log.method, fontSize = 10.sp, color = DC.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(8.dp))
                        Text(log.url, fontSize = 10.sp, color = DC.SubText, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (log.status > 0) {
                            Text("${log.status}", fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.width(4.dp))
                        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = DC.Muted, modifier = Modifier.size(16.dp))
                    }
                    if (expanded) {
                        Spacer(Modifier.height(8.dp))
                        DetailText("URL:", log.url)
                        if (log.headers.isNotEmpty() && log.headers != "{}") DetailText("Headers:", log.headers)
                        if (log.body.isNotEmpty()) DetailText("Body:", log.body)
                        if (log.responseBody.isNotEmpty()) DetailText("Response:", log.responseBody)
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("Headers", log.headers)) }) { Text("Copy Headers", color = DC.Primary, fontSize = 10.sp) }
                            TextButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("Body", log.body)) }) { Text("Copy Body", color = DC.Primary, fontSize = 10.sp) }
                            TextButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("Response", log.responseBody)) }) { Text("Copy Response", color = DC.Primary, fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailText(label: String, value: String) {
    if (value.isEmpty() || value == "null") return
    Text(label, fontSize = 9.sp, color = DC.Primary, fontWeight = FontWeight.Bold)
    Text(value, fontSize = 9.sp, color = DC.Muted, fontFamily = FontFamily.Monospace, maxLines = 5, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SourceTab(webView: WebView?, htmlInject: String, onHtmlChange: (String) -> Unit, applyHtml: () -> Unit) {
    var sourceCode by remember { mutableStateOf("") }
    
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    webView?.evaluateJavascript("(function(){return document.documentElement.outerHTML})()") { result ->
                        sourceCode = result?.removeSurrounding("\"")?.replace("\\n", "\n")?.replace("\\\"", "\"")?.replace("\\u003C", "<")?.replace("\\u003E", ">")?.replace("\\/", "/") ?: ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DC.Primary)
            ) { Text("Fetch Source Code") }
            Spacer(Modifier.weight(1f))
            Text("Live HTML Editor (JS)", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(8.dp))
        
        OutlinedTextField(
            value = htmlInject,
            onValueChange = onHtmlChange,
            modifier = Modifier.fillMaxWidth().height(100.dp).background(DC.Card),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = DC.White),
            colors = OutlinedTextFieldDefaults.colors(unfocusedTextColor = DC.White, unfocusedBorderColor = DC.Border, cursorColor = DC.Primary),
            placeholder = { Text("e.g. document.body.innerHTML += '<h1>Modified</h1>'", color = DC.Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
        )
        
        Button(onClick = applyHtml, colors = ButtonDefaults.buttonColors(containerColor = DC.Warning)) { Text("Apply & Save") }
        
        Spacer(Modifier.height(16.dp))
        
        Text("Source Code:", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        
        LazyColumn(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(DC.Card).padding(8.dp)) {
            if (sourceCode.isEmpty()) {
                item { Text("Click 'Fetch Source Code' to load...", color = DC.Muted, fontSize = 10.sp) }
            } else {
                val lines = sourceCode.lines()
                itemsIndexed(lines) { index, line ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("${index + 1}", color = DC.Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(30.dp).padding(end = 8.dp))
                        Text(parseHtml(line), color = DC.SubText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, overflow = TextOverflow.Visible)
                    }
                }
            }
        }
    }
}

@Composable
private fun JsExecTab(webView: WebView?, jsInput: String, onJsInputChange: (String) -> Unit) {
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Execute JavaScript:", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = jsInput,
            onValueChange = {
                onJsInputChange(it)
                if (it.endsWith("print(") || it.endsWith("find(") || it.endsWith("highlight(")) {
                    webView?.evaluateJavascript("(function(){try{return Array.from(document.querySelectorAll('[id], button, a, input, div')).slice(0, 15).map(e=>{return (e.id?'#'+e.id:'<'+e.tagName.toLowerCase()+'>')+(e.innerText.substring(0,10)?': '+e.innerText.substring(0,10):'')})}catch(e){return []}})();") { res ->
                        try {
                            val arr = JSONArray(res ?: "[]")
                            suggestions = (0 until arr.length()).map { arr.getString(it) }
                        } catch (_: Exception) {
                            suggestions = emptyList()
                        }
                    }
                } else {
                    suggestions = emptyList()
                }
            },
            modifier = Modifier.fillMaxWidth().height(150.dp).background(DC.Card),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = DC.White),
            colors = OutlinedTextFieldDefaults.colors(unfocusedTextColor = DC.White, unfocusedBorderColor = DC.Border, cursorColor = DC.Primary),
            placeholder = { Text("e.g: print(document)", color = DC.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
        )
        
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Suggestions (Click to insert & highlight):", color = DC.Warning, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestions) { sugg ->
                    val idOrTag = sugg.substringBefore(":").trim()
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(DC.CardAlt).clickable {
                            val selector = "'$idOrTag'"
                            onJsInputChange(jsInput.dropLast(1) + "$selector)")
                            webView?.evaluateJavascript("try{var e=document.querySelector($selector); if(e){e.style.outline='2px solid cyan'; e.style.backgroundColor='rgba(0,255,255,0.2)'; e.scrollIntoView({behavior:'smooth',block:'center'});}}catch(e){}", null)
                            suggestions = emptyList()
                        }.padding(8.dp)
                    ) {
                        Text(sugg, color = DC.SubText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { webView?.evaluateJavascript(jsInput, null) },
            colors = ButtonDefaults.buttonColors(containerColor = DC.Success)
        ) { Text("Run JS") }
    }
}

@Composable
private fun HooksTab(hooksInput: String, onHooksChange: (String) -> Unit, applyHooks: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Hook System (JS):", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Use window.addHook(function(req){ ... })", color = DC.Muted, fontSize = 10.sp)
        Text("req has: method, url, headers, body", color = DC.Muted, fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = hooksInput,
            onValueChange = onHooksChange,
            modifier = Modifier.fillMaxWidth().height(200.dp).background(DC.Card),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = DC.White),
            colors = OutlinedTextFieldDefaults.colors(unfocusedTextColor = DC.White, unfocusedBorderColor = DC.Border, cursorColor = DC.Primary),
            placeholder = { Text("window.addHook(function(r){ if(r.headers['Pix']=='1') r.headers['Pix']='0'; });", color = DC.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = applyHooks,
            colors = ButtonDefaults.buttonColors(containerColor = DC.Primary)
        ) { Text("Save & Apply Hooks") }
    }
}
