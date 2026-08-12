package com.rhyan57.rcst.ui.screens

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rhyan57.rcst.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    
    var showTools    by remember { mutableStateOf(false) }
    var toolsTab     by remember { mutableIntStateOf(0) }

    val prefs = context.getSharedPreferences("rcst_prefs", Context.MODE_PRIVATE)
    var monitorEnabled by remember { mutableStateOf(prefs.getBoolean("monitor_enabled", true)) }

    var requestLogs by remember { mutableStateOf(mutableListOf<RequestLog>()) }
    var logIdCounter by remember { mutableStateOf(0L) }
    var jsInput by remember { mutableStateOf("") }
    var hooksInput by remember { mutableStateOf("") }
    var pluginsInput by remember { mutableStateOf("") }
    var videoLinks by remember { mutableStateOf(mutableListOf<String>()) }

    val pendingRequests = remember { mutableMapOf<String, RequestLog>() }
    val cacheFile = remember { File(context.cacheDir, "rcst_session.json") }
    val hookPrefs = remember { context.getSharedPreferences("rcst_hooks", Context.MODE_PRIVATE) }

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
        hooksInput = hookPrefs.getString("hooks", "") ?: ""
        pluginsInput = hookPrefs.getString("plugins", "") ?: ""
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

    val discordFixJs = """
        try {
            Object.defineProperty(navigator, 'userAgent', { get: function () { return 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'; } });
            Object.defineProperty(navigator, 'platform', { get: function () { return 'Win32'; } });
            window.chrome = { runtime: {} };
        } catch(e) {}
    """

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
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
                IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Forward", tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showTools = !showTools }) {
                    Icon(Icons.Outlined.Code, "Tools", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Outlined.Refresh, "Reload", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(if (showTools) 0.4f else 1f)) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    canGoBack = view.canGoBack()
                                    canGoForward = view.canGoForward()
                                    onScrolled(false)
                                    view.evaluateJavascript(discordFixJs, null)
                                }
                                override fun onPageFinished(view: WebView, url: String?) {
                                    isLoading = false
                                    canGoBack = view.canGoBack()
                                    canGoForward = view.canGoForward()
                                    
                                    if (monitorEnabled) {
                                        view.evaluateJavascript("window.__rcst_user_hooks = `$hooksInput`;", null)
                                        view.evaluateJavascript(hookJs, null)
                                    }
                                    if (pluginsInput.isNotEmpty()) {
                                        view.evaluateJavascript("try { eval(`$pluginsInput`); } catch(e) {}", null)
                                    }
                                    view.evaluateJavascript("(function(){return JSON.stringify(Array.from(document.querySelectorAll('video')).map(v=>v.src||v.currentSrc).filter(s=>s.length>0))})()") { res ->
                                        try {
                                            val arr = JSONArray(res ?: "[]")
                                            videoLinks = (0 until arr.length()).map { arr.getString(it) }.toMutableList()
                                        } catch (_: Exception) {}
                                    }
                                }
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    view.loadUrl(request.url.toString())
                                    return true
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                            }
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                allowFileAccess = true
                                allowContentAccess = true
                                mediaPlaybackRequiresUserGesture = false
                                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
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
                                            val finalLog = pending?.copy(status = status, responseBody = resBody) ?: RequestLog(++logIdCounter, ts, type, method, url, headers, body, status, resBody)
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
                        view.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading && progress < 30) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (showTools) {
                VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp).background(DC.Border))
                Box(modifier = Modifier.weight(0.6f).background(DC.Bg)) {
                    WebToolsPanel(
                        webView = webView,
                        context = context,
                        requestLogs = requestLogs,
                        toolsTab = toolsTab,
                        onTabChange = { toolsTab = it },
                        jsInput = jsInput,
                        onJsInputChange = { jsInput = it },
                        hooksInput = hooksInput,
                        onHooksChange = { hooksInput = it },
                        applyHooks = {
                            hookPrefs.edit().putString("hooks", hooksInput).apply()
                            webView?.evaluateJavascript("window.__rcst_user_hooks = `$hooksInput`;", null)
                            webView?.reload()
                        },
                        pluginsInput = pluginsInput,
                        onPluginsChange = { pluginsInput = it },
                        applyPlugins = {
                            hookPrefs.edit().putString("plugins", pluginsInput).apply()
                            webView?.evaluateJavascript("try { eval(`$pluginsInput`); } catch(e) {}", null)
                        },
                        videoLinks = videoLinks,
                        downloadHtml = {
                            webView?.evaluateJavascript("(function(){return document.documentElement.outerHTML})()") { result ->
                                val html = result?.removeSurrounding("\"")?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: ""
                                scope.launch(Dispatchers.IO) {
                                    val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "page_${System.currentTimeMillis()}.html")
                                    file.writeText(html)
                                }
                            }
                        },
                        downloadSiteZip = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val baseUrlStr = webView?.url ?: return@launch
                                    val url = URL(baseUrlStr)
                                    val connection = url.openConnection() as HttpURLConnection
                                    val doc = connection.inputStream.bufferedReader().use { it.readText() }
                                    connection.disconnect()
                                    
                                    val zipFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "site_${System.currentTimeMillis()}.zip")
                                    ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                                        zip.putNextEntry(ZipEntry("index.html"))
                                        zip.write(doc.toByteArray())
                                        zip.closeEntry()
                                        
                                        val regex = Regex("(?:src|href)=[\"']([^\"']+)[\"']")
                                        val matches = regex.findAll(doc).map { it.groupValues[1] }.filter { it.startsWith("http") || it.startsWith("/") }.take(20)
                                        
                                        for (match in matches) {
                                            try {
                                                val absUrlStr = if (match.startsWith("http")) match else URL(url, match).toString()
                                                val assetUrl = URL(absUrlStr)
                                                val assetConn = assetUrl.openConnection() as HttpURLConnection
                                                val assetBody = assetConn.inputStream.use { it.readBytes() }
                                                assetConn.disconnect()
                                                
                                                val name = absUrlStr.substringAfterLast('/').take(50).ifEmpty { "file_${System.currentTimeMillis()}" }
                                                zip.putNextEntry(ZipEntry("assets/$name"))
                                                zip.write(assetBody)
                                                zip.closeEntry()
                                            } catch (_: Exception) {}
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WebToolsPanel(
    webView: WebView?,
    context: Context,
    requestLogs: List<RequestLog>,
    toolsTab: Int,
    onTabChange: (Int) -> Unit,
    jsInput: String,
    onJsInputChange: (String) -> Unit,
    hooksInput: String,
    onHooksChange: (String) -> Unit,
    applyHooks: () -> Unit,
    pluginsInput: String,
    onPluginsChange: (String) -> Unit,
    applyPlugins: () -> Unit,
    videoLinks: List<String>,
    downloadHtml: () -> Unit,
    downloadSiteZip: () -> Unit
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(DC.Surface).padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TabButton("Monitor", toolsTab == 0) { onTabChange(0) }
            TabButton("Source", toolsTab == 1) { onTabChange(1) }
            TabButton("JS", toolsTab == 2) { onTabChange(2) }
            TabButton("Hooks", toolsTab == 3) { onTabChange(3) }
            TabButton("Plugins", toolsTab == 4) { onTabChange(4) }
            TabButton("Media", toolsTab == 5) { onTabChange(5) }
        }
        
        when (toolsTab) {
            0 -> {
                if (requestLogs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No requests intercepted yet", color = DC.Muted) }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(requestLogs) { log ->
                            var expanded by remember { mutableStateOf(false) }
                            val typeColor = when (log.type) { "fetch", "xhr" -> DC.Primary; "ws", "ws_send" -> DC.OrbViolet; else -> DC.Muted }
                            Card(colors = CardDefaults.cardColors(containerColor = DC.Card), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                                Column(Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(log.type.uppercase(), fontSize = 8.sp, color = typeColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Spacer(Modifier.width(4.dp))
                                        Text(log.method, fontSize = 9.sp, color = DC.White, fontFamily = FontFamily.Monospace)
                                        Spacer(Modifier.width(4.dp))
                                        Text(log.url, fontSize = 9.sp, color = DC.SubText, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        if (log.status > 0) Text("${log.status}", fontSize = 9.sp, color = DC.Success, fontFamily = FontFamily.Monospace)
                                    }
                                    if (expanded) {
                                        Spacer(Modifier.height(4.dp))
                                        if (log.headers.isNotEmpty() && log.headers != "{}") DetailText("Headers:", log.headers)
                                        if (log.body.isNotEmpty()) DetailText("Body:", log.body)
                                        if (log.responseBody.isNotEmpty()) DetailText("Response:", log.responseBody)
                                        Row {
                                            TextButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("Headers", log.headers)) }) { Text("Copy Headers", color = DC.Primary, fontSize = 9.sp) }
                                            TextButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("Body", log.body)) }) { Text("Copy Body", color = DC.Primary, fontSize = 9.sp) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Button(onClick = downloadHtml, colors = ButtonDefaults.buttonColors(containerColor = DC.Primary), modifier = Modifier.fillMaxWidth()) { Text("Download HTML") }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = downloadSiteZip, colors = ButtonDefaults.buttonColors(containerColor = DC.Warning), modifier = Modifier.fillMaxWidth()) { Text("Download Site (ZIP)") }
                    Spacer(Modifier.height(16.dp))
                    Text("Source Preview:", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    var sourceCode by remember { mutableStateOf("") }
                    Button(onClick = { webView?.evaluateJavascript("(function(){return document.documentElement.outerHTML})()") { res -> sourceCode = res?.removeSurrounding("\"")?.replace("\\n", "\n") ?: "" } }) { Text("Fetch Source") }
                    Spacer(Modifier.height(8.dp))
                    Text(sourceCode, color = DC.SubText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.verticalScroll(rememberScrollState()))
                }
            }
            2 -> {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Text("Execute JavaScript:", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = jsInput, onValueChange = onJsInputChange,
                        modifier = Modifier.fillMaxWidth().height(100.dp).background(DC.Card),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = DC.White),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedTextColor = DC.White, unfocusedBorderColor = DC.Border, cursorColor = DC.Primary),
                        placeholder = { Text("e.g: print(document)", color = DC.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                    )
                    Button(onClick = { webView?.evaluateJavascript(jsInput, null) }, colors = ButtonDefaults.buttonColors(containerColor = DC.Success)) { Text("Run") }
                }
            }
            3 -> {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Text("Hook System (JS):", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = hooksInput, onValueChange = onHooksChange,
                        modifier = Modifier.fillMaxWidth().height(100.dp).background(DC.Card),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = DC.White),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedTextColor = DC.White, unfocusedBorderColor = DC.Border, cursorColor = DC.Primary),
                        placeholder = { Text("window.addHook(function(r){ if(r.headers['Pix']=='1') r.headers['Pix']='0'; });", color = DC.Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
                    )
                    Button(onClick = applyHooks, colors = ButtonDefaults.buttonColors(containerColor = DC.Primary)) { Text("Save & Apply") }
                }
            }
            4 -> {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Text("Plugins (Auto-Inject JS):", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = pluginsInput, onValueChange = onPluginsChange,
                        modifier = Modifier.fillMaxWidth().height(100.dp).background(DC.Card),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = DC.White),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedTextColor = DC.White, unfocusedBorderColor = DC.Border, cursorColor = DC.Primary),
                        placeholder = { Text("console.log('Plugin loaded!');", color = DC.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                    )
                    Button(onClick = applyPlugins, colors = ButtonDefaults.buttonColors(containerColor = DC.Warning)) { Text("Save & Apply") }
                }
            }
            5 -> {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Text("Media Extractor:", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (videoLinks.isEmpty()) {
                        Text("No videos found on this page.", color = DC.Muted, fontSize = 11.sp)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(videoLinks) { url ->
                                Card(colors = CardDefaults.cardColors(containerColor = DC.Card), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text(url, color = DC.SubText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Row {
                                            TextButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("Video URL", url)) }) { Text("Copy URL", color = DC.Primary, fontSize = 10.sp) }
                                            TextButton(onClick = {
                                                try {
                                                    val request = DownloadManager.Request(Uri.parse(url))
                                                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "video_${System.currentTimeMillis()}.mp4")
                                                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                                    dm.enqueue(request)
                                                } catch (_: Exception) {}
                                            }) { Text("Download", color = DC.Success, fontSize = 10.sp) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(if (selected) DC.Primary.copy(0.2f) else DC.Card).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 10.sp, color = if (selected) DC.Primary else DC.Muted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun DetailText(label: String, value: String) {
    if (value.isEmpty() || value == "null") return
    Text(label, fontSize = 9.sp, color = DC.Primary, fontWeight = FontWeight.Bold)
    Text(value, fontSize = 9.sp, color = DC.Muted, fontFamily = FontFamily.Monospace, maxLines = 5, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(4.dp))
}
