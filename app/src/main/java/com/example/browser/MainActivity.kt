package com.example.browser

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import coil.compose.AsyncImage
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

private val Ink = Color(0xFF0B1220)
private val Purple = Color(0xFF7057E8)
private val SoftPurple = Color(0xFFEDEAFF)
private val Page = Color(0xFFF6F7FB)
private const val TabExpiryMs = 24L * 60L * 60L * 1000L
private const val MostVisitedExpiryMs = 60L * 60L * 1000L

data class BrowserTab(val id: Long, val url: String, val title: String, val lastActive: Long, val inactive: Boolean)
data class HomeShortcut(val id: Long, val title: String, val url: String)
data class VisitEntry(val url: String, val title: String, val count: Int, val lastVisited: Long)

private fun loadTabs(prefs: SharedPreferences): List<BrowserTab> {
    val now = System.currentTimeMillis()
    val stored = runCatching { JSONArray(prefs.getString("tabs", "[]")) }.getOrElse { JSONArray() }
    return (0 until stored.length()).mapNotNull { index ->
        runCatching {
            val item = stored.getJSONObject(index)
            val lastActive = item.optLong("lastActive", now)
            if (item.optBoolean("inactive", false) && now - lastActive >= TabExpiryMs) null else BrowserTab(item.getLong("id"), item.optString("url"), item.optString("title"), lastActive, item.optBoolean("inactive", false))
        }.getOrNull()
    }
}

private fun saveTabs(prefs: SharedPreferences, tabs: List<BrowserTab>) {
    val array = JSONArray()
    tabs.forEach { tab ->
        array.put(JSONObject().apply { put("id", tab.id); put("url", tab.url); put("title", tab.title); put("lastActive", tab.lastActive); put("inactive", tab.inactive) })
    }
    prefs.edit().putString("tabs", array.toString()).apply()
}

private fun loadShortcuts(prefs: SharedPreferences): List<HomeShortcut> {
    val stored = runCatching { JSONArray(prefs.getString("shortcuts", "[]")) }.getOrElse { JSONArray() }
    return (0 until stored.length()).mapNotNull { index -> runCatching { val item = stored.getJSONObject(index); HomeShortcut(item.getLong("id"), item.optString("title"), item.optString("url")) }.getOrNull() }
}

private fun saveShortcuts(prefs: SharedPreferences, shortcuts: List<HomeShortcut>) {
    val array = JSONArray()
    shortcuts.forEach { shortcut -> array.put(JSONObject().apply { put("id", shortcut.id); put("title", shortcut.title); put("url", shortcut.url) }) }
    prefs.edit().putString("shortcuts", array.toString()).apply()
}

private fun loadVisits(prefs: SharedPreferences): List<VisitEntry> {
    val now = System.currentTimeMillis()
    val stored = runCatching { JSONArray(prefs.getString("visits", "[]")) }.getOrElse { JSONArray() }
    return (0 until stored.length()).mapNotNull { index -> runCatching { val item = stored.getJSONObject(index); VisitEntry(item.optString("url"), item.optString("title"), item.optInt("count", 1), item.optLong("lastVisited")) }.getOrNull() }.filter { now - it.lastVisited < MostVisitedExpiryMs }
}

private fun saveVisits(prefs: SharedPreferences, visits: List<VisitEntry>) {
    val array = JSONArray()
    visits.forEach { visit -> array.put(JSONObject().apply { put("url", visit.url); put("title", visit.title); put("count", visit.count); put("lastVisited", visit.lastVisited) }) }
    prefs.edit().putString("visits", array.toString()).apply()
}

private fun faviconUrl(url: String): String = runCatching { "https://www.google.com/s2/favicons?domain=${Uri.parse(url).host}&sz=96" }.getOrDefault("")

private fun compactUrl(raw: String): String {
    val value = raw.trim()
    val parsed = runCatching { Uri.parse(value) }.getOrNull()
    val host = parsed?.host
    if (host.isNullOrBlank()) return value.removePrefix("https://").removePrefix("http://").removeSuffix("/")
    val cleanHost = host.removePrefix("www.")
    val path = parsed.path.orEmpty().trimEnd('/')
    return cleanHost + path
}

private fun normalizeInput(input: String): String = input.trim().replace(Regex("\\s*\\.\\s*"), ".")

private val engines = listOf("Bing", "Google", "DuckDuckGo", "Yahoo", "Yandex", "Ecosia", "Baidu")

