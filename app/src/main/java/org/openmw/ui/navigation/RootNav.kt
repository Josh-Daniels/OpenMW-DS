package org.openmw.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.openmw.modDownloader.StatusInfo.processing
import org.openmw.ui.MainScreen
import org.openmw.ui.controls.UIStateManager.configureControls
import org.openmw.ui.view.BackgroundAnimation

enum class RootDesc(
    val route: String
) {
    Main("main_screen"),
}

val LocalRootNav = staticCompositionLocalOf<NavHostController> {
    error("LocalRootNav Not Provide")
}

@Composable
fun RootNav() {
    val navController = rememberNavController()
    CompositionLocalProvider(
        LocalRootNav provides navController,
    ) {
        RootNavHost(navController)
    }
}

@Composable
fun RootNavHost(
    rootNavController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = rootNavController,
        startDestination = RootDesc.Main.route,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }) + fadeIn()
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it }) + fadeIn()
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        }
    ) {
        composable(route = RootDesc.Main.route) {

            AnimatedVisibility(
                visible = !processing,
                enter = fadeIn() + expandIn(),
                exit = fadeOut() + shrinkOut()
            ) {
                BackgroundAnimation()
            }
            MainScreen()
        }
    }
}
