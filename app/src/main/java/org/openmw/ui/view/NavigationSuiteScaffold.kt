package org.openmw.ui.view

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collection.MutableVector
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastFirst
import org.openmw.utils.getLayoutType

@Composable
fun NavigationSuiteScaffold(
    navigationSuiteItems: NavigationSuiteScope.() -> Unit,
    modifier: Modifier = Modifier,
    layoutType: NavigationSuiteType = getLayoutType(),
    navigationSuiteColors: NavigationSuiteColors = NavigationSuiteDefaults.colors(),
    containerColor: Color = NavigationSuiteScaffoldDefaults.containerColor,
    contentColor: Color = NavigationSuiteScaffoldDefaults.contentColor,
    content: @Composable () -> Unit = {},
) {
    Surface(modifier = modifier, color = containerColor, contentColor = contentColor) {
        RtlScaffold(
            rtlContent = { content ->
                NavigationSuiteScaffoldLayout(
                    navigationSuite = {
                        NavigationSuite(
                            layoutType = layoutType,
                            colors = navigationSuiteColors,
                            content = navigationSuiteItems
                        )
                    },
                    layoutType = layoutType,
                    content = {
                        content()
                    }
                )
            },
            content = {
                Box(
                    Modifier.consumeWindowInsets(
                        when (layoutType) {
                            NavigationSuiteType.NavigationBar ->
                                NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom)
                            NavigationSuiteType.NavigationRail ->
                                NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start)
                            NavigationSuiteType.NavigationDrawer ->
                                DrawerDefaults.windowInsets.only(WindowInsetsSides.Start)
                            else -> NoWindowInsets
                        }
                    )
                ) {
                    content()
                }
            }
        )
    }
}

private const val NavigationSuiteLayoutIdTag = "navigationSuite"
private const val ContentLayoutIdTag = "content"

@Composable
fun NavigationSuiteScaffoldLayout(
    navigationSuite: @Composable () -> Unit,
    layoutType: NavigationSuiteType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(WindowAdaptiveInfoDefault),
    content: @Composable () -> Unit = {}
) {
    Layout({
        // Wrap the navigation suite and content composables each in a Box to not propagate the
        // parent's (Surface) min constraints to its children (see b/312664933).
        Box(Modifier.layoutId(NavigationSuiteLayoutIdTag)) { navigationSuite() }
        Box(Modifier.layoutId(ContentLayoutIdTag)) { content() }
    }) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        // Find the navigation suite composable through it's layoutId tag
        val navigationPlaceable =
            measurables
                .fastFirst { it.layoutId == NavigationSuiteLayoutIdTag }
                .measure(looseConstraints)
        val isNavigationBar = layoutType == NavigationSuiteType.NavigationBar
        val layoutHeight = constraints.maxHeight
        val layoutWidth = constraints.maxWidth
        // Find the content composable through it's layoutId tag
        val contentPlaceable =
            measurables
                .fastFirst { it.layoutId == ContentLayoutIdTag }
                .measure(
                    if (isNavigationBar) {
                        constraints.copy(
                            minHeight = layoutHeight - navigationPlaceable.height,
                            maxHeight = layoutHeight - navigationPlaceable.height
                        )
                    } else {
                        constraints.copy(
                            minWidth = layoutWidth - navigationPlaceable.width,
                            maxWidth = layoutWidth - navigationPlaceable.width
                        )
                    }
                )

        layout(layoutWidth, layoutHeight) {
            if (isNavigationBar) {
                // Place content above the navigation component.
                contentPlaceable.placeRelative(0, 0)
                // Place the navigation component at the bottom of the screen.
                navigationPlaceable.placeRelative(0, layoutHeight - (navigationPlaceable.height))
            } else {
                // Place the navigation component at the start of the screen.
                navigationPlaceable.placeRelative(0, 0)
                // Place content to the side of the navigation component.
                contentPlaceable.placeRelative(navigationPlaceable.width, 0)
            }
        }
    }
}

private val NoWindowInsets = WindowInsets(0, 0, 0, 0)

internal val WindowAdaptiveInfoDefault
    @Composable get() = currentWindowAdaptiveInfo()