private fun searchUrl(engine: String, query: String): String {
    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
    return when (engine) {
        "Bing" -> "https://www.bing.com/search?q=$encoded"
        "Google" -> "https://www.google.com/search?q=$encoded"
        "Yahoo" -> "https://search.yahoo.com/search?p=$encoded"
        "Yandex" -> "https://yandex.com/search/?text=$encoded"
        "Ecosia" -> "https://www.ecosia.org/search?q=$encoded"
        "Baidu" -> "https://www.baidu.com/s?wd=$encoded"
        else -> "https://html.duckduckgo.com/html/?q=$encoded"
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BrowserRoot() }
    }
}

@Composable
private fun BrowserRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("browser_settings", android.content.Context.MODE_PRIVATE) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var engine by remember { mutableStateOf(prefs.getString("engine", "DuckDuckGo") ?: "DuckDuckGo") }
    var showMostVisited by remember { mutableStateOf(prefs.getBoolean("show_most_visited", true)) }
    BrowserTheme(darkMode) {
        BrowserApp(engine, darkMode, showMostVisited, { value -> darkMode = value; prefs.edit().putBoolean("dark_mode", value).apply() }, { value -> engine = value; prefs.edit().putString("engine", value).apply() }, { value -> showMostVisited = value; prefs.edit().putBoolean("show_most_visited", value).apply() })
    }
}

@Composable
private fun BrowserTheme(darkMode: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkMode) androidx.compose.material3.darkColorScheme(primary = Color(0xFFB9A7FF), background = Color(0xFF101116), surface = Color(0xFF1B1C22), onSurface = Color.White, onBackground = Color.White) else androidx.compose.material3.lightColorScheme(primary = Purple, onPrimary = Color.White, background = Page, surface = Color.White, onSurface = Ink, onBackground = Ink),
        content = content
    )
}

