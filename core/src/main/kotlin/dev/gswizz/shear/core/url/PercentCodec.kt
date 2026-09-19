/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.url

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Percent-encoding routines with Chromium's semantics, used only on values that rules produce.
 *
 * User-supplied URLs are never decoded or re-encoded; byte-for-byte preservation is the engine's contract. These
 * functions exist so that redirect destinations extracted from query parameters and `Location` headers are interpreted
 * exactly as Brave interprets them.
 */
public object PercentCodec {
    /**
     * Port of `base::UnescapeURLComponent` with SPACES, PATH_SEPARATORS, and URL_SPECIAL_CHARS_EXCEPT_PATH_SEPARATORS,
     * plus REPLACE_PLUS_WITH_SPACE when [plusToSpace] is set.
     *
     * Control characters, malformed escapes, invalid UTF-8, and Chromium's unsafe code points (bidi and format
     * controls, exotic spaces, non-characters) stay escaped.
     */
    public fun unescapeChromium(s: String, plusToSpace: Boolean): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val byte = if (c == '%') hexByteAt(s, i) else null
            when {
                c == '+' && plusToSpace -> {
                    out.append(' ')
                    i++
                }
                byte == null -> {
                    out.append(c)
                    i++
                }
                byte < HIGH_BIT -> {
                    if (byte < SPACE || byte == DEL) out.append(s, i, i + ESCAPE_LENGTH) else out.append(byte.toChar())
                    i += ESCAPE_LENGTH
                }
                else -> i = appendHighByteRun(s, i, out)
            }
        }
        return out.toString()
    }

    /**
     * Minimal GURL-style canonicalization for rule-produced URLs: trims surrounding whitespace, drops interior tab and
     * newline characters, lowercases the scheme and host, and percent-encodes spaces, controls, non-ASCII, and the
     * characters `"<>\`{}|^\` that are not URL code points. Existing escapes are kept as written.
     */
    public fun escapeIllegal(s: String): String {
        val cleaned = s.trim { it in SURROUNDING_WHITESPACE }.filterNot { it in INTERIOR_WHITESPACE }
        val schemeEnd = cleaned.indexOf("://")
        val out = StringBuilder(cleaned.length)
        val rest: String
        if (schemeEnd > 0 && cleaned.substring(0, schemeEnd).all { it.isLetterOrDigit() || it in "+-." }) {
            val authorityEnd = cleaned.indexOfAny(AUTHORITY_TERMINATORS, schemeEnd + SCHEME_SEPARATOR.length)
            val authorityStop = if (authorityEnd < 0) cleaned.length else authorityEnd
            val authority = cleaned.substring(schemeEnd + SCHEME_SEPARATOR.length, authorityStop)
            val at = authority.lastIndexOf('@')
            out.append(cleaned.substring(0, schemeEnd).lowercase()).append(SCHEME_SEPARATOR)
            if (at >= 0) out.append(authority, 0, at + 1)
            out.append(authority.substring(at + 1).lowercase())
            rest = cleaned.substring(authorityStop)
        } else {
            rest = cleaned
        }
        var i = 0
        while (i < rest.length) {
            val cp = rest.codePointAt(i)
            if (needsEscape(cp)) appendEscaped(cp, out) else out.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    /** `application/x-www-form-urlencoded` encoding, the inverse of [unescapeChromium] for redirect destinations. */
    internal fun formEncode(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)

    private fun appendHighByteRun(s: String, start: Int, out: StringBuilder): Int {
        val bytes = ArrayList<Int>()
        var end = start
        while (end < s.length && s[end] == '%') {
            val b = hexByteAt(s, end) ?: break
            if (b < HIGH_BIT) break
            bytes.add(b)
            end += ESCAPE_LENGTH
        }
        var k = 0
        while (k < bytes.size) {
            val decoded = decodeUtf8(bytes, k)
            if (decoded != null && !isUnsafeCodePoint(decoded.first)) {
                out.appendCodePoint(decoded.first)
                k += decoded.second
            } else {
                val at = start + k * ESCAPE_LENGTH
                out.append(s, at, at + ESCAPE_LENGTH)
                k++
            }
        }
        return end
    }

    /**
     * Decodes one UTF-8 code point starting at [at]; returns the code point and its byte length, or null if invalid.
     */
    private fun decodeUtf8(bytes: List<Int>, at: Int): Pair<Int, Int>? {
        val lead = bytes[at]
        val (length, minimum) =
            when {
                lead and 0xE0 == 0xC0 -> 2 to 0x80
                lead and 0xF0 == 0xE0 -> 3 to 0x800
                lead and 0xF8 == 0xF0 -> 4 to 0x10000
                else -> return null
            }
        if (at + length > bytes.size) return null
        var cp = lead and (0xFF shr (length + 1))
        for (j in 1 until length) {
            val b = bytes[at + j]
            if (b and 0xC0 != 0x80) return null
            cp = (cp shl 6) or (b and 0x3F)
        }
        val surrogate = cp in Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code
        if (cp < minimum || cp > Character.MAX_CODE_POINT || surrogate) return null
        return cp to length
    }

    private fun isUnsafeCodePoint(cp: Int): Boolean =
        cp == 0xA0 ||
            cp == 0x1680 ||
            cp in 0x2000..0x200F ||
            cp in 0x2028..0x202F ||
            cp == 0x205F ||
            cp in 0x2066..0x2069 ||
            cp == 0x3000 ||
            cp == 0xFEFF ||
            cp in 0xFFF9..0xFFFB ||
            cp in 0xFDD0..0xFDEF ||
            (cp and 0xFFFE) == 0xFFFE

    private fun needsEscape(cp: Int): Boolean =
        cp <= SPACE || cp == DEL || cp >= HIGH_BIT || cp.toChar() in ESCAPED_ASCII

    private fun appendEscaped(cp: Int, out: StringBuilder) {
        for (b in String(Character.toChars(cp)).toByteArray(StandardCharsets.UTF_8)) {
            out.append('%').append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
        }
    }

    private fun hexByteAt(s: String, i: Int): Int? {
        if (i + 2 >= s.length) return null
        val hi = Character.digit(s[i + 1], HEX_RADIX)
        val lo = Character.digit(s[i + 2], HEX_RADIX)
        return if (hi < 0 || lo < 0) null else (hi shl 4) or lo
    }

    private const val HEX_RADIX = 16
    private const val HEX = "0123456789ABCDEF"
    private const val ESCAPE_LENGTH = 3
    private const val SPACE = 0x20
    private const val DEL = 0x7F
    private const val HIGH_BIT = 0x80
    private const val SCHEME_SEPARATOR = "://"
    private const val ESCAPED_ASCII = "\"<>`{}|^\\"
    private const val SURROUNDING_WHITESPACE = " \t\n\r"
    private const val INTERIOR_WHITESPACE = "\t\n\r"
    private val AUTHORITY_TERMINATORS = charArrayOf('/', '?', '#')
}
