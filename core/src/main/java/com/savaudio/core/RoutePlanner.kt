package com.savaudio.core

/** Pure routing decisions, kept free of Android services so they can be unit tested. */
object RoutePlanner {

    /**
     * Picks the connected device a rule should play on.
     * Exact name match wins, then any device of the same kind (a reconnect can rename a device).
     * Returns null when the rule is off or its output is gone, which unpins the route.
     */
    fun resolve(rule: RouteRule, devices: List<OutputDevice>): OutputDevice? {
        if (!rule.enabled) return null
        val sameKind = devices.filter { it.kind == rule.kind }
        return sameKind.firstOrNull { it.name == rule.deviceName } ?: sameKind.firstOrNull()
    }

    /** Package name to target device for every rule that can run right now. */
    fun plan(rules: List<RouteRule>, devices: List<OutputDevice>): Map<String, OutputDevice> {
        val out = LinkedHashMap<String, OutputDevice>()
        for (rule in rules) {
            val device = resolve(rule, devices) ?: continue
            out[rule.packageName] = device
        }
        return out
    }
}
