package com.ashareai.app.ui

/** Common presentation state used by connected Compose screens. */
sealed interface ScreenState<out T> {
    data object Loading : ScreenState<Nothing>
    data object Empty : ScreenState<Nothing>
    data class Content<T>(val value: T) : ScreenState<T>
    data class Error<T>(val message: String, val previous: T? = null) : ScreenState<T>
}

internal fun <T> ScreenState<T>.contentOrNull(): T? = when (this) {
    is ScreenState.Content -> value
    is ScreenState.Error -> previous
    ScreenState.Loading, ScreenState.Empty -> null
}
