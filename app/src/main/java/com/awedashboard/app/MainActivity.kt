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
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private val dashboardUrl = "http://192.168.68.141/dashboard/awe-home.html"

    private var backPressedOnce = false
    private val backResetHandler = Handler(Looper.getMainLooper())
    private val backResetRunnable = Runnable { backPressedOnce = false }

    // Hidden menu: press 1, 2, 3 in sequence (within 2 s each) to open
    private val menuSequence = listOf(KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3)
    private val sequenceBuffer = mutableListOf<Int>()
    private val sequenceHandler = Handler(Looper.getMainLooper())
    private val sequenceResetRunnable = Runnable { sequenceBuffer.clear() }
    private val SEQUENCE_TIMEOUT_MS = 2000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFullscreen()
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        webView = findViewById(R.id.webView)

        setupWebView()
        setupBackHandler()
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
     * Modern back handler using OnBackPressedDispatcher.
     * This catches back presses from ALL sources — hardware buttons,
     * remote controls, gesture nav, system back, etc.
     */
    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }

    private fun handleBackNavigation() {
        // Ask the JS app to handle back first.
        // window.androidBack() returns true if it handled it (modal/screen close),
        // false if we're at the home screen and should exit.
        webView.evaluateJavascript("(function(){ return window.androidBack ? window.androidBack() : false; })()") { result ->
            val jsHandled = result?.trim() == "true"
            if (!jsHandled) {
                // JS says we're at the home screen — double-press to exit
                if (backPressedOnce) {
                    backResetHandler.removeCallbacks(backResetRunnable)
                    finishAndRemoveTask()
                    return@evaluateJavascript
                }
                backPressedOnce = true
                Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
                backResetHandler.removeCallbacks(backResetRunnable)
                backResetHandler.postDelayed(backResetRunnable, 2500)
            }
        }
    }

    /**
     * Also intercept KEYCODE_BACK via onKeyDown as a belt-and-suspenders
     * approach for remotes that bypass onBackPressedDispatcher.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                handleBackNavigation()
                return true
            }
            KeyEvent.KEYCODE_R -> {
                webView.reload()
                Toast.makeText(this, "Reloading…", Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Primary key interception point — runs before the WebView sees any event.
     *
     * Responsibilities:
     *  1. Consume KEYCODE_BACK ACTION_UP (already handled on ACTION_DOWN).
     *  2. Track the 1-2-3 hidden-menu sequence on ACTION_DOWN, consuming only
     *     the final key so that intermediate presses still reach the WebView.
     */
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.dispatchKeyEvent(event)

        // Belt-and-suspenders: eat the back UP so nothing downstream sees it twice.
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val key = event.keyCode
            when {
                key == menuSequence.getOrNull(sequenceBuffer.size) -> {
                    // Correct next key in the sequence
                    sequenceHandler.removeCallbacks(sequenceResetRunnable)
                    sequenceBuffer.add(key)
                    if (sequenceBuffer == menuSequence) {
                        // Full sequence matched — open menu and reset
                        sequenceBuffer.clear()
                        showHiddenMenu()
                        return true // consume the final key; don't pass to WebView
                    }
                    sequenceHandler.postDelayed(sequenceResetRunnable, SEQUENCE_TIMEOUT_MS)
                }
                key == menuSequence[0] -> {
                    // Wrong key but it restarts the sequence from step 1
                    sequenceHandler.removeCallbacks(sequenceResetRunnable)
                    sequenceBuffer.clear()
                    sequenceBuffer.add(key)
                    sequenceHandler.postDelayed(sequenceResetRunnable, SEQUENCE_TIMEOUT_MS)
                }
                key != KeyEvent.KEYCODE_BACK -> {
                    // Any unrelated key resets tracking
                    sequenceHandler.removeCallbacks(sequenceResetRunnable)
                    sequenceBuffer.clear()
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private val urlPresets = listOf(
        "Local  — 192.168.68.141" to "http://192.168.68.141/dashboard/awe-home.html",
        "Remote — GitHub Pages"   to "https://kavanagh-rob.github.io/Pages/awe-dashboard.html"
    )

    private fun showHiddenMenu() {
        val items = arrayOf(
            ">> Clear Cache & Reload <<",   // 0 — most-used, listed first
            "Reload Page",                  // 1
            "Load Preset URL",              // 2
            "Enter Custom URL",             // 3
            "Exit App"                      // 4
        )
        AlertDialog.Builder(this)
            .setTitle("Admin Menu")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        webView.clearCache(true)
                        webView.reload()
                        Toast.makeText(this, "Cache cleared, reloading...", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        webView.reload()
                        Toast.makeText(this, "Reloading...", Toast.LENGTH_SHORT).show()
                    }
                    2 -> showPresetMenu()
                    3 -> showUrlDialog()
                    4 -> finishAndRemoveTask()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPresetMenu() {
        val labels = urlPresets.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Load Preset")
            .setItems(labels) { _, which ->
                val url = urlPresets[which].second
                webView.clearCache(true)
                webView.loadUrl(url)
                Toast.makeText(this, "Loading: ${urlPresets[which].first.trim()}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun showUrlDialog() {
        val input = EditText(this).apply {
            setText(webView.url ?: dashboardUrl)
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Load URL")
            .setView(input)
            .setPositiveButton("Load") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) webView.loadUrl(url)
            }
            .setNegativeButton("Cancel", null)
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
        backResetHandler.removeCallbacksAndMessages(null)
        sequenceHandler.removeCallbacksAndMessages(null)
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
            <div class="hint">Press R to reload</div>
        </body></html>
        """.trimIndent()
    }
}