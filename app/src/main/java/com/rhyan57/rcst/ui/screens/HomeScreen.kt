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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rhyan57.rcst.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val prefs = context.getSharedPreferences("rcst_prefs", Context.MODE_PRIVATE)
    var monitorEnabled by remember { mutableStateOf(prefs.getBoolean("monitor_enabled", true)) }

    var requestLogs by remember { mutableStateOf(mutableListOf<RequestLog>()) }
    var logIdCounter by remember { mutableStateOf(0L) }
    var showWebTools by remember { mutableStateOf(false) }
    var toolsTab by remember { mutableIntStateOf(0) }
    var sourceCode by remember { mutableStateOf("") }
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
        AlertDialog(
            onDismissRequest = { showWebTools = false },
            confirmButton = {
                TextButton(onClick = { showWebTools = false }) {
                    Text("Close")
                }
            },
            title = { Text("Web Tools") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { toolsTab = 0 }) { Text("Monitor", color = if (toolsTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                        TextButton(onClick = { toolsTab = 1 }) { Text("Source", color = if (toolsTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                        TextButton(onClick = { toolsTab = 2 }) { Text("JS Exec", color = if (toolsTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                        TextButton(onClick = { toolsTab = 3 }) { Text("Hooks", color = if (toolsTab == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    when (toolsTab) {
                        0 -> {
                            LazyColumn(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                                items(requestLogs) { log ->
                                    var expanded by remember { mutableStateOf(false) }
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expanded = !expanded }
                                    ) {
                                        Text("${log.method} ${log.type} ${log.url.take(50)}", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                                        if (expanded) {
                                            Text("URL: ${log.url}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                            Text("Headers: ${log.headers}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                            Text("Req Body: ${log.body}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                            Text("Status: ${log.status}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                            Text("Res Body: ${log.responseBody}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                            
                                            Row {
                                                TextButton(onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("Headers", log.headers))
                                                }) { Text("Copy Headers") }
                                                TextButton(onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("Body", log.body))
                                                }) { Text("Copy Body") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                                Button(onClick = {
                                    webView?.evaluateJavascript("(function(){return document.documentElement.outerHTML})()") { result ->
                                        sourceCode = result?.removeSurrounding("\"")?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: ""
                                    }
                                }) { Text("Fetch Source") }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("HTML Editor (JS to inject on load):", color = MaterialTheme.colorScheme.onSurface)
                                OutlinedTextField(
                                    value = htmlInject,
                                    onValueChange = { htmlInject = it },
                                    modifier = Modifier.fillMaxWidth().height(150.dp).verticalScroll(rememberScrollState()),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                )
                                Button(onClick = {
                                    context.getSharedPreferences("rcst_hooks", Context.MODE_PRIVATE).edit().putString("html", htmlInject).apply()
                                    webView?.evaluateJavascript("try { eval(`$htmlInject`); } catch(e) {}", null)
                                }) { Text("Apply & Save") }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Source Code:", color = MaterialTheme.colorScheme.onSurface)
                                Text(text = sourceCode, modifier = Modifier.fillMaxWidth().height(100.dp).verticalScroll(rememberScrollState()), fontSize = 10.sp)
                            }
                        }
                        2 -> {
                            Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                                Text("Execute JavaScript:", color = MaterialTheme.colorScheme.onSurface)
                                OutlinedTextField(
                                    value = jsInput,
                                    onValueChange = { jsInput = it },
                                    modifier = Modifier.fillMaxWidth().height(150.dp).verticalScroll(rememberScrollState()),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    placeholder = { Text("e.g: cloneref(document)") }
                                )
                                Button(onClick = {
                                    webView?.evaluateJavascript(jsInput, null)
                                }) { Text("Run") }
                            }
                        }
                        3 -> {
                            Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                                Text("Hook System (JS):", color = MaterialTheme.colorScheme.onSurface)
                                Text("Use window.addHook(function(req){ ... })", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                Text("req has: method, url, headers, body", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                OutlinedTextField(
                                    value = hooksInput,
                                    onValueChange = { hooksInput = it },
                                    modifier = Modifier.fillMaxWidth().height(150.dp).verticalScroll(rememberScrollState()),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    placeholder = { Text("window.addHook(function(r){ if(r.headers['Pix']=='1') r.headers['Pix']='0'; });") }
                                )
                                Button(onClick = {
                                    context.getSharedPreferences("rcst_hooks", Context.MODE_PRIVATE).edit().putString("hooks", hooksInput).apply()
                                    webView?.evaluateJavascript("window.__rcst_user_hooks = `$hooksInput`;", null)
                                    webView?.reload()
                                }) { Text("Save & Apply") }
                            }
                        }
                    }
                }
            }
        )
    }
}
