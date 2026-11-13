@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.hindianimestuff.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private const val TAB_HOME = 0
private const val TAB_TRENDING = 1
private const val TAB_SEARCH = 2
private const val TAB_FAV = 3
private const val TAB_MORE = 4

@Composable
fun MainScreen(
    vm: AppViewModel, onOpen: (String) -> Unit,
    onOpenPage: (String, String) -> Unit, onViewAll: (String) -> Unit, onWatch: (String) -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(TAB_HOME) }
    val items by vm.items.collectAsState()
    val loading by vm.loading.collectAsState()
    val favs by vm.favs.collectAsState()

    Scaffold(
        containerColor = C.Bg,
        bottomBar = {
            NavigationBar(containerColor = C.Surface) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = C.Accent, selectedTextColor = C.Accent,
                    unselectedIconColor = C.Muted, unselectedTextColor = C.Muted,
                    indicatorColor = C.AccentSoft
                )
                listOf(
                    Triple(TAB_HOME, R.drawable.ic_home, "Home"),
                    Triple(TAB_TRENDING, R.drawable.ic_trending, "Trending"),
                    Triple(TAB_SEARCH, R.drawable.ic_search, "Search"),
                    Triple(TAB_FAV, R.drawable.ic_favorite, "Favourite"),
                    Triple(TAB_MORE, R.drawable.ic_menu, "Menu")
                ).forEach { (t, icon, label) ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t }, colors = colors,
                        icon = { Ic(icon, if (tab == t) C.Accent else C.Muted, 22.dp) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(C.Bg)) {
            if (loading && items.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = C.Accent)
            } else when (tab) {
                TAB_HOME -> HomeTab(items, onOpen, onWatch, onViewAll) { tab = TAB_SEARCH }
                TAB_TRENDING -> TrendingTab(items, onOpen)
                TAB_SEARCH -> SearchTab(items, onOpen)
                TAB_FAV -> FavTab(items.filter { it.id in favs }, onOpen)
                else -> MoreTab(vm, onOpenPage)
            }
        }
    }
}

@Composable
private fun TopBar(title: String? = null, onSearch: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(10.dp))
        Text(title ?: "HindiAnimestuff", color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, modifier = Modifier.weight(1f))
        if (onSearch != null) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onSearch), contentAlignment = Alignment.Center) {
                Ic(R.drawable.ic_search, C.Text, 22.dp)
            }
        }
    }
}

private val GRID_PAD = PaddingValues(start = 14.dp, end = 14.dp, bottom = 16.dp)

/* ------------------------------- HOME ------------------------------- */
// Full-bleed OTT hero on top, then short groups of 10 per category (full lists behind "View All").
@Composable
private fun HomeTab(
    items: List<Item>, onOpen: (String) -> Unit, onWatch: (String) -> Unit,
    onViewAll: (String) -> Unit, onSearch: () -> Unit
) {
    val anime = remember(items) { items.filter { it.category == "anime" } }
    val hero = remember(items) {
        val flagged = anime.filter { it.trending }
        val base = if (flagged.isNotEmpty()) flagged else anime.sortedByDescending { it.ratingValue }
        base.distinctBy { it.title }.take(6)
    }
    val latestAnime = remember(anime) { anime.take(10) }
    val animeMovies = remember(anime) { anime.filter { it.type == "Movie" }.sortedByDescending { it.ratingValue }.take(10) }
    val movies = remember(items) { items.filter { it.category == "movies" }.take(10) }
    val series = remember(items) { items.filter { it.category == "series" }.take(10) }
    val ctx = LocalContext.current
    val side = Modifier.padding(horizontal = 14.dp)

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
        if (hero.isNotEmpty()) item { HeroBanner(hero, onOpen, onWatch, onSearch) }
        else item { TopBar(onSearch = onSearch) }
        if (latestAnime.isNotEmpty()) item { Box(side) { HomeSection("Latest Anime", latestAnime, onOpen) { onViewAll("anime") } } }
        if (animeMovies.isNotEmpty()) item { Box(side) { HomeSection("Anime Movies", animeMovies, onOpen) { onViewAll("animemovies") } } }
        if (movies.isNotEmpty()) item { Box(side) { HomeSection("Movies", movies, onOpen) { onViewAll("movies") } } }
        if (series.isNotEmpty()) item { Box(side) { HomeSection("Series", series, onOpen) { onViewAll("series") } } }
        item { Box(side) { TelegramCard { openUrl(ctx, Config.TELEGRAM) } } }
    }
}

