package org.openmw.ui.view

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun RtlScaffold(
    rtlContent: @Composable (content: @Composable () -> Unit) -> Unit,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl ) {
        rtlContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr ) {
                content()
            }
        }
    }
}

@Composable
fun RtlRowScopeScaffold(
    rtlContent: @Composable (content: @Composable RowScope.() -> Unit) -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl ) {
        rtlContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr ) {
                content()
            }
        }
    }
}