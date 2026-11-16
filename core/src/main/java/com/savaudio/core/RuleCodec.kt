package com.savaudio.core

/** Tab separated, one rule per line. Small enough to live in SharedPreferences and easy to test. */
object RuleCodec {

    fun encode(rules: List<RouteRule>): String = rules.joinToString("\n") { r ->
        listOf(escape(r.packageName), r.kind.name, r.enabled.toString(), escape(r.deviceName)).joinToString("\t")
    }

    fun decode(text: String?): List<RouteRule> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split("\n").mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size != 4) return@mapNotNull null
            val kind = DeviceKind.values().firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
            val pkg = unescape(parts[0])
            if (pkg.isBlank()) return@mapNotNull null
            RouteRule(pkg, kind, unescape(parts[3]), parts[2] == "true")
        }
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
