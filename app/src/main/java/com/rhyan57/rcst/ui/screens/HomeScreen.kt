package com.rhyan57.rcst.ui.screens

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rhyan57.rcst.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    val responseBody: String,
    val isBlocked: Boolean = false
)

data class MediaLink(val url: String, val type: String, val fileName: String = "")

data class DownloadItem(val id: Long, val title: String, val status: Int, val progress: Int)

data class InspectElement(
    val path: String,
    val tag: String,
    val id: String,
    val className: String,
    val text: String,
    val html: String,
    val href: String,
    val src: String,
    val alt: String,
    val value: String,
    val placeholder: String,
    val inputType: String,
    val color: String,
    val bgColor: String,
    val fontSize: String,
    val display: String,
    val visibility: String,
    val opacity: String,
    val attributes: String,
    val outerHTML: String
)

data class KeepEdit(
    val path: String,
    val text: String?,
    val html: String?,
    val href: String?,
    val src: String?,
    val color: String?,
    val bgColor: String?,
    val fontSize: String?,
    val display: String?,
    val visibility: String?,
    val attributes: Map<String, String>?
)

data class RedirectEntry(val from: String, val to: String, var blocked: Boolean = false)

class WebHookInterface(
    private val onLog: (String) -> Unit,
    private val onMedia: (String) -> Unit,
    private val onInspect: (String) -> Unit
) {
    @android.webkit.JavascriptInterface
    fun onRequest(data: String) { onLog(data) }
    
    @android.webkit.JavascriptInterface
    fun onMedia(data: String) { onMedia(data) }
    
    @android.webkit.JavascriptInterface
    fun onInspect(data: String) { onInspect(data) }
}

private fun getDownloadStatusText(status: Int): String {
    return when (status) {
        DownloadManager.STATUS_PENDING -> "Pendente"
        DownloadManager.STATUS_RUNNING -> "Baixando"
        DownloadManager.STATUS_PAUSED -> "Pausado"
        DownloadManager.STATUS_SUCCESSFUL -> "Concluído"
        DownloadManager.STATUS_FAILED -> "Falhou"
        else -> "Desconhecido"
    }
}

