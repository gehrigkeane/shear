/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.rules

import java.io.InputStream

/**
 * Where rule files come from.
 *
 * The bundled snapshot is the only source today. A future remotely refreshed bundle needs nothing more than another
 * implementation of this interface, since every consumer reads plain streams by resource name.
 */
public fun interface RulesSource {
    /**
     * Opens the resource called [name] (for example `brave/clean-urls.json`), or returns null when it does not exist.
     */
    public fun open(name: String): InputStream?
}

/** Reads rule files from the `:core` classpath, where `mise run rules-update` places them. */
public object ClasspathRulesSource : RulesSource {
    override fun open(name: String): InputStream? = ClasspathRulesSource::class.java.getResourceAsStream("/$name")
}
