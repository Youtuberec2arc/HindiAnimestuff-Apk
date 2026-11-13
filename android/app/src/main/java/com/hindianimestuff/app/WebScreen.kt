package com.hindianimestuff.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val FILE_PATH = Regex(".*\\.(mkv|mp4|m4v|avi|webm|mov|zip|rar|7z|apk)$", RegexOption.IGNORE_CASE)

/** Hands a real file to Android's DownloadManager (notification + Downloads/HindiAnimestuff). */
fun downloadFromWeb(ctx: Context, url: String, userAgent: String?, referer: String?, disposition: String?, mime: String?) {
    try {
        val name = URLUtil.guessFileName(url, disposition, mime)
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "HindiAnimestuff/$name")
            .setAllowedOverMetered(true)
        if (!mime.isNullOrBlank()) req.setMimeType(mime)
        val cookie = CookieManager.getInstance().getCookie(url)
        if (!cookie.isNullOrBlank()) req.addRequestHeader("Cookie", cookie)
        if (!userAgent.isNullOrBlank()) req.addRequestHeader("User-Agent", userAgent)
        if (!referer.isNullOrBlank()) req.addRequestHeader("Referer", referer)
        (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        Toast.makeText(ctx, "Download started — see notifications", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "Couldn't start the download", Toast.LENGTH_SHORT).show()
    }
}

/**
 * In-app browser. Used for download pages (filesdl / fastxyz / hubcloud ...) and for the
 * How-to-Download / Report / DMCA pages. A page is only turned into a download when the server
 * answers with a real file (DownloadListener) or the link itself ends in a file extension.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(url: String, title: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var progress by remember { mutableStateOf(0) }
    var pageTitle by remember { mutableStateOf(title) }
    var canGoBack by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = canGoBack) { web?.goBack() }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopRow(pageTitle.ifBlank { "HindiAnimestuff" }, onBack)
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = C.Accent, trackColor = C.Surface2
            )
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.height(2.dp))
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                val wv = WebView(c)
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.settings.useWideViewPort = true
                wv.settings.loadWithOverviewMode = true
                wv.settings.javaScriptCanOpenWindowsAutomatically = false
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

                wv.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val u = request.url
                        val s = u.toString()
                        val scheme = u.scheme ?: ""
                        if (scheme != "http" && scheme != "https") {
                            openUrl(c, s)            // tg://, intent:, market: ... -> proper app
                            return true
                        }
                        if (isTelegramLink(s)) { openUrl(c, s); return true }
                        if (FILE_PATH.matches(u.path ?: "")) {
                            downloadFromWeb(c, s, view.settings.userAgentString, view.url, null, null)
                            return true
                        }
                        return false                 // everything else stays inside the app
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        canGoBack = view.canGoBack()
                    }
                }
                wv.webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                    override fun onReceivedTitle(view: WebView, t: String?) {
                        if (!t.isNullOrBlank() && !t.startsWith("http")) pageTitle = t
                    }
                }
                wv.setDownloadListener(DownloadListener { dlUrl, userAgent, disposition, mime, _ ->
                    downloadFromWeb(c, dlUrl, userAgent, wv.url, disposition, mime)
                })
                wv.loadUrl(url)
                web = wv
                wv
            },
            onRelease = { it.stopLoading(); it.destroy() }
        )
    }
}