private fun jsString(s: String): String {
    return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "") + "'"
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
    var currentUrl   by remember { mutableStateOf(homeUrl) }
    var urlInput     by remember { mutableStateOf(homeUrl) }
    
    var showTools    by remember { mutableStateOf(false) }
    var toolsTab     by remember { mutableIntStateOf(0) }
    var showMenu     by remember { mutableStateOf(false) }
    
    var inspectorMode by remember { mutableStateOf(false) }
    var keepsEnabled by remember { mutableStateOf(true) }
    var inspectData by remember { mutableStateOf<InspectElement?>(null) }
    var keepsList by remember { mutableStateOf(mutableListOf<KeepEdit>()) }
    var redirectsList by remember { mutableStateOf(mutableListOf<RedirectEntry>()) }
    val blockedRedirectPatterns = remember { mutableStateListOf<String>() }

    val prefs = context.getSharedPreferences("rcst_prefs", Context.MODE_PRIVATE)
    var monitorEnabled by remember { mutableStateOf(prefs.getBoolean("monitor_enabled", true)) }

    var requestLogs by remember { mutableStateOf(mutableListOf<RequestLog>()) }
    val blockedKeys = remember { mutableStateListOf<String>() }
    var downloadItems by remember { mutableStateOf(mutableListOf<DownloadItem>()) }
    val trackedDownloadIds = remember { mutableStateListOf<Long>() }
    
    var logIdCounter by remember { mutableStateOf(0L) }
    var jsInput by remember { mutableStateOf("") }
    var hooksInput by remember { mutableStateOf("") }
    var pluginsInput by remember { mutableStateOf("") }
    var mediaLinks by remember { mutableStateOf(mutableListOf<MediaLink>()) }
    var storageData by remember { mutableStateOf("No data fetched.") }

    val pendingRequests = remember { mutableMapOf<String, RequestLog>() }
    val cacheFile = remember { File(context.cacheDir, "rcst_session.json") }
    val hookPrefs = remember { context.getSharedPreferences("rcst_hooks", Context.MODE_PRIVATE) }
    val keepsPrefs = remember { context.getSharedPreferences("rcst_keeps", Context.MODE_PRIVATE) }
    val redirectsPrefs = remember { context.getSharedPreferences("rcst_redirects", Context.MODE_PRIVATE) }

    val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    val mobileUA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun saveRedirects() {
        val arr = JSONArray()
        blockedRedirectPatterns.forEach { arr.put(it) }
        redirectsPrefs.edit().putString("blocked", arr.toString()).apply()
    }

    fun saveKeeps() {
        val arr = JSONArray()
        keepsList.forEach { k ->
            val o = JSONObject()
            o.put("path", k.path)
            k.text?.let { o.put("text", it) }
            k.html?.let { o.put("html", it) }
            k.href?.let { o.put("href", it) }
            k.src?.let { o.put("src", it) }
            k.color?.let { o.put("color", it) }
            k.bgColor?.let { o.put("bgColor", it) }
            k.fontSize?.let { o.put("fontSize", it) }
            k.display?.let { o.put("display", it) }
            k.visibility?.let { o.put("visibility", it) }
            k.attributes?.let { attrs ->
                val attrsObj = JSONObject()
                attrs.forEach { (k2, v2) -> attrsObj.put(k2, v2) }
                o.put("attributes", attrsObj)
            }
            arr.put(o)
        }
        keepsPrefs.edit().putString("keeps", arr.toString()).apply()
    }

    fun isRedirectBlocked(url: String): Boolean {
        if (blockedRedirectPatterns.isEmpty()) return false
        return blockedRedirectPatterns.any { pattern ->
            try {
                val regexPattern = pattern
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .replace("?", "\\?")
                url.matches(Regex(regexPattern, RegexOption.IGNORE_CASE)) || url.contains(pattern, ignoreCase = true)
            } catch (_: Exception) {
                url.contains(pattern, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (cacheFile.exists()) {
            try {
                val arr = JSONArray(cacheFile.readText())
                val list = mutableListOf<RequestLog>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(RequestLog(
                        o.getLong("id"), o.optString("timestamp"), o.optString("type"), o.optString("method"),
                        o.optString("url"), o.optString("headers"), o.optString("body"), o.optInt("status"),
                        o.optString("responseBody"), o.optBoolean("isBlocked")
                    ))
                }
                requestLogs = list
                logIdCounter = list.maxOfOrNull { it.id } ?: 0L
            } catch (_: Exception) {}
        }
        hooksInput = hookPrefs.getString("hooks", "") ?: ""
        pluginsInput = hookPrefs.getString("plugins", "") ?: ""
        
        try {
            val keepsArr = JSONArray(keepsPrefs.getString("keeps", "[]"))
            val kList = mutableListOf<KeepEdit>()
            for (i in 0 until keepsArr.length()) {
                val o = keepsArr.getJSONObject(i)
                val attrsMap = if (o.has("attributes")) {
                    val attrsObj = o.getJSONObject("attributes")
                    val m = mutableMapOf<String, String>()
                    val keys = attrsObj.keys()
                    while (keys.hasNext()) { val k = keys.next(); m[k] = attrsObj.getString(k) }
                    m
                } else null
                kList.add(KeepEdit(
                    o.getString("path"),
                    if(o.has("text")) o.getString("text") else null,
                    if(o.has("html")) o.getString("html") else null,
                    if(o.has("href")) o.getString("href") else null,
                    if(o.has("src")) o.getString("src") else null,
                    if(o.has("color")) o.getString("color") else null,
                    if(o.has("bgColor")) o.getString("bgColor") else null,
                    if(o.has("fontSize")) o.getString("fontSize") else null,
                    if(o.has("display")) o.getString("display") else null,
                    if(o.has("visibility")) o.getString("visibility") else null,
                    attrsMap
                ))
            }
            keepsList = kList
        } catch (_: Exception) {}

        try {
            val redirectsArr = JSONArray(redirectsPrefs.getString("blocked", "[]"))
            for (i in 0 until redirectsArr.length()) {
                blockedRedirectPatterns.add(redirectsArr.getString(i))
            }
        } catch (_: Exception) {}

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        while (isActive) {
            try {
                if (trackedDownloadIds.isNotEmpty()) {
                    val query = DownloadManager.Query()
                    val ids = trackedDownloadIds.toLongArray()
                    query.setFilterById(*ids)
                    val cursor = dm.query(query)
                    val items = mutableListOf<DownloadItem>()
                    while (cursor.moveToNext()) {
                        val idIdx = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                        val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val bytesDownloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        
                        if (idIdx >= 0 && titleIdx >= 0 && statusIdx >= 0 && bytesDownloadedIdx >= 0 && bytesTotalIdx >= 0) {
                            val id = cursor.getLong(idIdx)
                            val title = cursor.getString(titleIdx)
                            val status = cursor.getInt(statusIdx)
                            val bytesDownloaded = cursor.getLong(bytesDownloadedIdx)
                            val bytesTotal = cursor.getLong(bytesTotalIdx)
                            val prog = if (bytesTotal > 0) (bytesDownloaded * 100 / bytesTotal).toInt() else -1
                            items.add(DownloadItem(id, title, status, prog))
                        }
                    }
                    cursor.close()
                    downloadItems = items
                }
            } catch (_: Exception) {}
            delay(1000)
        }
    }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    fun saveLogsCache() {
        scope.launch(Dispatchers.IO) {
            try {
                val arr = JSONArray()
                requestLogs.takeLast(200).forEach { log ->
                    val o = JSONObject()
                    o.put("id", log.id); o.put("timestamp", log.timestamp); o.put("type", log.type)
                    o.put("method", log.method); o.put("url", log.url); o.put("headers", log.headers)
                    o.put("body", log.body); o.put("status", log.status); o.put("responseBody", log.responseBody)
                    o.put("isBlocked", log.isBlocked)
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
            window.__rcst_blocked = new Set();
            var h=window.__rcst_hooks;
            function applyHooks(d){h.forEach(function(f){try{f(d)}catch(e){}});return d;}
            function log(d){try{AndroidWebInterface.onRequest(JSON.stringify(d))}catch(e){}}
            
            function isMediaUrl(url) {
                if(!url) return false;
                var exts = ['.mp4', '.webm', '.ogg', '.mp3', '.wav', '.m3u8', '.jpg', '.jpeg', '.png', '.gif', '.webp', '.svg', '.bmp', '.avi', '.mov', '.flv', '.wmv', '.m4a', '.aac', '.flac'];
                var u = url.split('?')[0].split('#')[0].toLowerCase();
                for(var i=0; i<exts.length; i++) if(u.endsWith(exts[i])) return true;
                return false;
            }
            
            function scanBodyForMedia(body) {
                if(!body || typeof body !== 'string') return;
                var regex = /https?:\/\/[^\s"'<>]+\.(?:mp4|webm|ogg|mp3|wav|m3u8|jpg|jpeg|png|gif|webp|svg|bmp|avi|mov|flv|wmv|m4a|aac|flac)/gi;
                var matches = body.match(regex);
                if(matches) {
                    var seen = new Set();
                    matches.forEach(function(u) {
                        if(!seen.has(u)) {
                            seen.add(u);
                            try { AndroidWebInterface.onMedia(JSON.stringify({type: 'media', mediaType: 'Image', url: u})); } catch(e) {}
                        }
                    });
                }
            }
            
            var oO=XMLHttpRequest.prototype.open,oS=XMLHttpRequest.prototype.send,oH=XMLHttpRequest.prototype.setRequestHeader;
            XMLHttpRequest.prototype.open=function(m,u){this.__m=m;this.__u=u;this.__h={};return oO.apply(this,arguments)};
            XMLHttpRequest.prototype.setRequestHeader=function(n,v){this.__h[n]=v};
            XMLHttpRequest.prototype.send=function(b){
                var self=this;
                var d={type:'xhr',method:this.__m,url:this.__u,headers:JSON.stringify(this.__h),body:b?String(b):''};
                applyHooks(d);
                var key=d.method+':'+d.url;
                if(window.__rcst_blocked.has(key)){
                    log({type:'xhr',method:d.method,url:d.url,headers:d.headers,body:d.body,status:0,responseBody:'Blocked by RCST',blocked:true});
                    return;
                }
                for(var k in d.headers){oH.call(this,k,d.headers[k])}
                log({type:'xhr',method:d.method,url:d.url,headers:d.headers,body:d.body,status:0,responseBody:'',blocked:false});
                this.addEventListener('load',function(){
                    var ct = self.getResponseHeader('content-type') || '';
                    if(isMediaUrl(self.__u) || ct.indexOf('image/') === 0 || ct.indexOf('video/') === 0 || ct.indexOf('audio/') === 0) {
                        var mediaType = ct.indexOf('video/') === 0 ? 'Video' : (ct.indexOf('audio/') === 0 ? 'Audio' : 'Image');
                        try { AndroidWebInterface.onMedia(JSON.stringify({type: 'media', mediaType: mediaType, url: self.__u})); } catch(e) {}
                    }
                    scanBodyForMedia(self.responseText);
                    log({type:'xhr_res',method:self.__m,url:self.__u,headers:'',body:'',status:self.status,responseBody:self.responseText?self.responseText.substring(0,5000):''})
                });
                if(d.method === 'GET' || d.method === 'HEAD'){ return oS.call(this, null); }
                return oS.call(this, d.body || null);
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
                var key=m+':'+u;
                if(window.__rcst_blocked.has(key)){
                    log({type:'fetch',method:m,url:u,headers:d.headers,body:b,status:0,responseBody:'Blocked by RCST',blocked:true});
                    return Promise.reject(new Error('Blocked by user'));
                }
                if(init){
                    init.headers=hd;
                    if(d.body && d.body.length > 0){ init.body = d.body; } else { try { delete init.body; } catch(e) {} }
                }
                log({type:'fetch',method:m,url:u,headers:d.headers,body:b,status:0,responseBody:'',blocked:false});
                return oF.apply(this,arguments).then(function(r){
                    var ct = r.headers.get('content-type') || '';
                    if(isMediaUrl(u) || ct.indexOf('image/') === 0 || ct.indexOf('video/') === 0 || ct.indexOf('audio/') === 0) {
                        var mediaType = ct.indexOf('video/') === 0 ? 'Video' : (ct.indexOf('audio/') === 0 ? 'Audio' : 'Image');
                        try { AndroidWebInterface.onMedia(JSON.stringify({type: 'media', mediaType: mediaType, url: u})); } catch(e) {}
                    }
                    r.clone().text().then(function(t){ 
                        scanBodyForMedia(t);
                        log({type:'fetch_res',method:m,url:u,headers:'',body:'',status:r.status,responseBody:t.substring(0,5000)})
                    });
                    return r
                });
            };
            
            var oW=window.WebSocket;
            window.WebSocket=function(u,p){
                var key='WS:'+u;
                if(window.__rcst_blocked.has(key)){
                    log({type:'ws',method:'WS',url:u,headers:'',body:'',status:0,responseBody:'Blocked by RCST',blocked:true});
                    return { send: function(){}, close: function(){}, addEventListener: function(){}, onopen: null, onmessage: null, onerror: null, onclose: null };
                }
                log({type:'ws',method:'WS',url:u,headers:'',body:'',status:0,responseBody:'',blocked:false});
                var w=p?new oW(u,p):new oW(u);
                var oSws=w.send.bind(w);
                w.send=function(data){log({type:'ws_send',method:'WS',url:u,headers:'',body:data?String(data):'',status:0,responseBody:'',blocked:false});return oSws(data)};
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

    val inspectJs = """
        (function(){
            if(window.__rcst_inspector_listeners_added) return;
            window.__rcst_inspector_listeners_added = true;
            window.__rcst_inspector_active = false;
            document.addEventListener('click', function(e) {
                if(!window.__rcst_inspector_active) return;
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
                var el = e.target;
                if(window.__rcst_last_el) {
                    try { window.__rcst_last_el.style.outline = window.__rcst_last_outline || ''; } catch(err) {}
                }
                try {
                    window.__rcst_last_outline = el.style.outline;
                    el.style.outline = '3px solid #00FFFF';
                    window.__rcst_last_el = el;
                } catch(err) {}
                
                var path = '';
                var current = el;
                while (current && current.nodeType === 1 && current !== document.documentElement) {
                    var sib = current.parentNode ? Array.prototype.indexOf.call(current.parentNode.children, current) + 1 : 1;
                    var sel = current.tagName.toLowerCase();
                    if (current.id) sel += '#' + current.id;
                    path = sel + ':nth-child(' + sib + ')' + (path ? ' > ' + path : '');
                    current = current.parentNode;
                }
                if (!path) path = el.tagName.toLowerCase();
                
                var cs = window.getComputedStyle(el);
                var attrs = {};
                for (var i = 0; i < el.attributes.length; i++) {
                    attrs[el.attributes[i].name] = el.attributes[i].value;
                }
                
                var props = {
                    path: path,
                    tag: el.tagName,
                    id: el.id || '',
                    className: el.className || '',
                    text: (el.innerText || '').substring(0, 500),
                    html: (el.innerHTML || '').substring(0, 2000),
                    href: el.href || '',
                    src: el.src || '',
                    alt: el.alt || '',
                    value: el.value || '',
                    placeholder: el.placeholder || '',
                    inputType: el.type || '',
                    color: cs.color,
                    bgColor: cs.backgroundColor,
                    fontSize: cs.fontSize,
                    display: cs.display,
                    visibility: cs.visibility,
                    opacity: cs.opacity,
                    attributes: JSON.stringify(attrs),
                    outerHTML: (el.outerHTML || '').substring(0, 2000)
                };
                try { AndroidWebInterface.onInspect(JSON.stringify(props)); } catch(err) {}
            }, true);
        })();
    """

    val applyKeepsJs = remember(keepsList, keepsEnabled) {
        if (!keepsEnabled || keepsList.isEmpty()) return@remember ""
        val arr = JSONArray()
        keepsList.forEach { k ->
            val o = JSONObject()
            o.put("path", k.path)
            k.text?.let { o.put("text", it) }
            k.html?.let { o.put("html", it) }
            k.href?.let { o.put("href", it) }
            k.src?.let { o.put("src", it) }
            k.color?.let { o.put("color", it) }
            k.bgColor?.let { o.put("bgColor", it) }
            k.fontSize?.let { o.put("fontSize", it) }
            k.display?.let { o.put("display", it) }
            k.visibility?.let { o.put("visibility", it) }
            k.attributes?.let { attrs ->
                val attrsObj = JSONObject()
                attrs.forEach { (k2, v2) -> attrsObj.put(k2, v2) }
                o.put("attributes", attrsObj)
            }
            arr.put(o)
        }
        """
        (function(){
            try {
                var keeps = $arr;
                keeps.forEach(function(k){
                    var el = document.querySelector(k.path);
                    if(el) {
                        if(k.text) el.innerText = k.text;
                        if(k.html) el.innerHTML = k.html;
                        if(k.href) el.href = k.href;
                        if(k.src) el.src = k.src;
                        if(k.color) el.style.color = k.color;
                        if(k.bgColor) el.style.backgroundColor = k.bgColor;
                        if(k.fontSize) el.style.fontSize = k.fontSize;
                        if(k.display) el.style.display = k.display;
                        if(k.visibility) el.style.visibility = k.visibility;
                        if(k.attributes) {
                            for (var attrName in k.attributes) {
                                el.setAttribute(attrName, k.attributes[attrName]);
                            }
                        }
                    }
                });
            } catch(e) {}
        })();
        """
    }

    val discordFixJs = """
        try {
            Object.defineProperty(navigator, 'platform', { get: function () { return 'Win32'; } });
            window.chrome = { runtime: {} };
        } catch(e) {}
    """

    val domMediaExtractorJs = """
        (function(){
            if(window.__rcst_media_extractor) return;
            window.__rcst_media_extractor = true;
            function extractDomMedia() {
                try {
                    var m = {type: 'dom', images:[], videos:[], audios:[]};
                    var seen = {};
                    function addUnique(arr, url) {
                        if(!url || seen[url]) return;
                        try { seen[url] = true; arr.push(url); } catch(e) {}
                    }
                    document.querySelectorAll('img').forEach(function(e) { 
                        if(e.src) addUnique(m.images, e.src);
                        if(e.dataset && e.dataset.src) addUnique(m.images, e.dataset.src);
                        if(e.dataset && e.dataset.original) addUnique(m.images, e.dataset.original);
                        if(e.srcset) e.srcset.split(',').forEach(function(s) { var u = s.trim().split(' ')[0]; if(u) addUnique(m.images, u); });
                    });
                    document.querySelectorAll('picture source').forEach(function(e) {
                        if(e.srcset) e.srcset.split(',').forEach(function(s) { var u = s.trim().split(' ')[0]; if(u) addUnique(m.images, u); });
                    });
                    document.querySelectorAll('video').forEach(function(e) { 
                        var src = e.src || e.currentSrc; 
                        if(src) addUnique(m.videos, src);
                        if(e.poster) addUnique(m.images, e.poster);
                    });
                    document.querySelectorAll('video source').forEach(function(e) { if(e.src) addUnique(m.videos, e.src); });
                    document.querySelectorAll('audio').forEach(function(e) { var src = e.src || e.currentSrc; if(src) addUnique(m.audios, src); });
                    document.querySelectorAll('audio source').forEach(function(e) { if(e.src) addUnique(m.audios, e.src); });
                    document.querySelectorAll('source').forEach(function(e) { 
                        if(e.src) {
                            var type = (e.type || '').toLowerCase();
                            if(type.indexOf('video') >= 0) addUnique(m.videos, e.src);
                            else if(type.indexOf('audio') >= 0) addUnique(m.audios, e.src);
                            else addUnique(m.images, e.src);
                        }
                    });
                    document.querySelectorAll('[style*="background-image"]').forEach(function(e) {
                        var style = e.getAttribute('style') || '';
                        var match = style.match(/url\\(['"]?([^'")\\)]+)['"]?\\)/);
                        if(match && match[1]) addUnique(m.images, match[1]);
                    });
                    document.querySelectorAll('a[href]').forEach(function(e) {
                        var href = e.href || '';
                        var exts = ['.mp4','.webm','.ogg','.mp3','.wav','.m3u8','.jpg','.jpeg','.png','.gif','.webp','.svg','.bmp','.avi','.mov','.flv','.wmv','.m4a','.aac','.flac'];
                        var lower = href.split('?')[0].split('#')[0].toLowerCase();
                        for(var i=0;i<exts.length;i++) if(lower.endsWith(exts[i])) {
                            if(exts[i]==='.mp4'||exts[i]==='.webm'||exts[i]==='.ogg'||exts[i]==='.avi'||exts[i]==='.mov'||exts[i]==='.flv'||exts[i]==='.wmv'||exts[i]==='.m3u8') addUnique(m.videos, href);
                            else if(exts[i]==='.mp3'||exts[i]==='.wav'||exts[i]==='.m4a'||exts[i]==='.aac'||exts[i]==='.flac') addUnique(m.audios, href);
                            else addUnique(m.images, href);
                            break;
                        }
                    });
                    try { AndroidWebInterface.onMedia(JSON.stringify(m)); } catch(e) {}
                } catch(e) {}
            }
            setTimeout(extractDomMedia, 500);
            setTimeout(extractDomMedia, 2000);
            setInterval(extractDomMedia, 4000);
        })();
    """

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DC.Surface)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
                IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Forward", tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
                
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            var url = urlInput.trim()
                            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
                            webView?.loadUrl(url)
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(20.dp),
                    trailingIcon = { if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) }
                )

                IconButton(onClick = { webView?.reload() }) { Icon(imageVector = Icons.Outlined.Refresh, contentDescription = "Reload", tint = MaterialTheme.colorScheme.onSurface) }
                IconButton(onClick = { showTools = !showTools }) { Icon(imageVector = Icons.Outlined.Code, contentDescription = "Tools", tint = if (showTools) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                IconButton(onClick = { showMenu = true }) { Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface) }
                
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (desktopSite) "Modo Mobile" else "Modo Desktop") },
                        leadingIcon = { Icon(imageVector = if(desktopSite) Icons.Outlined.Smartphone else Icons.Outlined.DesktopWindows, contentDescription = null) },
                        onClick = { vm.setDesktopSite(!desktopSite); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(if (inspectorMode) "Desativar Inspetor" else "Ativar Inspetor") },
                        leadingIcon = { Icon(imageVector = Icons.Outlined.TouchApp, contentDescription = null) },
                        onClick = {
                            inspectorMode = !inspectorMode
                            if (inspectorMode) {
                                webView?.evaluateJavascript(inspectJs, null)
                                webView?.evaluateJavascript("window.__rcst_inspector_active = true;", null)
                            } else {
                                webView?.evaluateJavascript("window.__rcst_inspector_active = false; if(window.__rcst_last_el) { try { window.__rcst_last_el.style.outline = window.__rcst_last_outline || ''; } catch(e){} }", null)
                            }
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (keepsEnabled) "Desativar Keeps" else "Ativar Keeps") },
                        leadingIcon = { Icon(imageVector = Icons.Outlined.Save, contentDescription = null) },
                        onClick = { keepsEnabled = !keepsEnabled; webView?.reload(); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Limpar Cache") },
                        leadingIcon = { Icon(imageVector = Icons.Outlined.DeleteSweep, contentDescription = null) },
                        onClick = {
                            webView?.clearCache(true)
                            webView?.clearHistory()
                            CookieManager.getInstance().removeAllCookies(null)
                            showMenu = false
                        }
                    )
                }
            }
        }

        Row(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.weight(if (showTools) 0.5f else 1f)) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    canGoBack = view.canGoBack()
                                    canGoForward = view.canGoForward()
                                    onScrolled(false)
                                    if (url != null) { 
                                        if (currentUrl != url && currentUrl.isNotEmpty() && !url.startsWith("about:")) {
                                            redirectsList = redirectsList.toMutableList().also { it.add(0, RedirectEntry(currentUrl, url)) }
                                            if (redirectsList.size > 50) redirectsList.removeAt(redirectsList.size - 1)
                                        }
                                        currentUrl = url
                                        urlInput = url 
                                    }
                                    view.evaluateJavascript(discordFixJs, null)
                                    val keysArray = JSONArray()
                                    blockedKeys.forEach { keysArray.put(it) }
                                    view.evaluateJavascript("window.__rcst_blocked = new Set(${keysArray});", null)
                                    if (inspectorMode) {
                                        view.evaluateJavascript(inspectJs, null)
                                        view.evaluateJavascript("window.__rcst_inspector_active = true;", null)
                                    }
                                }
                                override fun onPageFinished(view: WebView, url: String?) {
                                    isLoading = false
                                    canGoBack = view.canGoBack()
                                    canGoForward = view.canGoForward()
                                    if (url != null) urlInput = url
                                    
                                    if (monitorEnabled) {
                                        view.evaluateJavascript("window.__rcst_user_hooks = `$hooksInput`;", null)
                                        view.evaluateJavascript(hookJs, null)
                                    }
                                    if (pluginsInput.isNotEmpty()) {
                                        view.evaluateJavascript("try { eval(`$pluginsInput`); } catch(e) {}", null)
                                    }
                                    if (keepsEnabled && applyKeepsJs.isNotEmpty()) {
                                        view.evaluateJavascript(applyKeepsJs, null)
                                    }
                                    if (inspectorMode) {
                                        view.evaluateJavascript(inspectJs, null)
                                        view.evaluateJavascript("window.__rcst_inspector_active = true;", null)
                                    }
                                    view.evaluateJavascript(domMediaExtractorJs, null)
                                    
                                    view.evaluateJavascript("""
                                        setTimeout(function() {
                                            try {
                                                let res = {};
                                                res.localStorage = JSON.stringify(localStorage);
                                                res.sessionStorage = JSON.stringify(sessionStorage);
                                                AndroidWebInterface.onMedia('STORAGE:' + JSON.stringify(res));
                                            } catch(e) {}
                                        }, 2500);
                                    """, null)
                                }
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val targetUrl = request.url.toString()
                                    if (isRedirectBlocked(targetUrl)) {
                                        return true
                                    }
                                    return false
                                }
                            }
                            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                try {
                                    val request = DownloadManager.Request(Uri.parse(url))
                                    request.setMimeType(mimetype ?: "*/*")
                                    request.addRequestHeader("User-Agent", userAgent ?: "Mozilla/5.0")
                                    val cookies = CookieManager.getInstance().getCookie(url)
                                    if (cookies != null) request.addRequestHeader("Cookie", cookies)
                                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                    request.setTitle(fileName)
                                    request.setDescription("Baixando via RCST Browser")
                                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                                    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                    val downloadId = dm.enqueue(request)
                                    trackedDownloadIds.add(downloadId)
                                } catch (e: Exception) {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val urlObj = URL(url)
                                            val connection = urlObj.openConnection() as HttpURLConnection
                                            connection.requestMethod = "GET"
                                            connection.setRequestProperty("User-Agent", userAgent ?: "Mozilla/5.0")
                                            val cookies = CookieManager.getInstance().getCookie(url)
                                            if (cookies != null) connection.setRequestProperty("Cookie", cookies)
                                            connection.connect()
                                            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                                                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                                                val outputFile = File(downloadsDir, fileName)
                                                val inputStream = connection.inputStream
                                                val outputStream = FileOutputStream(outputFile)
                                                val buffer = ByteArray(4096)
                                                var bytesRead: Int
                                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                                    outputStream.write(buffer, 0, bytesRead)
                                                }
                                                outputStream.flush()
                                                outputStream.close()
                                                inputStream.close()
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean { return true }
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
                                javaScriptCanOpenWindowsAutomatically = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                userAgentString = if (desktopSite) desktopUA else mobileUA
                            }
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            
                            addJavascriptInterface(WebHookInterface(
                                onLog = { dataStr ->
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
                                        val isBlocked = json.optBoolean("blocked", false)
                                        
                                        val key = "$method:$url"
                                        
                                        if (type == "xhr_res" || type == "fetch_res") {
                                            val pending = pendingRequests.remove(key)
                                            val finalLog = pending?.copy(status = status, responseBody = resBody) ?: RequestLog(++logIdCounter, ts, type, method, url, headers, body, status, resBody, false)
                                            requestLogs = requestLogs.toMutableList().also { it.add(0, finalLog) }
                                            if (requestLogs.size > 200) requestLogs.removeAt(requestLogs.size - 1)
                                            saveLogsCache()
                                        } else {
                                            val reqLog = RequestLog(++logIdCounter, ts, type, method, url, headers, body, 0, "", isBlocked)
                                            pendingRequests[key] = reqLog
                                            requestLogs = requestLogs.toMutableList().also { it.add(0, reqLog) }
                                            if (requestLogs.size > 200) requestLogs.removeAt(requestLogs.size - 1)
                                            saveLogsCache()
                                        }
                                    } catch (_: Exception) {}
                                },
                                onMedia = { dataStr ->
                                    try {
                                        if (dataStr.startsWith("STORAGE:")) {
                                            val json = JSONObject(dataStr.substringAfter("STORAGE:"))
                                            val ls = json.optString("localStorage")
                                            val ss = json.optString("sessionStorage")
                                            val cookies = CookieManager.getInstance().getCookie(currentUrl)
                                            storageData = "Cookies:\n${cookies ?: "None"}\n\nLocal Storage:\n$ls\n\nSession Storage:\n$ss"
                                        } else {
                                            val json = JSONObject(dataStr)
                                            val type = json.optString("type")
                                            if (type == "media") {
                                                val mediaType = json.optString("mediaType")
                                                val url = json.optString("url")
                                                if (url.isNotEmpty() && mediaLinks.none { it.url == url }) {
                                                    val fileName = URLUtil.guessFileName(url, null, null)
                                                    mediaLinks = mediaLinks.toMutableList().also { it.add(0, MediaLink(url, mediaType, fileName)) }
                                                }
                                            } else if (type == "dom") {
                                                val currentUrls = mediaLinks.map { it.url }.toMutableSet()
                                                val newList = mediaLinks.toMutableList()
                                                
                                                json.optJSONArray("images")?.let { for (i in 0 until it.length()) { val u = it.getString(i); if (u.isNotEmpty() && !currentUrls.contains(u)) { newList.add(MediaLink(u, "Image", URLUtil.guessFileName(u, null, null))); currentUrls.add(u) } } }
                                                json.optJSONArray("videos")?.let { for (i in 0 until it.length()) { val u = it.getString(i); if (u.isNotEmpty() && !currentUrls.contains(u)) { newList.add(MediaLink(u, "Video", URLUtil.guessFileName(u, null, null))); currentUrls.add(u) } } }
                                                json.optJSONArray("audios")?.let { for (i in 0 until it.length()) { val u = it.getString(i); if (u.isNotEmpty() && !currentUrls.contains(u)) { newList.add(MediaLink(u, "Audio", URLUtil.guessFileName(u, null, null))); currentUrls.add(u) } } }
                                                
                                                mediaLinks = newList
                                            }
                                        }
                                    } catch (_: Exception) {}
                                },
                                onInspect = { dataStr ->
                                    try {
                                        val json = JSONObject(dataStr)
                                        inspectData = InspectElement(
                                            json.optString("path"),
                                            json.optString("tag"),
                                            json.optString("id"),
                                            json.optString("className"),
                                            json.optString("text"),
                                            json.optString("html"),
                                            json.optString("href"),
                                            json.optString("src"),
                                            json.optString("alt"),
                                            json.optString("value"),
                                            json.optString("placeholder"),
                                            json.optString("inputType"),
                                            json.optString("color"),
                                            json.optString("bgColor"),
                                            json.optString("fontSize"),
                                            json.optString("display"),
                                            json.optString("visibility"),
                                            json.optString("opacity"),
                                            json.optString("attributes"),
                                            json.optString("outerHTML")
                                        )
                                    } catch (_: Exception) {}
                                }
                            ), "AndroidWebInterface")
                            loadUrl(homeUrl)
                            webView = this
                        }
                    },
                    update = { view ->
                        view.settings.javaScriptEnabled = jsEnabled
                        view.settings.userAgentString = if (desktopSite) desktopUA else mobileUA
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading && progress < 30) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }

            if (showTools) {
                VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp).background(DC.Border))
                Box(modifier = Modifier.weight(0.5f).background(DC.Bg)) {
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
                        mediaLinks = mediaLinks,
                        storageData = storageData,
                        downloadItems = downloadItems,
                        keepsList = keepsList,
                        onDeleteKeep = { keep ->
                            keepsList = keepsList.toMutableList().also { it.remove(keep) }
                            saveKeeps()
                        },
                        redirectsList = redirectsList,
                        blockedRedirectPatterns = blockedRedirectPatterns,
                        onBlockRedirect = { pattern ->
                            if (!blockedRedirectPatterns.contains(pattern)) {
                                blockedRedirectPatterns.add(pattern)
                                saveRedirects()
                            }
                        },
                        onUnblockRedirect = { pattern ->
                            blockedRedirectPatterns.remove(pattern)
                            saveRedirects()
                        },
                        onAddRedirectPattern = { pattern ->
                            if (pattern.isNotEmpty() && !blockedRedirectPatterns.contains(pattern)) {
                                blockedRedirectPatterns.add(pattern)
                                saveRedirects()
                            }
                        },
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
                        },
                        blockedKeys = blockedKeys,
                        onBlockToggle = { key, block ->
                            val jsKey = "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                            if (block) {
                                if (!blockedKeys.contains(key)) blockedKeys.add(key)
                                webView?.evaluateJavascript("window.__rcst_blocked.add($jsKey);", null)
                            } else {
                                blockedKeys.remove(key)
                                webView?.evaluateJavascript("window.__rcst_blocked.delete($jsKey);", null)
                            }
                        },
                        onDownloadMedia = { url ->
                            try {
                                val fileName = URLUtil.guessFileName(url, null, null)
                                val request = DownloadManager.Request(Uri.parse(url))
                                request.setTitle(fileName)
                                request.setDescription("Mídia extraída pelo RCST")
                                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                val id = dm.enqueue(request)
                                trackedDownloadIds.add(id)
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }
    }

    inspectData?.let { data ->
        InspectorDialog(
            data = data,
            onDismiss = { inspectData = null },
            onApply = { newText, newHtml, newHref, newSrc, newColor, newBgColor, newFontSize, newDisplay, newVisibility, newAttrs, keep ->
                val attrsJs = if (newAttrs != null && newAttrs.isNotEmpty()) {
                    newAttrs.entries.joinToString("") { (k, v) -> 
                        "el.setAttribute(${jsString(k)}, ${jsString(v)});" 
                    }
                } else ""
                val js = """
                    (function(){
                        var el = document.querySelector(${jsString(data.path)});
                        if(el) {
                            ${if(newText != null) "el.innerText = ${jsString(newText)};" else ""}
                            ${if(newHtml != null) "el.innerHTML = ${jsString(newHtml)};" else ""}
                            ${if(newHref != null) "el.href = ${jsString(newHref)};" else ""}
                            ${if(newSrc != null) "el.src = ${jsString(newSrc)};" else ""}
                            ${if(newColor != null) "el.style.color = ${jsString(newColor)};" else ""}
                            ${if(newBgColor != null) "el.style.backgroundColor = ${jsString(newBgColor)};" else ""}
                            ${if(newFontSize != null) "el.style.fontSize = ${jsString(newFontSize)};" else ""}
                            ${if(newDisplay != null) "el.style.display = ${jsString(newDisplay)};" else ""}
                            ${if(newVisibility != null) "el.style.visibility = ${jsString(newVisibility)};" else ""}
                            $attrsJs
                        }
                    })();
                """
                webView?.evaluateJavascript(js, null)
                if (keep) {
                    val newKeep = KeepEdit(data.path, newText, newHtml, newHref, newSrc, newColor, newBgColor, newFontSize, newDisplay, newVisibility, newAttrs)
                    keepsList = keepsList.toMutableList().also { 
                        it.removeAll { k -> k.path == data.path }
                        it.add(newKeep) 
                    }
                    saveKeeps()
                }
                inspectData = null
            }
        )
    }
}

