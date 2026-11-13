@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hindianimestuff.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun Ic(res: Int, tint: Color = C.Text, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(res), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

@Composable
fun Poster(item: Item, width: Int, modifier: Modifier = Modifier) {
    AsyncImage(
        model = posterUrl(item.poster, width),
        contentDescription = item.title,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(C.Surface3)
    )
}

@Composable
fun PosterCard(item: Item, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(onClick = onClick)) {
        Box {
            Poster(item, 342, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            if (item.ratingValue > 0) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Ic(R.drawable.ic_star, C.Gold, 11.dp)
                    Spacer(Modifier.width(3.dp))
                    Text(item.rating, color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (item.episodeTag.isNotBlank()) {
                Text(
                    item.episodeTag.replace("E ", "EP "),
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                        .clip(RoundedCornerShape(6.dp)).background(C.Accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title, color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp
        )
        if (item.season.isNotBlank()) {
            Text(item.season, color = C.Muted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) C.Accent else C.Surface,
        border = BorderStroke(1.dp, if (selected) C.Accent else C.Border)
    ) {
        Text(
            text, color = if (selected) Color.White else C.TextSecondary,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp)) {
        Text(text, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border))
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color) {
    Text(
        text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreTags(genres: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        genres.forEach { g ->
            Text(
                g, color = C.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(C.Surface2)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
fun BigButton(text: String, icon: Int, bg: Color, fg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = bg, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Ic(icon, fg, 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(text, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}

@Composable
fun TelegramCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(16.dp), color = C.Telegram,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(999.dp)).background(Color(0x2EFFFFFF)),
                contentAlignment = Alignment.Center
            ) { Ic(R.drawable.ic_send, Color.White, 22.dp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Join our Telegram", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(
                    "New episodes, daily updates & backup links.",
                    color = Color(0xEBFFFFFF), fontSize = 12.sp, lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Join Now", color = Color(0xFF1A82B6), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
    }
}
