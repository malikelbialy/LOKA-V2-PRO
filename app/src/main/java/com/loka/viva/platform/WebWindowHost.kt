package com.loka.viva.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import android.webkit.WebViewClient

class WebWindowHost(
    private val context: Context,
    private val onUrlSelected: (String) -> Unit
) {
    private val webViews = mutableMapOf<String, WebView>()

    fun open(url: String, windowId: String) {
        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setWebViewClient(object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView?, u: String?): Boolean {
                    u?.let { onUrlSelected(it) }
                    return false
                }
            })
            loadUrl(url)
        }
        webViews[windowId] = wv
    }

    fun snapshot(windowId: String): Bitmap? {
        val wv = webViews[windowId] ?: return null
        val bmp = Bitmap.createBitmap(
            if (wv.width > 0) wv.width else 1280,
            if (wv.height > 0) wv.height else 720,
            Bitmap.Config.ARGB_8888
        )
        wv.draw(Canvas(bmp))
        return bmp
    }

    fun close(windowId: String) { webViews.remove(windowId)?.destroy() }
}
