package com.savaudio.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RouterStatus(
    val running: Boolean = false,
    /** package name to the output it is playing on */
    val active: Map<String, String> = emptyMap(),
    /** package name to the reason its route could not start */
    val errors: Map<String, String> = emptyMap(),
)

object RouterState {
    private val state = MutableStateFlow(RouterStatus())
    val status: StateFlow<RouterStatus> = state
    internal fun set(status: RouterStatus) { state.value = status }
}
