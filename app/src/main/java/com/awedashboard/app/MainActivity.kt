package com.awedashboard.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private val dashboardUrl = "http://192.168.68.141/dashboard/awe-home.html"

    private data class UrlPreset(val label: String, val url: String)
    private val urlPresets = listOf(
        UrlPreset("🏠  Home Server", "http://192.168.68.141/dashboard/awe-home.html"),
        UrlPreset("🐙  GitHub Pages", "https://kavanagh-rob.github.io/Pages/awe-dashboard.html")
    )

    // Secret menu: press 1-2-3 in sequence
    private val secretSequence = listOf(
        KeyEvent.KEYCODE_1,
        KeyEvent.KEYCODE_2,
        KeyEvent.KEYCODE_3
    )
    private val inputBuffer = mutableListOf<Int>()
    private val bufferResetHandler = Handler(Looper.getMainLooper())
    private val bufferResetRunnable = Runnable { inputBuffer.clear() }
    private var secretMenuShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFullscreen()
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        webView = findViewById(R.id.webView)

        setupWebView()
        webView.loadUrl(dashboardUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            databaseEnabled = true
            setSupportZoom(false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                view?.evaluateJavascript("""
                    (function() {
                        var first = document.querySelector('[tabindex]');
                        if (first) first.focus();
                    })();
                """.trimIndent(), null)
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    view?.loadData(getErrorHtml(
                        error?.description?.toString() ?: "Unknown error"
                    ), "text/html", "UTF-8")
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean {
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
            }
        }
    }

    /**
     * ONLY intercept number keys for the 1-2-3 secret menu.
     * Everything else — back, arrows, enter — goes straight to WebView.
     */
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event != null && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2,
                KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5,
                KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8,
                KeyEvent.KEYCODE_9 -> {
                    checkSecretSequence(event.keyCode)
                    return true
                }
            }
        }
        if (event != null && event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2,
                KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5,
                KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8,
                KeyEvent.KEYCODE_9 -> {
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun checkSecretSequence(keyCode: Int) {
        bufferResetHandler.removeCallbacks(bufferResetRunnable)
        bufferResetHandler.postDelayed(bufferResetRunnable, 3000)

        inputBuffer.add(keyCode)

        if (inputBuffer.size >= secretSequence.size) {
            val tail = inputBuffer.takeLast(secretSequence.size)
            if (tail == secretSequence) {
                inputBuffer.clear()
                bufferResetHandler.removeCallbacks(bufferResetRunnable)
                if (!secretMenuShowing) showSecretMenu()
            }
        }

        if (inputBuffer.size > 10) inputBuffer.removeAt(0)
    }

    // ── SECRET MENU ──

    private fun showSecretMenu() {
        if (secretMenuShowing) return
        secretMenuShowing = true

        val items = arrayOf(
            "🔗  Switch Dashboard Source",
            "\uD83D\uDDD1  Clear Cache & Reload",
            "\uD83D\uDD04  Hard Reload (bypass cache)",
            "\uD83C\uDF10  Enter Custom URL",
            "\u2139\uFE0F   App Info",
            "✖  Cancel"
        )

        AlertDialog.Builder(this, R.style.SecretMenuTheme)
            .setTitle("⚙ Secret Menu")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showUrlPresets()
                    1 -> clearCacheAndReload()
                    2 -> hardReload()
                    3 -> showUrlInput()
                    4 -> showAppInfo()
                    5 -> { /* cancel */ }
                }
            }
            .setOnDismissListener { secretMenuShowing = false }
            .show()
    }

    private fun showUrlPresets() {
        val currentUrl = webView.url ?: ""
        val labels = urlPresets.map { preset ->
            val active = if (currentUrl == preset.url ||
                currentUrl.startsWith(preset.url.substringBefore("?"))) " ✓" else ""
            "${preset.label}$active\n${preset.url}"
        }.toTypedArray()

        AlertDialog.Builder(this, R.style.SecretMenuTheme)
            .setTitle("🔗 Switch Dashboard Source")
            .setItems(labels) { _, which ->
                val selected = urlPresets[which]
                webView.loadUrl(selected.url)
                Toast.makeText(this, "Loading: ${selected.label.drop(4)}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearCacheAndReload() {
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        webView.reload()
        Toast.makeText(this, "Cache cleared & reloaded", Toast.LENGTH_SHORT).show()
    }

    private fun hardReload() {
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.reload()
        Handler(Looper.getMainLooper()).postDelayed({
            webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        }, 3000)
        Toast.makeText(this, "Hard reload (no cache)", Toast.LENGTH_SHORT).show()
    }

    private fun showUrlInput() {
        val input = EditText(this).apply {
            setText(webView.url ?: dashboardUrl)
            setTextColor(0xFFE8E8F0.toInt())
            setHintTextColor(0xFF4A4A5A.toInt())
            setBackgroundColor(0xFF1C1C28.toInt())
            setPadding(32, 24, 32, 24)
            textSize = 16f
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            selectAll()
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(input)
        }

        AlertDialog.Builder(this, R.style.SecretMenuTheme)
            .setTitle("Enter URL")
            .setView(container)
            .setPositiveButton("Load") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) webView.loadUrl(url)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAppInfo() {
        val currentUrl = webView.url ?: "—"
        val activePreset = urlPresets.find {
            currentUrl == it.url || currentUrl.startsWith(it.url.substringBefore("?"))
        }
        val sourceName = activePreset?.label?.drop(4) ?: "Custom"
        val presetList = urlPresets.joinToString("\n") { "  • ${it.label.drop(4)}: ${it.url}" }

        val info = """
            AWE Dashboard v${packageManager.getPackageInfo(packageName, 0).versionName}
            
            Active source: $sourceName
            Current URL: $currentUrl
            
            Available sources:
$presetList
            
            Secret menu: Press 1-2-3
        """.trimIndent()

        AlertDialog.Builder(this, R.style.SecretMenuTheme)
            .setTitle("ℹ App Info")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun applyFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        bufferResetHandler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }

    private fun getErrorHtml(message: String): String {
        return """
        <!DOCTYPE html>
        <html><head><style>
            body { background:#0a0a0f; color:#e8e8f0; font-family:sans-serif;
                   display:flex; flex-direction:column; align-items:center;
                   justify-content:center; height:100vh; margin:0; }
            h1 { color:#f87171; font-size:36px; }
            p { color:#6e6e82; font-size:20px; max-width:600px; text-align:center; }
            .hint { margin-top:30px; padding:16px 32px; border:2px solid #2a2a3a;
                    border-radius:12px; color:#4d8eff; font-size:18px; }
        </style></head><body>
            <h1>Connection Error</h1>
            <p>$message</p>
            <div class="hint">Press 1-2-3 for menu</div>
        </body></html>
        """.trimIndent()
    }
}
