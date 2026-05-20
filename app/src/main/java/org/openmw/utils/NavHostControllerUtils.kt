package org.openmw.utils

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

fun NavHostController.navigateSingleTopTo(route: String) =
    this.navigate(route) {
        popUpTo(
            this@navigateSingleTopTo.graph.findStartDestination().id
        ) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }

fun NavHostController.navigateWithoutSaveTo(route: String) =
    this.navigate(route) {
        popUpTo(this@navigateWithoutSaveTo.graph.findStartDestination().id) {
            // inclusive = true
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
