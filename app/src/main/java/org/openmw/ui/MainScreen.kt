package org.openmw.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.InternalCoroutinesApi
import org.openmw.R
import org.openmw.ui.controls.UIStateManager.transparentBlack
import org.openmw.ui.theme.AccentLightBlue
import org.openmw.ui.navigation.MainPageDestination
import org.openmw.ui.navigation.MainPageNav
import org.openmw.ui.view.MyFloatingActionButton
import org.openmw.ui.view.NavigationSuiteScaffold
import org.openmw.utils.getLayoutType
import org.openmw.utils.navigateSingleTopTo
import org.openmw.utils.stringRes

@OptIn(ExperimentalMaterial3Api::class, InternalCoroutinesApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStack?.destination
    val currentScreen = MainPageDestination.entries.find { it.route == currentDestination?.route } ?: MainPageDestination.Home
    val layoutType = getLayoutType()

    NavigationSuiteScaffold(
        layoutType = layoutType,
        containerColor = Color.Transparent,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = transparentBlack,
            navigationRailContainerColor = transparentBlack,
            navigationDrawerContainerColor = transparentBlack
        ),
        navigationSuiteItems = {
            MainPageDestination.entries.forEachIndexed { index, it ->
                item(
                    selected = it == currentScreen,
                    icon = {
                        if (it != MainPageDestination.StartGame) {
                            Icon(
                                imageVector = it.getIcon(it == currentScreen),
                                contentDescription = null
                            )
                        } else {
                            Box {
                                MyFloatingActionButton() // Start Game Button
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Start Game",
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(28.dp)
                                        .offset(x = 8.dp, y = 8.dp),
                                    tint = AccentLightBlue
                                )
                            }
                        }
                    },
                    onClick = {
                        if (it != MainPageDestination.StartGame) {
                            navController.navigateSingleTopTo(it.route)
                        }
                    },
                    label = if (it != MainPageDestination.StartGame) {
                        {
                            Text(text = stringRes(it.labelRes))
                        }
                    } else null
                )
            }
        }
    ) {
        MainPageNav(
            navController = navController,
        )
    }
}