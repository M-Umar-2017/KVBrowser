package com.example.browser

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    BrowserTheme(darkMode) {
        BrowserApp(engine, darkMode, { value -> darkMode = value; prefs.edit().putBoolean("dark_mode", value).apply() }, { value -> engine = value; prefs.edit().putString("engine", value).apply() })
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
private fun BrowserApp(engine: String, darkMode: Boolean, onDarkModeChanged: (Boolean) -> Unit, onEngineChanged: (String) -> Unit) {
    val context = LocalContext.current
    var address by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

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
        webView?.loadUrl(target)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(Page),
        bottomBar = {
            BrowserNavigationBar(
                webView = webView,
                currentUrl = currentUrl,
                onHome = { webView?.stopLoading(); currentUrl = ""; address = "" },
                onClose = { webView?.stopLoading() }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.weight(1f).height(54.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    leadingIcon = { Icon(Icons.Default.Search, "Search", tint = Purple) },
                    trailingIcon = {
                        if (address.isNotEmpty()) IconButton(onClick = { address = "" }) { Icon(Icons.Default.Close, "Clear") }
                    },
                    placeholder = { Text("Search or enter address", style = MaterialTheme.typography.bodyMedium) },
                    shape = RoundedCornerShape(17.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { navigate(address) })
                )
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More", tint = Ink) }
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
                        DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { showMenu = false; showSettings = true })
                        DropdownMenuItem(text = { Text("About") }, leadingIcon = { Icon(Icons.Default.Shield, null) }, onClick = { showMenu = false; showAbout = true })
                    }
                }
            }
            if (isLoading) CircularProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Purple, strokeWidth = 2.dp)
            if (currentUrl.isEmpty()) {
                CompactStartPage(onNavigate = ::navigate)
            } else {
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
                                }
                                override fun onPageFinished(view: WebView, url: String) {
                                    isLoading = false
                                    currentUrl = url
                                    address = compactUrl(url)
                                }
                            }
                            webChromeClient = WebChromeClient()
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
                            webView = this
                            loadUrl(currentUrl)
                        }
                    },
                    update = { view ->
                        webView = view
                        if (view.url != currentUrl && currentUrl.isNotEmpty()) view.loadUrl(currentUrl)
                    }
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(darkMode, engine, onDarkModeChanged, onEngineChanged) { showSettings = false }
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
private fun CompactStartPage(onNavigate: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = Ink, modifier = Modifier.height(64.dp).width(64.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("✦", color = Purple, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(14.dp))
        Text("Browse beautifully.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        Text("Fast, calm, and ready for the web.", color = Color(0xFF6B7280), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = SoftPurple), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                Text("Quick start", color = Purple, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Text("Use the search bar above to find anything online.", color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickLink("Wikipedia", "https://wikipedia.org", onNavigate)
            QuickLink("YouTube", "https://youtube.com", onNavigate)
        }
    }
}

@Composable
private fun QuickLink(label: String, url: String, onNavigate: (String) -> Unit) {
    Button(onClick = { onNavigate(url) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink), shape = RoundedCornerShape(12.dp), contentPadding = ButtonDefaults.ContentPadding) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SettingsDialog(darkMode: Boolean, engine: String, onDarkModeChanged: (Boolean) -> Unit, onEngineChanged: (String) -> Unit, onDismiss: () -> Unit) {
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
private fun BrowserNavigationBar(webView: WebView?, currentUrl: String, onHome: () -> Unit, onClose: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 8.dp, modifier = Modifier.navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { webView?.goBack() }, enabled = webView?.canGoBack() == true) { Icon(Icons.Default.ArrowBack, "Back", tint = if (webView?.canGoBack() == true) Ink else Color.LightGray) }
            IconButton(onClick = { webView?.goForward() }, enabled = webView?.canGoForward() == true) { Icon(Icons.Default.ArrowForward, "Forward", tint = if (webView?.canGoForward() == true) Ink else Color.LightGray) }
            IconButton(onClick = onHome) { Icon(Icons.Default.Home, "Home", tint = Purple) }
            Text(if (currentUrl.isEmpty()) "Start" else "Page", color = Color(0xFF6B7280), style = MaterialTheme.typography.labelSmall)
            IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, "Refresh", tint = Ink) }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Stop", tint = Ink) }
        }
    }
}
