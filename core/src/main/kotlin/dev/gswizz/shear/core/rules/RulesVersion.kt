/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.rules

/** Reads the rules version out of an `UPSTREAM` provenance file. */
public object RulesVersion {
    /** The `adblock-lists-commit` value, falling back to `commit`, then to `"unknown"`. */
    public fun parse(upstream: String): String {
        val entries = upstream.lines().mapNotNull { line -> line.split(':', limit = 2).takeIf { it.size == 2 } }
        val byKey = entries.associate { (k, v) -> k.trim() to v.trim() }
        return byKey["adblock-lists-commit"] ?: byKey["commit"] ?: "unknown"
    }
}
