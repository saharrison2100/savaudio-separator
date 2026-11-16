package com.savaudio.service

import android.content.Context
import com.savaudio.core.RouteRule
import com.savaudio.core.RuleCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Persistent routing rules, shared by the UI and the service (same process). */
class RuleRepository private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("routes", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(RuleCodec.decode(prefs.getString(KEY, null)))
    val rules: StateFlow<List<RouteRule>> = state

    @Synchronized
    fun put(rule: RouteRule) = save(state.value.filterNot { it.packageName == rule.packageName } + rule)

    @Synchronized
    fun remove(packageName: String) = save(state.value.filterNot { it.packageName == packageName })

    private fun save(rules: List<RouteRule>) {
        prefs.edit().putString(KEY, RuleCodec.encode(rules)).apply()
        state.value = rules
    }

    companion object {
        private const val KEY = "rules"
        @Volatile private var instance: RuleRepository? = null

        fun get(context: Context): RuleRepository =
            instance ?: synchronized(this) {
                instance ?: RuleRepository(context.applicationContext).also { instance = it }
            }
    }
}
