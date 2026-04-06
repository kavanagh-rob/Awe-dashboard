package com.awedashboard.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private val dashboardUrl = "https://kavanagh-rob.github.io/Pages/awe-dashboard.html"

    // Double-back-to-exit state
    private var backPressedOnce = false
    private val backResetHandler = Handler(Looper.getMainLooper())

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
                // Auto-focus first focusable element for D-pad navigation
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

            // Keep all navigation inside the WebView
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                return handleBack()
            }
            // R to reload
            KeyEvent.KEYCODE_R -> {
                webView.reload()
                Toast.makeText(this, "Reloading…", Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleBack(): Boolean {
        // 1. If WebView can go back in its history, do that
        if (webView.canGoBack()) {
            webView.goBack()
            backPressedOnce = false
            return true
        }

        // 2. Try JavaScript back navigation (for single-page apps with internal routing)
        webView.evaluateJavascript("""
            (function() {
                // Check if the page has a custom back handler
                if (typeof window.onDashboardBack === 'function') {
                    return window.onDashboardBack();
                }
                // Check if there are focused elements to unfocus
                if (document.activeElement && document.activeElement !== document.body) {
                    document.activeElement.blur();
                    return true;
                }
                return false;
            })();
        """.trimIndent()) { result ->
            if (result == "true") {
                backPressedOnce = false
            }
        }

        // 3. Double-back to exit
        if (backPressedOnce) {
            finish()
            return true
        }

        backPressedOnce = true
        Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()

        // Reset after 2 seconds
        backResetHandler.postDelayed({ backPressedOnce = false }, 2000)
        return true
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
