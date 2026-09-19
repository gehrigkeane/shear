/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The two scopes a shared-element modifier needs: the layout hosting the transition and the entry being shown.
 *
 * The navigation shell hands one to each screen; a screen composed on its own, as in tests, passes null and its shared
 * elements simply do not animate.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
data class SharedScopes(val transition: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

/** Marks this as the shared element [key] within [scopes], or leaves the modifier alone when [scopes] is null. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementIn(scopes: SharedScopes?, key: String): Modifier {
    if (scopes == null) return this
    return with(scopes.transition) {
        this@sharedElementIn.sharedElement(rememberSharedContentState(key), scopes.visibility)
    }
}

/**
 * Marks this as the shared bounds [key] within [scopes], or leaves the modifier alone when [scopes] is null.
 *
 * Bounds rather than element because text changes style between screens and should crossfade while it moves.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBoundsIn(scopes: SharedScopes?, key: String): Modifier {
    if (scopes == null) return this
    return with(scopes.transition) {
        this@sharedBoundsIn.sharedBounds(rememberSharedContentState(key), scopes.visibility)
    }
}
