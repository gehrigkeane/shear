/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.engine

/**
 * Synthesizes test URLs from Brave match patterns so every rule in the vendored datasets becomes a golden test.
 *
 * The URL carries every parameter the rule lists plus one it does not (`shear_keep`), and a fragment, so an assertion
 * can check that exactly the listed parameters vanish and everything else survives byte for byte.
 */
object GoldenSupport {
    const val HOST = "www.shear-golden.test"
    const val SEGMENT = "shear-seg"
    const val KEEP = "shear_keep=1"
    const val PREFIX = "shear_prefix=1"
    const val FRAGMENT = "#shear-frag"

    data class Golden(val url: String, val expected: String)

    private val shape = Regex("""^(\*|https?)://([^/]*)(/.*)$""")

    /**
     * Builds a golden for [pattern] carrying [params], or null when the pattern's path can never carry a query.
     * [bareHost] picks the apex instead of a subdomain for `*.host` patterns.
     */
    fun synthesize(pattern: String, params: List<String>, bareHost: Boolean = false): Golden? {
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
        val path = pathGlob.replace("*", SEGMENT)
        val prefix =
            when {
                queryGlob != null -> if ('*' in queryGlob) queryGlob.replace("*", PREFIX) else queryGlob
                pathGlob.endsWith("*") -> null
                else -> return null
            }
        val tracked = params.mapIndexed { i, p -> "$p=v$i" }
        val query = (listOfNotNull(prefix) + tracked + KEEP).joinToString("&")
        val expectedQuery = (listOfNotNull(prefix) + KEEP).joinToString("&")
        return Golden("$scheme://$host$path?$query$FRAGMENT", "$scheme://$host$path?$expectedQuery$FRAGMENT")
    }
}
