/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.engine

/**
 * Synthesizes test URLs from Brave match patterns so every rule in the vendored datasets becomes a golden test.
 *
 * [base] turns a pattern into a concrete scheme, host, and path that the pattern matches, plus the query prefix the
 * pattern demands, or null when the pattern can never carry a query. [synthesize] builds the strip-stage golden: every
 * parameter the rule lists plus one it does not (`shear_keep`) and a fragment.
 */
object GoldenSupport {
    const val HOST = "www.shear-golden.test"
    const val SEGMENT = "shear-seg"
    const val KEEP = "shear_keep=1"
    const val PREFIX = "shear_prefix=1"
    const val FRAGMENT = "#shear-frag"

    data class Base(val scheme: String, val host: String, val path: String, val prefix: String?) {
        fun withQuery(query: String, fragment: String = ""): String =
            "$scheme://$host$path?${listOfNotNull(prefix, query).joinToString("&")}$fragment"

        fun withPath(path: String): String = "$scheme://$host$path"
    }

    data class Golden(val url: String, val expected: String)

    private val shape = Regex("""^(\*|https?)://([^/]*)(/.*)$""")

    /** A URL skeleton matching [pattern], or null when the pattern's path can never carry extra query parameters. */
    fun base(pattern: String, bareHost: Boolean = false): Base? {
        val scheme: String
        val host: String
        val glob: String
        if (pattern == "<all_urls>") {
            scheme = "https"
            host = HOST
            glob = "/*"
        } else {
            val m = shape.find(pattern) ?: error("unsupported pattern $pattern")
            scheme = if (m.groupValues[1] == "*") "https" else m.groupValues[1]
            val rawHost = m.groupValues[2]
            host =
                when {
                    rawHost == "*" -> HOST
                    rawHost.startsWith("*.") -> if (bareHost) rawHost.substring(2) else "sub." + rawHost.substring(2)
                    else -> rawHost
                }
            glob = m.groupValues[3]
        }
        val question = glob.indexOf('?')
        val pathGlob = if (question >= 0) glob.substring(0, question) else glob
        val queryGlob = if (question >= 0) glob.substring(question + 1) else null
        val prefix =
            when {
                queryGlob != null -> if ('*' in queryGlob) queryGlob.replace("*", PREFIX) else return null
                pathGlob.endsWith("*") -> null
                else -> return null
            }
        return Base(scheme, host, pathGlob.replace("*", SEGMENT), prefix)
    }

    /** Golden for a strip-stage rule: [params] all present with values, `shear_keep` surviving, fragment intact. */
    fun synthesize(pattern: String, params: List<String>, bareHost: Boolean = false): Golden? {
        val base = base(pattern, bareHost) ?: return null
        val tracked = params.mapIndexed { i, p -> "$p=v$i" }
        return Golden(
            url = base.withQuery((tracked + KEEP).joinToString("&"), FRAGMENT),
            expected = base.withQuery(KEEP, FRAGMENT),
        )
    }
}
