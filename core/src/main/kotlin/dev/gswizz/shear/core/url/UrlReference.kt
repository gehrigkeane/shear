/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.url

/**
 * Resolves `Location` header values against the URL that produced them, following RFC 3986 section 5.2.
 *
 * Absolute references with a scheme other than http or https resolve to null; callers use [schemeOf] to tell that case
 * apart from a malformed reference.
 */
public object UrlReference {
    private val schemePrefix = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):")

    /** The scheme named by an absolute [reference], or null when the reference is relative. */
    public fun schemeOf(reference: String): String? = schemePrefix.find(reference)?.groupValues?.get(1)

    /** Returns the target of [reference] relative to [base], or null if the result is not an http(s) URL. */
    public fun resolve(base: UrlParts, reference: String): UrlParts? {
        if (schemeOf(reference) != null) return UrlParts.parse(reference)
        if (reference.startsWith("//")) return UrlParts.parse("${base.scheme}:$reference")
        val hash = reference.indexOf('#')
        val beforeHash = if (hash >= 0) reference.substring(0, hash) else reference
        val fragment = if (hash >= 0) reference.substring(hash + 1) else null
        val question = beforeHash.indexOf('?')
        val refPath = if (question >= 0) beforeHash.substring(0, question) else beforeHash
        val refQuery = if (question >= 0) beforeHash.substring(question + 1) else null
        val path: String
        val query: String?
        if (refPath.isEmpty()) {
            path = base.path
            query = refQuery ?: base.query
        } else {
            path = removeDotSegments(if (refPath.startsWith('/')) refPath else merge(base.path, refPath))
            query = refQuery
        }
        val target = buildString {
            append(base.scheme).append("://")
            base.userInfo?.let { append(it).append('@') }
            append(base.host)
            base.port?.let { append(':').append(it) }
            append(path)
            query?.let { append('?').append(it) }
            fragment?.let { append('#').append(it) }
        }
        return UrlParts.parse(target)
    }

    private fun merge(basePath: String, refPath: String): String {
        if (basePath.isEmpty()) return "/$refPath"
        return basePath.substring(0, basePath.lastIndexOf('/') + 1) + refPath
    }

    private fun removeDotSegments(path: String): String {
        var input = path
        val output = StringBuilder()
        while (input.isNotEmpty()) {
            when {
                input.startsWith("../") -> input = input.substring(3)
                input.startsWith("./") -> input = input.substring(2)
                input.startsWith("/./") -> input = input.substring(2)
                input == "/." -> input = "/"
                input.startsWith("/../") -> {
                    input = input.substring(3)
                    dropLastSegment(output)
                }
                input == "/.." -> {
                    input = "/"
                    dropLastSegment(output)
                }
                input == "." || input == ".." -> input = ""
                else -> {
                    val start = if (input.startsWith('/')) 1 else 0
                    val next = input.indexOf('/', start).let { if (it < 0) input.length else it }
                    output.append(input, 0, next)
                    input = input.substring(next)
                }
            }
        }
        return output.toString()
    }

    private fun dropLastSegment(output: StringBuilder) {
        val slash = output.lastIndexOf("/")
        output.setLength(if (slash < 0) 0 else slash)
    }
}