@Composable
private fun InspectorDialog(
    data: InspectElement,
    onDismiss: () -> Unit,
    onApply: (String?, String?, String?, String?, String?, String?, String?, String?, String?, Map<String, String>?, Boolean) -> Unit
) {
    var dialogTab by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf(data.text) }
    var html by remember { mutableStateOf(data.html) }
    var href by remember { mutableStateOf(data.href) }
    var src by remember { mutableStateOf(data.src) }
    var alt by remember { mutableStateOf(data.alt) }
    var value by remember { mutableStateOf(data.value) }
    var placeholder by remember { mutableStateOf(data.placeholder) }
    var inputType by remember { mutableStateOf(data.inputType) }
    var color by remember { mutableStateOf(data.color) }
    var bgColor by remember { mutableStateOf(data.bgColor) }
    var fontSize by remember { mutableStateOf(data.fontSize) }
    var display by remember { mutableStateOf(data.display) }
    var visibility by remember { mutableStateOf(data.visibility) }
    var opacity by remember { mutableStateOf(data.opacity) }
    var id by remember { mutableStateOf(data.id) }
    var className by remember { mutableStateOf(data.className) }
    var keep by remember { mutableStateOf(false) }
    
    var attrMap by remember { 
        mutableStateOf(
            try { 
                JSONObject(data.attributes).let { m -> 
                    val map = mutableMapOf<String, String>()
                    val keys = m.keys()
                    while(keys.hasNext()) { val k = keys.next(); map[k] = m.getString(k) }
                    map
                } 
            } 
            catch (_: Exception) { mutableMapOf() }
        ) 
    }
    var newAttrKey by remember { mutableStateOf("") }
    var newAttrValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Outlined.Code, contentDescription = null, tint = DC.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Inspetor de Elemento", color = DC.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DC.Card,
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Atributos", "Estilo", "Conteúdo", "Avançado").forEachIndexed { idx, tab ->
                        TabButton(tab, dialogTab == idx) { dialogTab = idx }
                    }
                }
                Spacer(Modifier.height(12.dp))
                
                Box(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    when (dialogTab) {
                        0 -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            InfoRow("Tag", data.tag)
                            OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 11.sp, color = DC.White))
                            OutlinedTextField(value = className, onValueChange = { className = it }, label = { Text("Class") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 11.sp, color = DC.White))
                            if (data.href.isNotEmpty() || href.isNotEmpty()) {
                                OutlinedTextField(value = href, onValueChange = { href = it }, label = { Text("Link (href)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 11.sp, color = DC.White))
                            }
                            if (data.src.isNotEmpty() || src.isNotEmpty()) {
                                OutlinedTextField(value = src, onValueChange = { src = it }, label = { Text("Source (src)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 11.sp, color = DC.White))
                            }
                            if (data.alt.isNotEmpty() || alt.isNotEmpty()) {
                                OutlinedTextField(value = alt, onValueChange = { alt = it }, label = { Text("Alt") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 11.sp, color = DC.White))
                            }
                            if (data.value.isNotEmpty() || value.isNotEmpty()) {
                                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Value") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 11.sp, color = DC.White))
                            }
                            if (data.placeholder.isNotEmpty() || placeholder.isNotEmpty()) {
                                OutlinedTextField(value = placeholder, onValueChange = { placeholder = it }, label = { Text("Placeholder") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 11.sp, color = DC.White))
                            }
                            if (data.inputType.isNotEmpty()) {
                                OutlinedTextField(value = inputType, onValueChange = { inputType = it }, label = { Text("Type") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 11.sp, color = DC.White))
                            }
                            
                            Spacer(Modifier.height(4.dp))
                            Text("Atributos Customizados:", color = DC.Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            attrMap.forEach { (k, v) ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("$k:", color = DC.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(80.dp))
                                    OutlinedTextField(value = v, onValueChange = { attrMap = attrMap.toMutableMap().also { m -> m[k] = it } }, modifier = Modifier.weight(1f), singleLine = true, textStyle = TextStyle(fontSize = 10.sp, color = DC.White))
                                    IconButton(onClick = { attrMap = attrMap.toMutableMap().also { m -> m.remove(k) } }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Outlined.Close, contentDescription = null, tint = DC.Error, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = newAttrKey, onValueChange = { newAttrKey = it }, label = { Text("chave") }, modifier = Modifier.weight(1f), singleLine = true, textStyle = TextStyle(fontSize = 10.sp, color = DC.White))
                                Spacer(Modifier.width(4.dp))
                                OutlinedTextField(value = newAttrValue, onValueChange = { newAttrValue = it }, label = { Text("valor") }, modifier = Modifier.weight(1f), singleLine = true, textStyle = TextStyle(fontSize = 10.sp, color = DC.White))
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { 
                                    if (newAttrKey.isNotEmpty()) {
                                        attrMap = attrMap.toMutableMap().also { it[newAttrKey] = newAttrValue }
                                        newAttrKey = ""
                                        newAttrValue = ""
                                    }
                                }) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, tint = DC.Success)
                                }
                            }
                        }
                        1 -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text("Cor do Texto") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 11.sp, color = DC.White))
                            OutlinedTextField(value = bgColor, onValueChange = { bgColor = it }, label = { Text("Cor de Fundo") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 11.sp, color = DC.White))
                            OutlinedTextField(value = fontSize, onValueChange = { fontSize = it }, label = { Text("Tamanho da Fonte") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 11.sp, color = DC.White))
                            OutlinedTextField(value = display, onValueChange = { display = it }, label = { Text("Display") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 11.sp, color = DC.White))
                            OutlinedTextField(value = visibility, onValueChange = { visibility = it }, label = { Text("Visibility") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 11.sp, color = DC.White))
                            OutlinedTextField(value = opacity, onValueChange = { opacity = it }, label = { Text("Opacity") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 11.sp, color = DC.White))
                        }
                        2 -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Texto (innerText)") }, modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 11.sp, color = DC.White))
                            OutlinedTextField(value = html, onValueChange = { html = it }, label = { Text("HTML (innerHTML)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), textStyle = TextStyle(fontSize = 11.sp, color = DC.White, fontFamily = FontFamily.Monospace))
                        }
                        3 -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            InfoRow("Path", data.path)
                            InfoRow("Outer HTML", data.outerHTML.take(500))
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keep, onCheckedChange = { keep = it })
                    Text("Keep (manter edição sempre)", color = DC.White, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                val finalAttrs = if (id != data.id || className != data.className || attrMap.isNotEmpty()) {
                    val m = attrMap.toMutableMap()
                    if (id != data.id) m["id"] = id
                    if (className != data.className) m["class"] = className
                    if (data.alt.isNotEmpty() && alt != data.alt) m["alt"] = alt
                    if (data.value.isNotEmpty() && value != data.value) m["value"] = value
                    if (data.placeholder.isNotEmpty() && placeholder != data.placeholder) m["placeholder"] = placeholder
                    if (data.inputType.isNotEmpty() && inputType != data.inputType) m["type"] = inputType
                    if (m.isNotEmpty()) m else null
                } else null
                onApply(
                    if(text != data.text) text else null,
                    if(html != data.html) html else null,
                    if(href != data.href && (data.href.isNotEmpty() || href.isNotEmpty())) href else null,
                    if(src != data.src && (data.src.isNotEmpty() || src.isNotEmpty())) src else null,
                    if(color != data.color) color else null,
                    if(bgColor != data.bgColor) bgColor else null,
                    if(fontSize != data.fontSize) fontSize else null,
                    if(display != data.display) display else null,
                    if(visibility != data.visibility) visibility else null,
                    finalAttrs,
                    keep
                ) 
            }, colors = ButtonDefaults.buttonColors(containerColor = DC.Primary)) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = DC.Muted) } }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, color = DC.Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = DC.SubText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
    mediaLinks: List<MediaLink>,
    storageData: String,
    downloadItems: List<DownloadItem>,
    keepsList: List<KeepEdit>,
    onDeleteKeep: (KeepEdit) -> Unit,
    redirectsList: List<RedirectEntry>,
    blockedRedirectPatterns: List<String>,
    onBlockRedirect: (String) -> Unit,
    onUnblockRedirect: (String) -> Unit,
    onAddRedirectPattern: (String) -> Unit,
    downloadHtml: () -> Unit,
    downloadSiteZip: () -> Unit,
    blockedKeys: List<String>,
    onBlockToggle: (String, Boolean) -> Unit,
    onDownloadMedia: (String) -> Unit
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val tabsList = listOf("Monitor", "Source", "JS", "Hooks", "Plugins", "Media", "Storage", "Downloads", "Keeps", "Redirects")
    var newRedirectPattern by remember { mutableStateOf("") }
    
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(DC.Surface).padding(horizontal = 4.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabsList.forEachIndexed { index, tab ->
                TabButton(tab, toolsTab == index) { onTabChange(index) }
            }
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
                            val key = "${log.method}:${log.url}"
                            val isCurrentlyBlocked = blockedKeys.contains(key)
                            
                            Card(colors = CardDefaults.cardColors(containerColor = DC.Card), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                                Column(Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(log.type.uppercase(), fontSize = 8.sp, color = typeColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Spacer(Modifier.width(4.dp))
                                        Text(log.method, fontSize = 9.sp, color = DC.White, fontFamily = FontFamily.Monospace)
                                        Spacer(Modifier.width(4.dp))
                                        Text(log.url, fontSize = 9.sp, color = DC.SubText, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        if (log.isBlocked) {
                                            Text("BLOCKED", fontSize = 9.sp, color = DC.Error, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        } else if (log.status > 0) {
                                            Text("${log.status}", fontSize = 9.sp, color = DC.Success, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                    if (expanded) {
                                        Spacer(Modifier.height(4.dp))
                                        if (log.headers.isNotEmpty() && log.headers != "{}") DetailText("Headers:", log.headers)
                                        if (log.body.isNotEmpty()) DetailText("Body:", log.body)
                                        if (log.responseBody.isNotEmpty()) DetailText("Response:", log.responseBody)
                                        Row {
                                            TextButton(onClick = { onBlockToggle(key, !isCurrentlyBlocked) }) {
                                                Text(if (isCurrentlyBlocked) "Unblock" else "Block", color = if (isCurrentlyBlocked) DC.Success else DC.Error, fontSize = 9.sp)
                                            }
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
                    Text("Live JavaScript Editor:", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = jsInput, onValueChange = onJsInputChange,
                        modifier = Modifier.fillMaxWidth().height(120.dp).background(DC.Card),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = DC.White),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedTextColor = DC.White, unfocusedBorderColor = DC.Border, cursorColor = DC.Primary),
                        placeholder = { Text("e.g: document.body.style.backgroundColor = 'red';", color = DC.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                    )
                    Row {
                        Button(onClick = { webView?.evaluateJavascript(jsInput, null) }, colors = ButtonDefaults.buttonColors(containerColor = DC.Success)) { Text("Run") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { webView?.evaluateJavascript(jsInput, null) }, colors = ButtonDefaults.buttonColors(containerColor = DC.Primary)) { Text("Apply Live") }
                    }
                }
            }
            3 -> {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Text("Hook System (JS):", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = hooksInput, onValueChange = onHooksChange,
                        modifier = Modifier.fillMaxWidth().height(120.dp).background(DC.Card),
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
                        modifier = Modifier.fillMaxWidth().height(120.dp).background(DC.Card),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = DC.White),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedTextColor = DC.White, unfocusedBorderColor = DC.Border, cursorColor = DC.Primary),
                        placeholder = { Text("console.log('Plugin loaded!');", color = DC.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                    )
                    Button(onClick = applyPlugins, colors = ButtonDefaults.buttonColors(containerColor = DC.Warning)) { Text("Save & Apply") }
                }
            }
            5 -> {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Media Extractor:", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${mediaLinks.size} items", color = DC.Muted, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (mediaLinks.isEmpty()) {
                        Text("No media found on this page.", color = DC.Muted, fontSize = 11.sp)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(mediaLinks) { media ->
                                Card(colors = CardDefaults.cardColors(containerColor = DC.Card), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = when(media.type) { "Video" -> Icons.Outlined.Videocam; "Audio" -> Icons.Outlined.AudioFile; else -> Icons.Outlined.Image },
                                                contentDescription = null, tint = DC.Primary, modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(media.type, color = DC.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.weight(1f))
                                            Text(media.fileName, color = DC.Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(media.url, color = DC.SubText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Row {
                                            TextButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("Media URL", media.url)) }) { Text("Copy URL", color = DC.Primary, fontSize = 10.sp) }
                                            TextButton(onClick = { onDownloadMedia(media.url) }) { Text("Download", color = DC.Success, fontSize = 10.sp) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            6 -> {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Text("Storage & Cache Inspector:", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(storageData, color = DC.SubText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
                }
            }
            7 -> {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Text("Downloads:", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (downloadItems.isEmpty()) {
                        Text("Nenhum download em andamento ou recente.", color = DC.Muted, fontSize = 11.sp)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(downloadItems) { item ->
                                Card(colors = CardDefaults.cardColors(containerColor = DC.Card), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text(item.title, color = DC.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(getDownloadStatusText(item.status), color = DC.Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.weight(1f))
                                            if (item.progress >= 0) Text("${item.progress}%", color = DC.SubText, fontSize = 10.sp)
                                        }
                                        if (item.status == DownloadManager.STATUS_RUNNING && item.progress >= 0) {
                                            Spacer(Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { item.progress / 100f },
                                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                                color = DC.Primary, trackColor = DC.Border
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            8 -> {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Text("Keeps (Edições Salvas):", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (keepsList.isEmpty()) {
                        Text("Nenhuma edição salva.", color = DC.Muted, fontSize = 11.sp)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(keepsList) { keep ->
                                Card(colors = CardDefaults.cardColors(containerColor = DC.Card), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(keep.path, color = DC.SubText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            IconButton(onClick = { onDeleteKeep(keep) }, modifier = Modifier.size(20.dp)) {
                                                Icon(Icons.Outlined.Delete, contentDescription = null, tint = DC.Error, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                        keep.text?.let { Text("Texto: $it", color = DC.White, fontSize = 10.sp) }
                                        keep.html?.let { Text("HTML: ${it.take(80)}", color = DC.White, fontSize = 10.sp) }
                                        keep.href?.let { Text("Link: $it", color = DC.White, fontSize = 10.sp) }
                                        keep.color?.let { Text("Cor: $it", color = DC.White, fontSize = 10.sp) }
                                        keep.bgColor?.let { Text("Fundo: $it", color = DC.White, fontSize = 10.sp) }
                                        keep.fontSize?.let { Text("Fonte: $it", color = DC.White, fontSize = 10.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            9 -> {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Text("Redirecionamentos:", color = DC.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newRedirectPattern, onValueChange = { newRedirectPattern = it },
                            modifier = Modifier.weight(1f), singleLine = true,
                            textStyle = TextStyle(fontSize = 11.sp, color = DC.White),
                            placeholder = { Text("URL ou padrão (* curinga)...", color = DC.Muted, fontSize = 11.sp) },
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = DC.Border, focusedBorderColor = DC.Primary)
                        )
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = { 
                            onAddRedirectPattern(newRedirectPattern)
                            newRedirectPattern = ""
                        }, colors = ButtonDefaults.buttonColors(containerColor = DC.Primary)) { Text("Bloquear") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Padrões bloqueados:", color = DC.Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (blockedRedirectPatterns.isEmpty()) {
                        Text("Nenhum padrão bloqueado.", color = DC.Muted, fontSize = 10.sp)
                    } else {
                        blockedRedirectPatterns.forEach { pattern ->
                            Card(colors = CardDefaults.cardColors(containerColor = DC.Card), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Row(Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Block, contentDescription = null, tint = DC.Error, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(pattern, color = DC.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    IconButton(onClick = { onUnblockRedirect(pattern) }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Outlined.Close, contentDescription = null, tint = DC.Muted, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Redirecionamentos detectados:", color = DC.Warning, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    if (redirectsList.isEmpty()) {
                        Text("Nenhum redirecionamento detectado.", color = DC.Muted, fontSize = 10.sp)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(redirectsList) { redirect ->
                                Card(colors = CardDefaults.cardColors(containerColor = DC.Card), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text("De: ${redirect.from}", color = DC.Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Para: ${redirect.to}", color = DC.SubText, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Row {
                                            TextButton(onClick = { onBlockRedirect(redirect.to) }) { 
                                                Text("Bloquear destino", color = DC.Error, fontSize = 9.sp) 
                                            }
                                            TextButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("URL", redirect.to)) }) { 
                                                Text("Copiar", color = DC.Primary, fontSize = 9.sp) 
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
