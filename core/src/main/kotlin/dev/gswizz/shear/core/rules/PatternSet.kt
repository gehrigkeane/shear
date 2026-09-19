/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.rules

import dev.gswizz.shear.core.url.UrlParts

/** The include/exclude pair every Brave rule carries; a URL matches when an include hits and no exclude does. */
public class PatternSet(public val include: List<MatchPattern>, public val exclude: List<MatchPattern>) {
    /** Index of the first include pattern that matches [url], or null when none does or an exclude applies. */
    public fun matches(url: UrlParts): Int? {
        if (exclude.any { it.matches(url) }) return null
        return include.indexOfFirst { it.matches(url) }.takeIf { it >= 0 }
    }
}
