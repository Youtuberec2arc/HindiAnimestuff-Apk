package com.hindianimestuff.app

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

object Config {
    const val SITE = "https://hindianimestuff.web.app"
    const val DATA_URL = "$SITE/app-data.json"
    const val TELEGRAM = "https://t.me/+ZY6u10TE0PdjZmM1"
    const val FIREBASE_PROJECT = "courseskida"
    const val FIREBASE_KEY = "AIzaSyDYkDycquqaHAneuxKRa7R2O31gmrm2NPc"
}

val QUALITIES = listOf("480p", "720p", "1080p")
private val QUALITY_PREF = listOf("720p", "480p", "1080p")

// Only these hosts hand out the real video file. Everything else (filesdl, fastxyz, hubcloud,
// mediafire, gplinks, t.me ...) is a web PAGE - it must be opened in the in-app browser,
// never fed to DownloadManager (that is what used to save an .html file) or to the video player.
private val DIRECT_FILE_HOSTS = listOf("workers.dev")

fun isStreamable(url: String): Boolean {
    if (!url.startsWith("http")) return false
    val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return false
    return DIRECT_FILE_HOSTS.any { host.endsWith(it) }
}

fun isTelegramLink(url: String): Boolean {
    val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return false
    return host == "t.me" || host.endsWith(".t.me") || host == "telegram.me"
}

data class Ep(val key: String, val label: String, val links: Map<String, String>)

data class Item(
    val id: String,
    val title: String,
    val category: String,
    val season: String,
    val poster: String,
    val rating: String,
    val genres: List<String>,
    val qualityTag: String,
    val episodeTag: String,
    val languages: List<String>,
    val runtime: String,
    val subtitle: String,
    val episodes: String,
    val type: String,
    val description: String,
    val year: String,
    val trending: Boolean,
    val eps: List<Ep>
) {
    val ratingValue: Double get() = rating.toDoubleOrNull() ?: 0.0
    val displayTitle: String get() = if (season.isNotBlank()) "$title · $season" else title

    /** Episodes with at least one quality a video player can attempt (direct file links). */
    val playableEps: List<Ep> by lazy {
        eps.mapNotNull { e ->
            val ok = e.links.filter { isStreamable(it.value) }
            if (ok.isEmpty()) null else Ep(e.key, e.label, ok)
        }
    }
}

fun posterUrl(url: String, width: Int): String =
    url.replace("/t/p/original/", "/t/p/w$width/")

fun sortedQualities(links: Map<String, String>): List<String> =
    links.keys.sortedBy { q -> QUALITIES.indexOf(q).let { if (it < 0) 99 else it } }

fun pickQuality(ep: Ep, preferred: String?, failed: Set<String>): String {
    val avail = ep.links.keys.filter { it !in failed }
    if (avail.isEmpty()) return ""
    if (preferred != null && preferred in avail) return preferred
    for (q in QUALITY_PREF) if (q in avail) return q
    return avail.first()
}

private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")
private fun JSONObject.strList(k: String): List<String> {
    val a = optJSONArray(k) ?: return emptyList()
    return (0 until a.length()).map { a.optString(it, "") }.filter { it.isNotBlank() }
}

