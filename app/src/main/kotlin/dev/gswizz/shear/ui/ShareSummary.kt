/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import dev.gswizz.shear.core.UrlExtractor
import dev.gswizz.shear.core.url.UrlParts

/**
 * One line naming what a share linked to: the distinct hosts of its cleaned URLs, comma separated.
 *
 * History rows and the share-details header both show it, so the two stay identical for the same event. [hosts] is
 * empty when the text carried no links.
 */
data class ShareSummary(val hosts: String, val urlCount: Int) {
    companion object {
        fun of(cleanedText: String): ShareSummary {
            val hosts = UrlExtractor.extract(cleanedText).mapNotNull { UrlParts.parse(it.url)?.hostLower }
            return ShareSummary(hosts = hosts.distinct().joinToString(", "), urlCount = hosts.size)
        }
    }
}