@Composable
private fun BrowserApp(engine: String, darkMode: Boolean, showMostVisited: Boolean, onDarkModeChanged: (Boolean) -> Unit, onEngineChanged: (String) -> Unit, onMostVisitedChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("browser_settings", android.content.Context.MODE_PRIVATE) }
    var tabs by remember { mutableStateOf(loadTabs(prefs).ifEmpty { listOf(BrowserTab(System.currentTimeMillis(), "", "New tab", System.currentTimeMillis(), false)) }) }
    var shortcuts by remember { mutableStateOf(loadShortcuts(prefs)) }
    var visits by remember { mutableStateOf(loadVisits(prefs)) }
    var selectedShortcutId by remember { mutableStateOf<Long?>(null) }
    var showAddShortcut by remember { mutableStateOf(false) }
    var showManageShortcuts by remember { mutableStateOf(false) }
    var selectedTabId by remember { mutableStateOf(tabs.first().id) }
    var address by remember { mutableStateOf(tabs.first().url.takeIf { it.isNotEmpty() }?.let(::compactUrl) ?: "") }
    var currentUrl by remember { mutableStateOf(tabs.first().url) }
    var isLoading by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    fun persistTabs() = saveTabs(prefs, tabs)
    fun persistShortcuts() = saveShortcuts(prefs, shortcuts)
    fun addShortcut(url: String, title: String = "") {
        val normalized = normalizeInput(url)
        if (normalized.isBlank()) return
        val target = if (normalized.startsWith("http://", true) || normalized.startsWith("https://", true)) normalized else "https://$normalized"
        if (shortcuts.none { it.url.equals(target, true) }) {
            shortcuts = shortcuts + HomeShortcut(System.currentTimeMillis(), title.ifBlank { Uri.parse(target).host ?: compactUrl(target) }, target)
            persistShortcuts()
        }
    }
    fun moveShortcut(id: Long, direction: Int) {
        val index = shortcuts.indexOfFirst { it.id == id }
        val target = index + direction
        if (index >= 0 && target in shortcuts.indices) {
            val updated = shortcuts.toMutableList().apply { add(target, removeAt(index)) }
            shortcuts = updated
            persistShortcuts()
        }
    }
    fun recordVisit(url: String, title: String) {
        if (url.isBlank()) return
        val now = System.currentTimeMillis()
        val existing = visits.firstOrNull { it.url == url }
        visits = if (existing == null) visits + VisitEntry(url, title, 1, now) else visits.map { if (it.url == url) it.copy(title = title.ifBlank { it.title }, count = it.count + 1, lastVisited = now) else it }
        saveVisits(prefs, visits)
    }
    fun refreshNavigationState(view: WebView? = webView) {
        canGoBack = view?.canGoBack() == true
        canGoForward = view?.canGoForward() == true
    }
    fun updateCurrentTab(url: String, title: String = "") {
        val now = System.currentTimeMillis()
        tabs = tabs.map { if (it.id == selectedTabId) it.copy(url = url, title = title.ifBlank { it.title }, lastActive = now, inactive = false) else it }
        persistTabs()
    }
    fun createTab() {
        val tab = BrowserTab(System.currentTimeMillis(), "", "New tab", System.currentTimeMillis(), false)
        tabs = tabs + tab
        selectedTabId = tab.id
        currentUrl = ""
        address = ""
        persistTabs()
    }
    fun closeTab(id: Long) {
        val remaining = tabs.filterNot { it.id == id }
        tabs = if (remaining.isEmpty()) listOf(BrowserTab(System.currentTimeMillis(), "", "New tab", System.currentTimeMillis(), false)) else remaining
        if (selectedTabId == id) {
            val next = tabs.first()
            selectedTabId = next.id
            currentUrl = next.url
            address = next.url.takeIf { it.isNotEmpty() }?.let(::compactUrl) ?: ""
        }
        persistTabs()
    }
    fun switchTab(tab: BrowserTab) {
        selectedTabId = tab.id
        currentUrl = tab.url
        address = tab.url.takeIf { it.isNotEmpty() }?.let(::compactUrl) ?: ""
        tabs = tabs.map { if (it.id == tab.id) it.copy(lastActive = System.currentTimeMillis(), inactive = false) else it }
        persistTabs()
        showTabs = false
    }
    DisposableEffect(Unit) {
        onDispose { saveTabs(prefs, tabs.map { it.copy(inactive = true) }) }
    }

    fun navigate(input: String) {
        val value = normalizeInput(input)
        if (value.isEmpty()) return
        val target = when {
            value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) -> value
            value.contains(".") && !value.contains(" ") -> "https://$value"
            else -> searchUrl(engine, value)
        }
        currentUrl = target
        address = compactUrl(target)
        updateCurrentTab(target)
        webView?.loadUrl(target)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        bottomBar = {
            BrowserNavigationBar(
                webView = webView,
                tabCount = tabs.size,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onBack = { webView?.goBack(); refreshNavigationState() },
                onForward = { webView?.goForward(); refreshNavigationState() },
                onHome = { webView?.stopLoading(); webView?.loadUrl("about:blank"); currentUrl = ""; address = ""; canGoBack = false; canGoForward = false; selectedShortcutId = null; updateCurrentTab("") },
                onTabs = { showTabs = true }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp, max = 54.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    leadingIcon = { Icon(Icons.Default.Search, "Search", tint = Purple) },
                    trailingIcon = {
                        if (address.isNotEmpty()) IconButton(onClick = { address = "" }) { Icon(Icons.Default.Close, "Clear") }
                    },
                    placeholder = { Text("Search or enter address", style = MaterialTheme.typography.bodyMedium) },
                    shape = RoundedCornerShape(23.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { navigate(address) })
                )
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurface) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Share page") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = {
                            showMenu = false
                            val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, currentUrl) }
                            context.startActivity(Intent.createChooser(share, "Share page"))
                        })
                        DropdownMenuItem(text = { Text("Copy address") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = {
                            showMenu = false
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Address", currentUrl))
                        })
                        DropdownMenuItem(text = { Text("Add shortcut") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { showMenu = false; showAddShortcut = true })
                        if (selectedShortcutId != null && currentUrl.isBlank()) DropdownMenuItem(text = { Text("Remove selected shortcut") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { shortcuts = shortcuts.filterNot { it.id == selectedShortcutId }; selectedShortcutId = null; persistShortcuts(); showMenu = false })
                        DropdownMenuItem(text = { Text("Manage shortcuts") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { showMenu = false; showManageShortcuts = true })
                        DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { showMenu = false; showSettings = true })
                        DropdownMenuItem(text = { Text("About") }, leadingIcon = { Icon(Icons.Default.Shield, null) }, onClick = { showMenu = false; showAbout = true })
                    }
                }
            }
            if (isLoading) CircularProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Purple, strokeWidth = 2.dp)
            if (currentUrl.isEmpty()) {
                CompactStartPage(darkMode = darkMode, shortcuts = shortcuts, visits = visits, showMostVisited = showMostVisited, selectedShortcutId = selectedShortcutId, onSelectShortcut = { selectedShortcutId = if (selectedShortcutId == it.id) null else it.id }, onNavigate = ::navigate)
            } else {
                key(selectedTabId) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.builtInZoomControls = false
                            settings.displayZoomControls = false
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.setSupportMultipleWindows(false)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
                                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                    isLoading = true
                                    currentUrl = url
                                    address = compactUrl(url)
                                    updateCurrentTab(url)
                                    refreshNavigationState(view)
                                }
                                override fun onPageFinished(view: WebView, url: String) {
                                    isLoading = false
                                    currentUrl = url
                                    address = compactUrl(url)
                                    updateCurrentTab(url, view.title ?: "")
                                    recordVisit(url, view.title ?: compactUrl(url))
                                    refreshNavigationState(view)
                                }
                            }
                            webChromeClient = WebChromeClient()
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) WebSettingsCompat.setForceDark(settings, if (darkMode) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF)
                            webView = this
                            loadUrl(currentUrl)
                        }
                    },
                    update = { view ->
                        webView = view
                        refreshNavigationState(view)
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) WebSettingsCompat.setForceDark(view.settings, if (darkMode) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF)
                        if (view.url != currentUrl && currentUrl.isNotEmpty()) view.loadUrl(currentUrl)
                        }
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(darkMode, engine, showMostVisited, onDarkModeChanged, onEngineChanged, onMostVisitedChanged) { showSettings = false }
    }
    if (showAddShortcut) {
        AddShortcutDialog(initialUrl = currentUrl, initialTitle = webView?.title ?: "", onAdd = { title, url -> addShortcut(url, title); showAddShortcut = false }, onDismiss = { showAddShortcut = false })
    }
    if (showManageShortcuts) {
        ShortcutManagerDialog(shortcuts, selectedShortcutId, onSelect = { selectedShortcutId = it }, onDelete = { id -> shortcuts = shortcuts.filterNot { it.id == id }; selectedShortcutId = null; persistShortcuts() }, onMove = ::moveShortcut, onDismiss = { showManageShortcuts = false })
    }

    if (showTabs) {
        TabOverviewDialog(tabs, selectedTabId, onSelect = ::switchTab, onCloseTab = ::closeTab, onNewTab = { showTabs = false; createTab() }, onDismiss = { showTabs = false })
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About") },
            text = { Text("KVB is a lightweight browser for everyday web browsing.") },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun CompactStartPage(
    darkMode: Boolean,
    shortcuts: List<HomeShortcut>,
    visits: List<VisitEntry>,
    showMostVisited: Boolean,
    selectedShortcutId: Long?,
    onSelectShortcut: (HomeShortcut) -> Unit,
    onNavigate: (String) -> Unit
) {
    val visibleShortcuts = shortcuts.take(8)
    val visibleMostVisited = if (showMostVisited) visits.filter { it.count > 3 }.sortedByDescending { it.count }.distinctBy { it.url }.take(5) else emptyList()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(18.dp), color = Ink, modifier = Modifier.height(72.dp).width(108.dp)) {
            Image(painter = painterResource(id = R.drawable.kvb_logo), contentDescription = "KVB logo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.height(10.dp))
        Text("Browse beautifully.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("Fast, calm, and ready for the web.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = if (darkMode) MaterialTheme.colorScheme.surfaceVariant else SoftPurple), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp)) {
                Text("Quick start", color = Purple, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Text("Use the search bar above to find anything online.", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Shortcuts", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        if (visibleShortcuts.isEmpty()) {
            Text("Add shortcuts from the three-dot menu.", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                visibleShortcuts.forEach { shortcut -> ShortcutIcon(shortcut, selectedShortcutId == shortcut.id, onSelectShortcut, onNavigate) }
            }
        }
        if (showMostVisited) {
            Spacer(Modifier.height(18.dp))
            Text("Most visited", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            if (visibleMostVisited.isEmpty()) {
                Text("Your frequently visited sites will appear here.", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    visibleMostVisited.forEach { visit ->
                        ShortcutIcon(HomeShortcut(-visit.url.hashCode().toLong(), visit.title, visit.url), false, {}, onNavigate)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutIcon(shortcut: HomeShortcut, selected: Boolean, onSelect: (HomeShortcut) -> Unit, onNavigate: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
        Surface(shape = CircleShape, color = if (selected) SoftPurple else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(50.dp).clickable { if (selected) onNavigate(shortcut.url) else onSelect(shortcut) }) {
            AsyncImage(model = faviconUrl(shortcut.url), contentDescription = shortcut.title, modifier = Modifier.fillMaxSize().padding(8.dp).clip(CircleShape))
        }
        Text(shortcut.title.ifBlank { compactUrl(shortcut.url) }, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AddShortcutDialog(initialUrl: String, initialTitle: String, onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add shortcut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text("Name") }, placeholder = { Text("YouTube") })
                OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true, label = { Text("URL") }, placeholder = { Text("youtube.com") })
            }
        },
        confirmButton = { TextButton(enabled = url.isNotBlank(), onClick = { onAdd(title, url) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ShortcutManagerDialog(shortcuts: List<HomeShortcut>, selectedId: Long?, onSelect: (Long) -> Unit, onDelete: (Long) -> Unit, onMove: (Long, Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage shortcuts") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(shortcuts, key = { it.id }) { shortcut ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(38.dp).clickable { onSelect(shortcut.id) }) {
                            AsyncImage(model = faviconUrl(shortcut.url), contentDescription = shortcut.title, modifier = Modifier.fillMaxSize().padding(7.dp).clip(CircleShape))
                        }
                        Text(shortcut.title.ifBlank { compactUrl(shortcut.url) }, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                        IconButton(onClick = { onMove(shortcut.id, -1) }) { Icon(Icons.Default.ArrowUpward, "Move up") }
                        IconButton(onClick = { onMove(shortcut.id, 1) }) { Icon(Icons.Default.ArrowDownward, "Move down") }
                        if (selectedId == shortcut.id) {
                            TextButton(onClick = { onDelete(shortcut.id) }) { Icon(Icons.Default.Delete, "Remove shortcut"); Spacer(Modifier.width(4.dp)); Text("Remove") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun SettingsDialog(darkMode: Boolean, engine: String, showMostVisited: Boolean, onDarkModeChanged: (Boolean) -> Unit, onEngineChanged: (String) -> Unit, onMostVisitedChanged: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Text("Appearance", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (darkMode) "Dark mode" else "Light mode")
                    androidx.compose.material3.Switch(checked = darkMode, onCheckedChange = onDarkModeChanged)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Show most visited sites")
                    androidx.compose.material3.Switch(checked = showMostVisited, onCheckedChange = onMostVisitedChanged)
                }
                Spacer(Modifier.height(8.dp))
                Text("Search engine", fontWeight = FontWeight.Bold)
                engines.forEach { option ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = engine == option, onClick = { onEngineChanged(option) })
                        Text(option)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun TabOverviewDialog(tabs: List<BrowserTab>, selectedTabId: Long, onSelect: (BrowserTab) -> Unit, onCloseTab: (Long) -> Unit, onNewTab: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tabs (${tabs.size})") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(tabs, key = { it.id }) { tab ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = if (tab.id == selectedTabId) SoftPurple else MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        onClick = { onSelect(tab) }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(tab.title.ifBlank { "New tab" }, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(if (tab.url.isBlank()) "New tab" else compactUrl(tab.url), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                            IconButton(onClick = { onCloseTab(tab.id) }) { Icon(Icons.Default.Close, "Close tab") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onNewTab) { Text("New tab") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun BrowserNavigationBar(webView: WebView?, tabCount: Int, canGoBack: Boolean, canGoForward: Boolean, onBack: () -> Unit, onForward: () -> Unit, onHome: () -> Unit, onTabs: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 5.dp, modifier = Modifier.navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = canGoBack) { Icon(Icons.Default.ArrowBack, "Back", tint = if (canGoBack) Ink else Color.LightGray) }
            IconButton(onClick = onForward, enabled = canGoForward) { Icon(Icons.Default.ArrowForward, "Forward", tint = if (canGoForward) Ink else Color.LightGray) }
            IconButton(onClick = onHome) { Icon(Icons.Default.Home, "Home", tint = Purple) }
            IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, "Refresh", tint = Ink) }
            Box {
                IconButton(onClick = onTabs) { Icon(Icons.Default.Layers, "Tabs", tint = Purple) }
                Text(tabCount.toString(), style = MaterialTheme.typography.labelSmall, color = Purple, modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp))
            }
        }
    }
}