private fun decode(b64: String): String =
    runCatching { String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault("")

fun parseItems(text: String): List<Item> {
    val arr: JSONArray = JSONObject(text).getJSONArray("items")
    val out = ArrayList<Item>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val epsArr = o.optJSONArray("eps") ?: JSONArray()
        val eps = ArrayList<Ep>()
        for (j in 0 until epsArr.length()) {
            val e = epsArr.getJSONObject(j)
            val qObj = e.optJSONObject("q") ?: continue
            val links = LinkedHashMap<String, String>()
            for (k in qObj.keys()) {
                val u = decode(qObj.optString(k, ""))
                if (u.isNotBlank()) links[k] = u
            }
            if (links.isNotEmpty()) eps.add(Ep(e.str("k"), e.str("l"), links))
        }
        out.add(
            Item(
                id = o.str("id"), title = o.str("title"), category = o.str("category"),
                season = o.str("season"), poster = o.str("poster"), rating = o.str("rating"),
                genres = o.strList("genres"), qualityTag = o.str("quality_tag"),
                episodeTag = o.str("episode_tag"), languages = o.strList("languages"),
                runtime = o.str("runtime"), subtitle = o.str("subtitle"),
                episodes = o.str("episodes"), type = o.str("type"),
                description = o.str("description"), year = o.str("year"),
                trending = o.optBoolean("trending", false), eps = eps
            )
        )
    }
    return out
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("app", Context.MODE_PRIVATE)
    private val cacheFile = File(app.filesDir, "app-data.json")

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _favs = MutableStateFlow(prefs.getStringSet("favs", emptySet()) ?: emptySet())
    val favs: StateFlow<Set<String>> = _favs

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = _items.value.isEmpty()
            // 1) instant: cached copy, else the catalog bundled inside the APK
            if (_items.value.isEmpty()) {
                val local = withContext(Dispatchers.IO) {
                    runCatching {
                        val text = if (cacheFile.exists()) cacheFile.readText()
                        else getApplication<Application>().assets.open("app-data.json").bufferedReader().use { it.readText() }
                        parseItems(text)
                    }.getOrNull()
                }
                if (local != null) _items.value = local
                _loading.value = false
            }
            // 2) background: fresh catalog from the website
            _status.value = "Updating…"
            val fresh = withContext(Dispatchers.IO) {
                runCatching {
                    val c = URL(Config.DATA_URL).openConnection() as HttpURLConnection
                    c.connectTimeout = 15000
                    c.readTimeout = 30000
                    c.setRequestProperty("Accept-Encoding", "gzip")
                    val stream = if (c.contentEncoding == "gzip") java.util.zip.GZIPInputStream(c.inputStream) else c.inputStream
                    val text = stream.bufferedReader().use { it.readText() }
                    val parsed = parseItems(text)
                    if (parsed.isNotEmpty()) cacheFile.writeText(text)
                    parsed
                }.getOrNull()
            }
            if (!fresh.isNullOrEmpty()) {
                _items.value = fresh
                _status.value = "Up to date · ${fresh.size} titles"
            } else {
                _status.value = if (_items.value.isEmpty()) "Couldn't load data" else "Offline · showing saved list"
            }
            _loading.value = false
        }
    }

    fun find(id: String): Item? = _items.value.firstOrNull { it.id == id }

    fun related(item: Item, limit: Int = 12): List<Item> {
        val g = item.genres.toSet()
        return _items.value.asSequence()
            .filter { it.id != item.id && it.title != item.title }
            .map { it to it.genres.count { x -> x in g } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second * 100 + it.first.ratingValue }
            .distinctBy { it.first.title }
            .take(limit).map { it.first }.toList()
    }

    fun toggleFav(id: String) {
        val cur = _favs.value.toMutableSet()
        if (!cur.add(id)) cur.remove(id)
        _favs.value = cur
        prefs.edit().putStringSet("favs", cur).apply()
    }
}

/* ---------- small helpers shared by screens ---------- */

fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(ctx, "Can't open link", Toast.LENGTH_SHORT).show() }
}

fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun nameFromUrl(url: String): String? =
    runCatching { Uri.parse(url).getQueryParameter("name") }.getOrNull()
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        ?.takeIf { it.isNotBlank() }

fun fileNameFor(item: Item, ep: Ep, quality: String, url: String): String {
    val raw = nameFromUrl(url)
        ?: "${item.displayTitle} ${if (ep.key == "main") "" else ep.label} $quality.mkv"
    return raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(150)
}

/** Direct file links go to Android's DownloadManager (notification + Downloads folder).
 *  Telegram bot links open in Telegram. Any other link is a web page: it opens INSIDE the app
 *  (onOpenPage) and the download only starts once that page serves the real file. */
fun startDownload(ctx: Context, item: Item, ep: Ep, quality: String, onOpenPage: (String) -> Unit) {
    val url = ep.links[quality] ?: return
    if (isTelegramLink(url)) { openUrl(ctx, url); return }
    if (!isStreamable(url)) { onOpenPage(url); return }
    try {
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileNameFor(item, ep, quality, url))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "HindiAnimestuff/" + fileNameFor(item, ep, quality, url))
            .setAllowedOverMetered(true)
        (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        Toast.makeText(ctx, "Download started — see notifications", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "Download failed to start, opening link", Toast.LENGTH_SHORT).show()
        onOpenPage(url)
    }
}