@Composable
private fun HomeSection(title: String, list: List<Item>, onOpen: (String) -> Unit, onViewAll: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text(
                "View All ›", color = C.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onViewAll).padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { it2 -> PosterCard(it2, { onOpen(it2.id) }, Modifier.width(118.dp)) }
        }
    }
}

/** Big edge-to-edge OTT hero: poster fills the width, fades into the page background,
 *  logo + search float on top, title / meta / buttons sit at the bottom, auto-slides every 5s. */
@Composable
private fun HeroBanner(list: List<Item>, onOpen: (String) -> Unit, onWatch: (String) -> Unit, onSearch: () -> Unit) {
    val pager = rememberPagerState(pageCount = { list.size })
    LaunchedEffect(list.size) {
        while (list.size > 1) {
            delay(5000)
            pager.animateScrollToPage((pager.currentPage + 1) % list.size)
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(0.78f)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val h = list[page]
            Box(Modifier.fillMaxSize().background(C.Surface3).clickable { onOpen(h.id) }) {
                AsyncImage(
                    model = posterUrl(h.poster, 780), contentDescription = h.title,
                    contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )
                // dark top (for the logo/search), clear middle (poster shows), fade into the page at the bottom
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color(0xB3000000),
                                0.22f to Color(0x00000000),
                                0.50f to Color(0x00000000),
                                0.80f to Color(0xE6000000),
                                1f to C.Bg
                            )
                        )
                    )
                )
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        h.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 34.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    // ★ 9.4  •  2025  •  Action · Fantasy · Drama
                    val meta = buildList {
                        if (h.year.isNotBlank()) add(h.year)
                        if (h.genres.isNotEmpty()) add(h.genres.take(3).joinToString(" · "))
                    }.joinToString("  •  ")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (h.ratingValue > 0) {
                            Ic(R.drawable.ic_star, C.Gold, 15.dp)
                            Spacer(Modifier.width(4.dp))
                            Text(h.rating, color = C.Gold, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            if (meta.isNotEmpty()) Text("  •  ", color = Color(0xCCFFFFFF), fontSize = 13.sp)
                        }
                        Text(meta, color = Color(0xE6FFFFFF), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (h.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            h.description, color = Color(0xCCFFFFFF), fontSize = 12.sp, lineHeight = 17.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White)
                                .clickable { if (h.playableEps.isNotEmpty()) onWatch(h.id) else onOpen(h.id) }
                                .padding(horizontal = 26.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Ic(R.drawable.ic_play, Color.Black, 20.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Watch Now", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x55FFFFFF))
                                .clickable { onOpen(h.id) }
                                .padding(horizontal = 22.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
        // floating top bar over the poster
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopStart).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(10.dp))
            Text("HindiAnimestuff", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(999.dp)).background(Color(0x40000000)).clickable(onClick = onSearch),
                contentAlignment = Alignment.Center
            ) { Ic(R.drawable.ic_search, Color.White, 22.dp) }
        }
        // slide indicators
        if (list.size > 1) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.Center) {
                list.indices.forEach { i ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).height(4.dp)
                            .width(if (i == pager.currentPage) 22.dp else 8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (i == pager.currentPage) C.Accent else Color(0x66FFFFFF))
                    )
                }
            }
        }
    }
}

/* --------------------------- VIEW ALL (category) --------------------------- */
@Composable
fun CategoryScreen(vm: AppViewModel, cat: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val items by vm.items.collectAsState()
    var genre by rememberSaveable { mutableStateOf("All") }
    val title = when (cat) {
        "anime" -> "Anime"
        "animemovies" -> "Anime Movies"
        "movies" -> "Movies"
        "series" -> "Series"
        else -> "All"
    }
    val inCat = remember(items, cat) {
        when (cat) {
            "animemovies" -> items.filter { it.category == "anime" && it.type == "Movie" }.sortedByDescending { it.ratingValue }
            else -> items.filter { it.category == cat }
        }
    }
    val genres = remember(inCat) {
        inCat.flatMap { it.genres }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(18).map { it.key }
    }
    val list = remember(inCat, genre) { if (genre == "All") inCat else inCat.filter { genre in it.genres } }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopRow(title, onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = GRID_PAD,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All") + genres) { g -> Chip(g, genre == g) { genre = g } }
                }
            }
            items(list, key = { it.id }) { it2 -> PosterCard(it2, { onOpen(it2.id) }) }
        }
    }
}

