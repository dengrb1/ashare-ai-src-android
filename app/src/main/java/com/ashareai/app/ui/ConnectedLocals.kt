package com.ashareai.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.ashareai.app.data.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not available outside the connected app composition.")
}

/** Shared feature state is provided by MainActivity, not owned by AppViewModel. */
val LocalMarketViewModel = staticCompositionLocalOf<MarketViewModel> {
    error("MarketViewModel is not available outside the connected app composition.")
}
