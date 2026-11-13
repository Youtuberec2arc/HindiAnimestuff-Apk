@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hindianimestuff.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NotFound(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(C.Bg).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Title not found", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        BigButton("Go back", R.drawable.ic_arrow_back, C.Accent, Color.White, onClick = onBack)
    }
}

@Composable
fun TopRow(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Ic(R.drawable.ic_arrow_back, C.Text, 24.dp)
        }
        Text(title, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
        trailing()
    }
}

/** Info block used on both the detail screen and under the player. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoBlock(item: Item) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (item.ratingValue > 0) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x26E8B84A)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ic(R.drawable.ic_star, C.Gold, 14.dp)
                Spacer(Modifier.width(4.dp))
                Text("${item.rating}/10", color = C.Gold, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
        if (item.runtime.isNotBlank()) Pill(item.runtime, C.Surface2, C.TextSecondary)
    }
    Spacer(Modifier.height(10.dp))
    GenreTags(item.genres)
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (item.qualityTag.isNotBlank()) Pill(item.qualityTag, Color(0x26E85D4C), C.Accent)
        if (item.episodeTag.isNotBlank()) Pill(item.episodeTag.replace("E ", "EP "), Color(0x2622C55E), C.Success)
        if (item.languages.isNotEmpty()) Pill(item.languages.joinToString(" · "), Color(0x263B82F6), Color(0xFF6CA6FF))
    }
    if (item.description.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(item.description, color = C.TextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
    }
    Spacer(Modifier.height(14.dp))
    Column(
        Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, C.Border, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        fun spec(label: String, value: String) = if (value.isBlank()) null else label to value
        listOfNotNull(
            spec("Runtime", item.runtime), spec("Language", item.languages.joinToString(", ")),
            spec("Subtitle", item.subtitle), spec("Quality", item.qualityTag),
            spec("Episodes", item.episodes), spec("Type", item.type), spec("Year", item.year)
        ).forEach { (l, v) ->
            Row {
                Text(l.uppercase(), color = C.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(92.dp))
                Text(v, color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun RelatedRow(list: List<Item>, onOpen: (String) -> Unit) {
    if (list.isEmpty()) return
    SectionTitle("♥ You May Also Like")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list, key = { it.id }) { r -> PosterCard(r, { onOpen(r.id) }, Modifier.width(112.dp)) }
    }
}

@Composable
fun DetailScreen(
    vm: AppViewModel, id: String, onBack: () -> Unit, onOpen: (String) -> Unit,
    onWatch: (String) -> Unit, onDownload: () -> Unit
) {
    val items by vm.items.collectAsState()
    val favs by vm.favs.collectAsState()
    val item = remember(items, id) { items.firstOrNull { it.id == id } }
    val ctx = LocalContext.current
    if (item == null) { NotFound(onBack); return }
    val related = remember(item, items) { vm.related(item) }
    val isFav = id in favs

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopRow(item.title, onBack) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).clickable { vm.toggleFav(id) }, contentAlignment = Alignment.Center) {
                Ic(if (isFav) R.drawable.ic_favorite else R.drawable.ic_favorite_border, if (isFav) C.Accent else C.Text, 24.dp)
            }
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).clickable {
                shareText(ctx, "${item.displayTitle} — watch & download on HindiAnimestuff\n${Config.SITE}/pages/${item.id}.html")
            }, contentAlignment = Alignment.Center) { Ic(R.drawable.ic_share, C.Text, 22.dp) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Poster(item, 500, Modifier.width(128.dp).aspectRatio(2f / 3f))
                    Column(Modifier.weight(1f)) {
                        if (item.season.isNotBlank()) Text(item.season.uppercase(), color = C.Accent, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        Text(item.title, color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 27.sp)
                        Spacer(Modifier.height(10.dp))
                        if (item.eps.isNotEmpty()) {
                            if (item.playableEps.isNotEmpty()) {
                                BigButton("Watch Now", R.drawable.ic_play, C.Gold, Color(0xFF1A1305), Modifier.fillMaxWidth()) { onWatch("") }
                                Spacer(Modifier.height(8.dp))
                            }
                            BigButton("Download", R.drawable.ic_download, C.Accent, Color.White, Modifier.fillMaxWidth(), onClick = onDownload)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                InfoBlock(item)
            }
            item {
                if (item.playableEps.size > 1) {
                    SectionTitle(if (item.playableEps.size > 1) "Episodes" else "")
                    EpisodeChips(item) { ep -> onWatch(ep.key) }
                }
            }
            item { RelatedRow(related, onOpen) }
            item { TelegramCard { openUrl(ctx, Config.TELEGRAM) } }
            item { Spacer(Modifier.height(6.dp)); CommentsSection(item.id); Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeChips(item: Item, current: String? = null, onPick: (Ep) -> Unit) {
    val eps = item.playableEps
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        eps.forEach { ep ->
            val label = if (item.eps.size > 1 && item.episodeTag.isNotBlank() && ep.label.startsWith("Episode")) ep.key else ep.label
            val on = ep.key == current
            Text(
                label, color = if (on) Color.White else C.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (on) C.Accent else C.Surface)
                    .border(1.dp, if (on) C.Accent else C.Border, RoundedCornerShape(10.dp))
                    .clickable { onPick(ep) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

/* ---------------------------- Download page ---------------------------- */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadScreen(vm: AppViewModel, id: String, onBack: () -> Unit, onWatch: (String) -> Unit, onOpenPage: (String) -> Unit) {
    val items by vm.items.collectAsState()
    val item = remember(items, id) { items.firstOrNull { it.id == id } }
    val ctx = LocalContext.current
    if (item == null) { NotFound(onBack); return }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopRow("Download", onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item {
                Text(item.title, color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                if (item.season.isNotBlank()) Text(item.season, color = C.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("Tap a quality to start. Direct files download straight to Downloads/HindiAnimestuff; other links open inside the app until the download starts.", color = C.Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
            }
            items(item.eps, key = { it.key }) { ep ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp)
                        .background(C.Surface, RoundedCornerShape(14.dp))
                        .border(1.dp, C.Border, RoundedCornerShape(14.dp)).padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (ep.key == "main") item.title else ep.label, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (ep.links.values.any { isStreamable(it) }) {
                            Row(
                                Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x26E8B84A))
                                    .clickable { onWatch(ep.key) }.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Ic(R.drawable.ic_play, C.Gold, 14.dp)
                                Spacer(Modifier.width(4.dp))
                                Text("Watch", color = C.Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sortedQualities(ep.links).forEach { q ->
                            Row(
                                Modifier.clip(RoundedCornerShape(10.dp)).background(C.Accent)
                                    .clickable { startDownload(ctx, item, ep, q, onOpenPage) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Ic(R.drawable.ic_download, Color.White, 16.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(q, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            item { TelegramCard { openUrl(ctx, Config.TELEGRAM) } }
        }
    }
}
