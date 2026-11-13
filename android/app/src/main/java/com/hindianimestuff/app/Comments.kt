package com.hindianimestuff.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class Comment(
    val id: String, val parentId: String?, val name: String, val text: String,
    val pinned: Boolean, val likes: Int, val time: Long
)

/**
 * Talks to the SAME Firestore comments collection as the website, through the plain
 * REST API (no Firebase SDK / google-services.json needed). Comments are shared with the site.
 */
object CommentsApi {
    private const val BASE =
        "https://firestore.googleapis.com/v1/projects/${Config.FIREBASE_PROJECT}/databases/(default)/documents"

    private fun call(url: String, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw RuntimeException("HTTP $code: ${text.take(200)}")
        return text
    }

    private fun JSONObject.s(field: String): String? =
        optJSONObject("fields")?.optJSONObject(field)?.optString("stringValue")?.takeIf { it.isNotEmpty() }

    fun load(pageId: String): List<Comment> {
        val q = JSONObject().put(
            "structuredQuery", JSONObject()
                .put("from", JSONArray().put(JSONObject().put("collectionId", "comments")))
                .put(
                    "where", JSONObject().put(
                        "fieldFilter", JSONObject()
                            .put("field", JSONObject().put("fieldPath", "pageId"))
                            .put("op", "EQUAL")
                            .put("value", JSONObject().put("stringValue", pageId))
                    )
                )
                .put("limit", 200)
        )
        val res = JSONArray(call("$BASE:runQuery?key=${Config.FIREBASE_KEY}", q.toString()))
        val out = ArrayList<Comment>()
        for (i in 0 until res.length()) {
            val doc = res.getJSONObject(i).optJSONObject("document") ?: continue
            val f = doc.optJSONObject("fields") ?: continue
            val ts = f.optJSONObject("timestamp")?.optString("timestampValue").orEmpty()
            out.add(
                Comment(
                    id = doc.optString("name").substringAfterLast("/"),
                    parentId = doc.s("parentId"),
                    name = doc.s("name") ?: "Guest",
                    text = doc.s("text") ?: "",
                    pinned = f.optJSONObject("pinned")?.optBoolean("booleanValue", false) ?: false,
                    likes = f.optJSONObject("likes")?.optString("integerValue", "0")?.toIntOrNull() ?: 0,
                    time = runCatching { Instant.parse(ts).toEpochMilli() }.getOrDefault(0L)
                )
            )
        }
        return out
    }

    fun post(pageId: String, name: String, text: String) {
        val fields = JSONObject()
            .put("pageId", JSONObject().put("stringValue", pageId))
            .put("parentId", JSONObject().put("nullValue", JSONObject.NULL))
            .put("name", JSONObject().put("stringValue", name.take(40)))
            .put("text", JSONObject().put("stringValue", text.take(500)))
            .put("pinned", JSONObject().put("booleanValue", false))
            .put("likes", JSONObject().put("integerValue", "0"))
            .put("timestamp", JSONObject().put("timestampValue", Instant.now().toString()))
        call("$BASE/comments?key=${Config.FIREBASE_KEY}", JSONObject().put("fields", fields).toString())
    }
}

private fun ago(t: Long): String {
    if (t <= 0) return ""
    val s = (System.currentTimeMillis() - t) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60}m ago"
        s < 86400 -> "${s / 3600}h ago"
        s < 86400 * 30 -> "${s / 86400}d ago"
        else -> "${s / (86400 * 30)}mo ago"
    }
}

@Composable
fun CommentsSection(pageId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("app", Context.MODE_PRIVATE) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var state by remember { mutableStateOf("loading") } // loading | ok | error
    var reload by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf(prefs.getString("comment_name", "") ?: "") }
    var text by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }

    LaunchedEffect(pageId, reload) {
        state = "loading"
        val r = withContext(Dispatchers.IO) { runCatching { CommentsApi.load(pageId) } }
        r.onSuccess { comments = it; state = "ok" }.onFailure { state = "error" }
    }

    val top = comments.filter { it.parentId == null }
        .sortedWith(compareByDescending<Comment> { it.pinned }.thenByDescending { it.time })
    val replies = comments.filter { it.parentId != null }.groupBy { it.parentId!! }

    Column(
        Modifier.fillMaxWidth()
            .background(C.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, C.Border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text("💬 Comments", color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Spacer(Modifier.height(12.dp))

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = C.Text, unfocusedTextColor = C.Text,
            focusedBorderColor = C.Accent, unfocusedBorderColor = C.Border,
            cursorColor = C.Accent,
            focusedPlaceholderColor = C.Muted, unfocusedPlaceholderColor = C.Muted
        )
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(500) },
            placeholder = { Text("Your comment…") },
            modifier = Modifier.fillMaxWidth(), minLines = 3, colors = fieldColors,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(40) },
            placeholder = { Text("Name *") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(10.dp))
        BigButton(
            if (posting) "Posting…" else "Post comment", R.drawable.ic_send, C.Accent, C.Text,
            Modifier.fillMaxWidth()
        ) {
            val n = name.trim(); val t = text.trim()
            when {
                posting -> {}
                n.isEmpty() -> Toast.makeText(ctx, "Please enter your name", Toast.LENGTH_SHORT).show()
                t.isEmpty() -> Toast.makeText(ctx, "Please write a comment", Toast.LENGTH_SHORT).show()
                else -> {
                    posting = true
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { CommentsApi.post(pageId, n, t) } }
                        posting = false
                        r.onSuccess {
                            prefs.edit().putString("comment_name", n).apply()
                            text = ""
                            reload++
                        }.onFailure {
                            Toast.makeText(ctx, "Couldn't post comment", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        when (state) {
            "loading" -> Text("Loading comments…", color = C.Muted, fontSize = 13.sp)
            "error" -> Text("Couldn't load comments. Check your connection.", color = C.Muted, fontSize = 13.sp)
            else -> if (top.isEmpty()) {
                Text("No comments yet. Be the first!", color = C.Muted, fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    top.forEach { c ->
                        CommentRow(c, false)
                        replies[c.id].orEmpty().sortedBy { it.time }.forEach { r -> CommentRow(r, true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(c: Comment, isReply: Boolean) {
    Column(
        Modifier.fillMaxWidth()
            .padding(start = if (isReply) 20.dp else 0.dp)
            .background(if (c.pinned) C.AccentSoft else C.Surface2, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        if (c.pinned) Text("📌 Pinned", color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(c.name, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(ago(c.time), color = C.Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(c.text, color = C.TextSecondary, fontSize = 14.sp, lineHeight = 19.sp)
    }
}