/* ----------------------------- TRENDING ----------------------------- */
@Composable
private fun TrendingTab(items: List<Item>, onOpen: (String) -> Unit) {
    val list = remember(items) {
        val flagged = items.filter { it.trending }
        (if (flagged.size >= 10) flagged else items.sortedByDescending { it.ratingValue }).take(60)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3), contentPadding = GRID_PAD,
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column { TopBar("Trending"); Spacer(Modifier.height(4.dp)) }
        }
        items(list, key = { it.id }) { it2 -> PosterCard(it2, { onOpen(it2.id) }) }
    }
}

/* ------------------------------ SEARCH ------------------------------ */
@Composable
private fun SearchTab(items: List<Item>, onOpen: (String) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val results = remember(items, q) {
        val t = q.trim().lowercase()
        if (t.length < 2) emptyList()
        else items.filter { it ->
            it.title.lowercase().contains(t) ||
                it.genres.any { g -> g.lowercase().contains(t) } ||
                it.languages.any { l -> l.lowercase().contains(t) }
        }.take(120)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3), contentPadding = GRID_PAD,
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                TopBar("Search")
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    placeholder = { Text("Search anime, movies, series…") },
                    leadingIcon = { Ic(R.drawable.ic_search, C.Muted, 20.dp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = C.Text, unfocusedTextColor = C.Text,
                        focusedBorderColor = C.Accent, unfocusedBorderColor = C.Border,
                        cursorColor = C.Accent, focusedContainerColor = C.Surface, unfocusedContainerColor = C.Surface,
                        focusedPlaceholderColor = C.Muted, unfocusedPlaceholderColor = C.Muted
                    )
                )
                Spacer(Modifier.height(10.dp))
                if (q.trim().length >= 2 && results.isEmpty()) {
                    Text("No results for “${q.trim()}”", color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp))
                } else if (q.trim().length < 2) {
                    Text("Type at least 2 letters. You can search by title, genre or language.", color = C.Muted, fontSize = 13.sp)
                }
            }
        }
        items(results, key = { it.id }) { it2 -> PosterCard(it2, { onOpen(it2.id) }) }
    }
}

/* --------------------------- FAVOURITES ---------------------------- */
@Composable
private fun FavTab(list: List<Item>, onOpen: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3), contentPadding = GRID_PAD,
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                TopBar("Favourites")
                if (list.isEmpty()) {
                    Text(
                        "Nothing here yet. Tap the heart on any title to save it.",
                        color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }
        }
        items(list, key = { it.id }) { it2 -> PosterCard(it2, { onOpen(it2.id) }) }
    }
}

/* ------------------------------- MORE ------------------------------- */
@Composable
private fun MoreTab(vm: AppViewModel, onOpenPage: (String, String) -> Unit) {
    val ctx = LocalContext.current
    val status by vm.status.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TopBar("Menu")
        TelegramCard { openUrl(ctx, Config.TELEGRAM) }
        Spacer(Modifier.height(6.dp))
        MenuRow("How to Download") { onOpenPage("${Config.SITE}/how-to-download.html", "How to Download") }
        MenuRow("Report a Problem") { onOpenPage("${Config.SITE}/report.html", "Report a Problem") }
        MenuRow("DMCA") { onOpenPage("${Config.SITE}/dmca.html", "DMCA") }
        MenuRow("Open Website") { onOpenPage(Config.SITE, "HindiAnimestuff") }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Surface)
                .clickable { vm.load() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Refresh list", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(status.ifBlank { "Tap to check for new titles" }, color = C.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(C.Surface)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("›", color = C.Muted, fontSize = 20.sp)
    }
}
