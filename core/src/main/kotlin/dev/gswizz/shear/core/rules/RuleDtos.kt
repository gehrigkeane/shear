/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One entry of `clean-urls.json` or `query-filter.json`. */
@Serializable
public data class ParamRuleDto(
    val include: List<String>,
    val exclude: List<String> = emptyList(),
    val params: List<String>,
)

/** One entry of `debounce.json`. */
@Serializable
public data class DebounceRuleDto(
    val include: List<String>,
    val exclude: List<String> = emptyList(),
    val action: String,
    val param: String,
    val pref: String? = null,
    @SerialName("prepend_scheme") val prependScheme: String? = null,
    @SerialName("redirect_url_template") val redirectUrlTemplate: String? = null,
)
