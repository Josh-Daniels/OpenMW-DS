package org.openmw.utils

import android.widget.LinearLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowSize
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.window.core.layout.WindowWidthSizeClass
import org.openmw.ui.theme.OpenMWTheme

@Composable
fun getWindowSize(): DpSize {
    return with(LocalDensity.current) {
        currentWindowSize().toSize().toDpSize()
    }
}

@Composable
fun getOrientation(): Int { // in fact it's dumb XD
    val windowSize = getWindowSize()
    val vertical = windowSize.height > windowSize.width
    return if (vertical) 1 else 0
}

@Composable
fun isCompatHorizontal(): Boolean {
    return getLayoutType() == NavigationSuiteType.NavigationRail && getOrientation() == LinearLayout.HORIZONTAL
}

@Composable
fun getLayoutType(useDrawer: Boolean = false): NavigationSuiteType {
    return NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfoFix(currentWindowAdaptiveInfo(), useDrawer)
}

/**
 * adaptive navigation calculate
 */
@Composable
fun NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfoFix(adaptiveInfo: WindowAdaptiveInfo, useDrawer: Boolean = false): NavigationSuiteType {
    return with(adaptiveInfo) {
        val windowSize = with(LocalDensity.current) {
            currentWindowSize().toSize().toDpSize()
        }
        val vertical = windowSize.height > windowSize.width
        val horizontal = windowSize.height <= windowSize.width
        if ( windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED &&
            windowSize.width > 1200.dp) { // 1200dp+ we use Drawer
            if (useDrawer) NavigationSuiteType.NavigationDrawer else NavigationSuiteType.NavigationRail
        } else if (windowPosture.isTabletop) {
            NavigationSuiteType.NavigationBar
        } else if (
            windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED ||
            windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM ||
            (windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT
                    && horizontal) // phone + landscape we use rail
        ) {
            NavigationSuiteType.NavigationRail
        } else {
            NavigationSuiteType.NavigationBar
        }
    }
}


/*@Composable
fun GetAdaptiveRowItemCount(adaptiveInfo: WindowAdaptiveInfo, windowSize: DpSize) : Int {
    LazyVerticalGrid() { }
    GridCells.Adaptive

    val widthSize = adaptiveInfo.windowSizeClass.windowWidthSizeClass
    if (widthSize == WindowWidthSizeClass.EXPANDED)
}*/


fun Density.calculateRowItemCount(availableSize: Int, spacing: Dp, minSize: Dp) : Int {
    val count = maxOf((availableSize + spacing.roundToPx()) / (minSize.roundToPx() + spacing.roundToPx()), 1)
    return count
}

@Composable
fun getListAdaptiveHorizontalPadding(
    normalPadding: Dp = OpenMWTheme.spacing.normal
): PaddingValues {
    val layoutType = getLayoutType()
    val windowSize = getWindowSize()
    val isWideScreen = layoutType == NavigationSuiteType.NavigationRail || layoutType == NavigationSuiteType.NavigationDrawer
    if (isWideScreen)
        return PaddingValues(horizontal = windowSize.width / 9)
    else
        return PaddingValues(0.dp)
}