/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.rules

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A `clean-urls.json` or `query-filter.json` rule with its patterns compiled and its parameter names as a set. */
public class ParamRule(public val index: Int, public val patterns: PatternSet, public val params: Set<String>)

/** The four debounce actions Brave implements; anything else is dropped so newer list entries never misfire. */
public enum class DebounceAction(public val wireName: String) {
    REDIRECT("redirect"),
    BASE64_REDIRECT("base64,redirect"),
    REGEX_PATH("regex-path"),
    REGEX_PATH_TEMPLATE("regex-path-template");

    public companion object {
        public fun parse(name: String): DebounceAction? = entries.firstOrNull { it.wireName == name }
    }
}

/** A validated `debounce.json` rule; regex actions carry their compiled pattern and capture-group count. */
public class DebounceRule
internal constructor(
    public val index: Int,
    public val patterns: PatternSet,
    public val action: DebounceAction,
    public val param: String,
    public val regex: Regex?,
    public val groupCount: Int,
    public val prependScheme: String?,
    public val template: String?,
    /** True when the rule is gated on Brave's De-AMP preference. */
    public val requiresDeAmp: Boolean,
)

/**
 * Brave's rule sets, compiled and validated.
 *
 * Rules that fail validation are dropped and described in [diagnostics] rather than failing the whole load, matching
 * Brave's tolerance for list entries newer than the client. [isHealthy] is false when anything was dropped or a file
 * was missing, so the app can fall back to forwarding text unchanged.
 */
