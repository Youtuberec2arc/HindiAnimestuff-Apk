package com.hindianimestuff.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val vm: AppViewModel = viewModel()
                AppNav(vm)
            }
        }
    }
}

@Composable
private fun AppNav(vm: AppViewModel) {
    val nav = rememberNavController()
    // every link that is a web page (download pages, How to Download, Report, DMCA ...) opens inside the app
    val openWeb: (String, String) -> Unit = { u, t -> nav.navigate("web/${Uri.encode(u)}?title=${Uri.encode(t)}") }
    NavHost(navController = nav, startDestination = "main") {
        composable("main") {
            MainScreen(
                vm = vm,
                onOpen = { id -> nav.navigate("detail/${Uri.encode(id)}") },
                onOpenPage = openWeb,
                onViewAll = { cat -> nav.navigate("list/${Uri.encode(cat)}") },
                onWatch = { id -> nav.navigate("player/${Uri.encode(id)}?ep=") }
            )
        }
        composable("list/{cat}") { entry ->
            CategoryScreen(
                vm = vm, cat = entry.arguments?.getString("cat").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate("detail/${Uri.encode(id)}") }
            )
        }
        composable(
            route = "web/{url}?title={title}",
            arguments = listOf(navArgument("title") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            WebScreen(
                url = entry.arguments?.getString("url").orEmpty(),
                title = entry.arguments?.getString("title").orEmpty(),
                onBack = { nav.popBackStack() }
            )
        }
        composable("detail/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DetailScreen(
                vm = vm, id = id,
                onBack = { nav.popBackStack() },
                onOpen = { other -> nav.navigate("detail/${Uri.encode(other)}") },
                onWatch = { ep -> nav.navigate("player/${Uri.encode(id)}?ep=${Uri.encode(ep)}") },
                onDownload = { nav.navigate("download/${Uri.encode(id)}") }
            )
        }
        composable("download/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DownloadScreen(
                vm = vm, id = id,
                onBack = { nav.popBackStack() },
                onWatch = { ep -> nav.navigate("player/${Uri.encode(id)}?ep=${Uri.encode(ep)}") },
                onOpenPage = { u -> openWeb(u, "Download") }
            )
        }
        composable(
            route = "player/{id}?ep={ep}",
            arguments = listOf(navArgument("ep") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val ep = entry.arguments?.getString("ep").orEmpty()
            PlayerScreen(
                vm = vm, id = id, startEp = ep,
                onBack = { nav.popBackStack() },
                onOpen = { other -> nav.navigate("detail/${Uri.encode(other)}") },
                onDownloadAll = { nav.navigate("download/${Uri.encode(id)}") },
                onOpenPage = { u -> openWeb(u, "Download") }
            )
        }
    }
}