@Composable
fun NavigationSuite(
    modifier: Modifier = Modifier,
    layoutType: NavigationSuiteType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(WindowAdaptiveInfoDefault),
    colors: NavigationSuiteColors = NavigationSuiteDefaults.colors(),
    content: NavigationSuiteScope.() -> Unit
) {
    val scope by rememberStateOfItems(content)
    // Define defaultItemColors here since we can't set NavigationSuiteDefaults.itemColors() as a
    // default for the colors param of the NavigationSuiteScope.item non-composable function.
    val defaultItemColors = NavigationSuiteDefaults.itemColors()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr ) {
        when (layoutType) {
            NavigationSuiteType.NavigationBar -> {
                NavigationBar(
                    modifier = modifier,
                    containerColor = colors.navigationBarContainerColor,
                    contentColor = colors.navigationBarContentColor
                ) {
                    scope.itemList.forEach {
                        NavigationBarItem(
                            modifier = it.modifier,
                            selected = it.selected,
                            onClick = it.onClick,
                            icon = { NavigationItemIcon(icon = it.icon, badge = it.badge) },
                            enabled = it.enabled,
                            label = it.label,
                            alwaysShowLabel = it.alwaysShowLabel,
                            colors =
                                it.colors?.navigationBarItemColors
                                    ?: defaultItemColors.navigationBarItemColors,
                            interactionSource = it.interactionSource
                        )
                    }
                }
            }
            NavigationSuiteType.NavigationRail -> {
                NavigationRail(
                    modifier = modifier,
                    containerColor = colors.navigationRailContainerColor,
                    contentColor = colors.navigationRailContentColor
                ) {
                    scope.itemList.forEach {
                        NavigationRailItem(
                            modifier = it.modifier.weight(1f),
                            selected = it.selected,
                            onClick = it.onClick,
                            icon = { NavigationItemIcon(icon = it.icon, badge = it.badge) },
                            enabled = it.enabled,
                            label = it.label,
                            alwaysShowLabel = it.alwaysShowLabel,
                            colors =
                                it.colors?.navigationRailItemColors
                                    ?: defaultItemColors.navigationRailItemColors,
                            interactionSource = it.interactionSource
                        )
                    }
                }
            }
            NavigationSuiteType.NavigationDrawer -> {
                PermanentDrawerSheet(
                    modifier = modifier,
                    drawerContainerColor = colors.navigationDrawerContainerColor,
                    drawerContentColor = colors.navigationDrawerContentColor
                ) {
                    scope.itemList.forEach {
                        NavigationDrawerItem(
                            modifier = it.modifier,
                            selected = it.selected,
                            onClick = it.onClick,
                            icon = it.icon,
                            badge = it.badge,
                            label = { it.label?.invoke() ?: Text("") },
                            colors =
                                it.colors?.navigationDrawerItemColors
                                    ?: defaultItemColors.navigationDrawerItemColors,
                            interactionSource = it.interactionSource
                        )
                    }
                }
            }
            NavigationSuiteType.None -> {
                /* Do nothing. */
            }
        }
    }
}

@Composable
private fun rememberStateOfItems(
    content: NavigationSuiteScope.() -> Unit
): State<NavigationSuiteItemProvider> {
    val latestContent = rememberUpdatedState(content)
    return remember { derivedStateOf { NavigationSuiteScopeImpl().apply(latestContent.value) } }
}

private class NavigationSuiteItem(
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: @Composable () -> Unit,
    val modifier: Modifier,
    val enabled: Boolean,
    val label: @Composable (() -> Unit)?,
    val alwaysShowLabel: Boolean,
    val badge: (@Composable () -> Unit)?,
    val colors: NavigationSuiteItemColors?,
    val interactionSource: MutableInteractionSource?
)

private interface NavigationSuiteItemProvider {
    val itemsCount: Int
    val itemList: MutableVector<NavigationSuiteItem>
}

sealed interface NavigationSuiteScope {
    fun item(
        selected: Boolean,
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        label: @Composable (() -> Unit)? = null,
        alwaysShowLabel: Boolean = true,
        badge: (@Composable () -> Unit)? = null,
        colors: NavigationSuiteItemColors? = null,
        interactionSource: MutableInteractionSource? = null
    )
}

private class NavigationSuiteScopeImpl : NavigationSuiteScope, NavigationSuiteItemProvider {

    override fun item(
        selected: Boolean,
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        label: @Composable (() -> Unit)?,
        alwaysShowLabel: Boolean,
        badge: (@Composable () -> Unit)?,
        colors: NavigationSuiteItemColors?,
        interactionSource: MutableInteractionSource?
    ) {
        itemList.add(
            NavigationSuiteItem(
                selected = selected,
                onClick = onClick,
                icon = icon,
                modifier = modifier,
                enabled = enabled,
                label = label,
                alwaysShowLabel = alwaysShowLabel,
                badge = badge,
                colors = colors,
                interactionSource = interactionSource
            )
        )
    }

    override val itemList: MutableVector<NavigationSuiteItem> = mutableVectorOf()

    override val itemsCount: Int
        get() = itemList.size
}

@Composable
private fun NavigationItemIcon(
    icon: @Composable () -> Unit,
    badge: (@Composable () -> Unit)? = null,
) {
    if (badge != null) {
        BadgedBox(badge = { badge.invoke() }) { icon() }
    } else {
        icon()
    }
}