public class BraveRules(
    public val version: String,
    public val cleanUrls: List<ParamRule>,
    public val queryFilter: List<ParamRule>,
    public val conditionalTrackers: Map<String, Regex>,
    public val debounce: List<DebounceRule>,
    public val diagnostics: List<String>,
) {
    public val isHealthy: Boolean
        get() = diagnostics.isEmpty() && cleanUrls.isNotEmpty() && queryFilter.isNotEmpty() && debounce.isNotEmpty()

    public companion object {
        private const val UPSTREAM = "brave/UPSTREAM"
        private const val CLEAN_URLS = "brave/clean-urls.json"
        private const val DEBOUNCE = "brave/debounce.json"
        private const val QUERY_FILTER = "brave/query-filter.json"
        private const val CONDITIONAL_TRACKERS = "brave/conditional-trackers.json"
        private const val MAX_REGEX_LENGTH = 200
        private const val MAX_TEMPLATE_GROUPS = 9
        private const val DE_AMP_PREF = "brave.de_amp.enabled"
        private val json = Json { ignoreUnknownKeys = true }
        private val placeholder = Regex("""\$([1-9])""")

        /** Loads and compiles every rule file from [source]. */
        public fun load(source: RulesSource = ClasspathRulesSource): BraveRules {
            val diagnostics = mutableListOf<String>()
            fun read(name: String): String? =
                source.open(name)?.bufferedReader()?.readText().also { if (it == null) diagnostics += "missing $name" }
            fun <T> decode(name: String, parse: (String) -> T, empty: T): T {
                val text = read(name) ?: return empty
                return try {
                    parse(text)
                } catch (e: SerializationException) {
                    diagnostics += "unreadable $name: ${e.message}"
                    empty
                } catch (e: IllegalArgumentException) {
                    diagnostics += "unreadable $name: ${e.message}"
                    empty
                }
            }
            val version = RulesVersion.parse(read(UPSTREAM) ?: "")
            val cleanUrls =
                decode(
                    CLEAN_URLS,
                    { json.decodeFromString(ListSerializer(ParamRuleDto.serializer()), it) },
                    emptyList(),
                )
            val debounce =
                decode(
                    DEBOUNCE,
                    { json.decodeFromString(ListSerializer(DebounceRuleDto.serializer()), it) },
                    emptyList(),
                )
            val queryFilter =
                decode(
                    QUERY_FILTER,
                    { json.decodeFromString(ListSerializer(ParamRuleDto.serializer()), it) },
                    emptyList(),
                )
            val trackers =
                decode(
                    CONDITIONAL_TRACKERS,
                    { json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it) },
                    emptyMap(),
                )
            val compiled = compile(version, cleanUrls, queryFilter, trackers, debounce)
            return BraveRules(
                compiled.version,
                compiled.cleanUrls,
                compiled.queryFilter,
                compiled.conditionalTrackers,
                compiled.debounce,
                diagnostics + compiled.diagnostics,
            )
        }

        /** Compiles already-decoded rule lists; every rejected rule or pattern becomes a diagnostic. */
        public fun compile(
            version: String,
            cleanUrls: List<ParamRuleDto>,
            queryFilter: List<ParamRuleDto>,
            conditionalTrackers: Map<String, String>,
            debounce: List<DebounceRuleDto>,
        ): BraveRules {
            val diagnostics = mutableListOf<String>()
            val trackers = conditionalTrackers.mapNotNull { (key, pattern) ->
                runCatching { key to Regex(pattern) }
                    .getOrElse {
                        diagnostics += "conditional tracker $key: invalid regex '$pattern'"
                        null
                    }
            }
            return BraveRules(
                version = version,
                cleanUrls = compileParamRules("clean-urls", cleanUrls, diagnostics),
                queryFilter = compileParamRules("query-filter", queryFilter, diagnostics),
                conditionalTrackers = trackers.toMap(),
                debounce = debounce.mapIndexedNotNull { i, dto -> compileDebounce(i, dto, diagnostics) },
                diagnostics = diagnostics,
            )
        }

        private fun compileParamRules(
            file: String,
            dtos: List<ParamRuleDto>,
            diagnostics: MutableList<String>,
        ): List<ParamRule> = dtos.mapIndexedNotNull { i, dto ->
            val patterns =
                compilePatterns("$file[$i]", dto.include, dto.exclude, diagnostics) ?: return@mapIndexedNotNull null
            if (dto.params.isEmpty()) {
                diagnostics += "$file[$i]: no params"
                return@mapIndexedNotNull null
            }
            ParamRule(i, patterns, dto.params.toSet())
        }

        private fun compilePatterns(
            where: String,
            include: List<String>,
            exclude: List<String>,
            diagnostics: MutableList<String>,
        ): PatternSet? {
            fun compileAll(list: List<String>) = list.mapNotNull { raw ->
                val pattern = if (isAdblockSyntax(raw)) null else MatchPattern.parse(raw)
                if (pattern == null) diagnostics += "$where: unsupported pattern '$raw'"
                pattern
            }
            val inc = compileAll(include)
            val exc = compileAll(exclude)
            if (inc.isEmpty()) {
                diagnostics += "$where: no usable include pattern"
                return null
            }
            return PatternSet(inc, exc)
        }

        private fun isAdblockSyntax(raw: String): Boolean = raw.startsWith("||") || '^' in raw || '|' in raw

        private fun compileDebounce(index: Int, dto: DebounceRuleDto, diagnostics: MutableList<String>): DebounceRule? {
            val where = "debounce[$index]"
            val action = DebounceAction.parse(dto.action)
            if (action == null) {
                diagnostics += "$where: unknown action '${dto.action}'"
                return null
            }
            val patterns = compilePatterns(where, dto.include, dto.exclude, diagnostics) ?: return null
            if (dto.pref != null && dto.pref != DE_AMP_PREF) {
                diagnostics += "$where: unknown pref '${dto.pref}'"
                return null
            }
            var regex: Regex? = null
            var groups = 0
            if (action == DebounceAction.REGEX_PATH || action == DebounceAction.REGEX_PATH_TEMPLATE) {
                if (dto.param.length > MAX_REGEX_LENGTH) {
                    diagnostics += "$where: regex longer than $MAX_REGEX_LENGTH"
                    return null
                }
                regex =
                    runCatching { Regex(dto.param) }
                        .getOrElse {
                            diagnostics += "$where: invalid regex '${dto.param}'"
                            return null
                        }
                groups = regex.toPattern().matcher("").groupCount()
                if (groups < 1) {
                    diagnostics += "$where: regex captures no groups"
                    return null
                }
            }
            if (action == DebounceAction.REGEX_PATH_TEMPLATE) {
                val template = dto.redirectUrlTemplate
                if (template == null || !templateMatchesGroups(template, groups)) {
                    diagnostics += "$where: template placeholders do not match ${groups} capture groups"
                    return null
                }
            }
            return DebounceRule(
                index = index,
                patterns = patterns,
                action = action,
                param = dto.param,
                regex = regex,
                groupCount = groups,
                prependScheme = dto.prependScheme,
                template = dto.redirectUrlTemplate,
                requiresDeAmp = dto.pref == DE_AMP_PREF,
            )
        }

        private fun templateMatchesGroups(template: String, groups: Int): Boolean {
            if (groups > MAX_TEMPLATE_GROUPS) return false
            val placeholders = placeholder.findAll(template).map { it.groupValues[1].toInt() }.toSet()
            return placeholders == (1..groups).toSet()
        }
    }
}
