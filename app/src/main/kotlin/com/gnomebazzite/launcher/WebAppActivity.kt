package com.gnomebazzite.launcher

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gnomebazzite.launcher.data.BuiltinApp
import java.io.File

class WebAppActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = intent.getParcelableExtra<BuiltinApp>("app")
        val path = intent.getStringExtra("path") ?: ""

        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#FF050608"))
        }

        // ── Mini titlebar ──
        val titlebar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#E614141F"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (38 * dp).toInt()
            )
            setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
        }
        val backBtn = TextView(this).apply {
            text = "✕"; textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#FFFF5F57"))
            setPadding(0, 0, (14 * dp).toInt(), 0)
            setOnClickListener { finish() }
        }
        val titleTv = TextView(this).apply {
            text = "${app?.iconEmoji ?: "📦"}  ${app?.name ?: "App"}"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#FFE8E8FF"))
        }
        titlebar.addView(backBtn); titlebar.addView(titleTv)
        root.addView(titlebar)

        // ── WebView ──
        val webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
            }
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
        }

        if (path.isNotEmpty() && File(path).exists()) {
            webView.loadUrl("file://$path")
        } else {
            webView.loadData(
                "<html><body style='background:#050608;color:#e8e8ff;font-family:monospace;" +
                "display:flex;justify-content:center;align-items:center;height:100vh;margin:0'>" +
                "<div style='text-align:center'>" +
                "<div style='font-size:48px'>${app?.iconEmoji ?: "📦"}</div>" +
                "<h2>${app?.name ?: "App"}</h2>" +
                "<p style='color:#88c8c0ff'>Fichiers de l'application non trouvés.<br>Essayez de réinstaller.</p>" +
                "</div></body></html>",
                "text/html", "utf-8"
            )
        }

        root.addView(webView)
        setContentView(root)
    }
}
