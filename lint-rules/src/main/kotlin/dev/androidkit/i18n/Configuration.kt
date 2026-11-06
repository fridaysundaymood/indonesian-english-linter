package dev.androidkit.i18n

import org.yaml.snakeyaml.Yaml
import java.io.File

internal data class Domain(val values: Set<String>, val localizer: String, val source: String?)
internal data class Configuration(
    val domains: Map<String, Domain>, val uiSinks: Set<String>, val dataSinks: Set<String>,
    val userExceptions: Set<String>, val locales: List<String>
) {
    companion object {
        fun load(project: File): Configuration {
            val selected = System.getenv("I18N_LINT_CONFIG")?.let(::File)
            val root = generateSequence(project) { it.parentFile }.firstOrNull { File(it, "i18n-lint.yaml").isFile || File(it, "android-kit.yaml").isFile } ?: project
            val candidates = listOf(File(root, "i18n-lint.yaml"), File(root, "android-kit.yaml")).filter { it.isFile }
            require(selected != null || candidates.size <= 1) { "Select one i18n configuration explicitly" }
            val file = selected ?: candidates.firstOrNull()
            val parsed = file?.inputStream()?.use { Yaml().load<Any>(it) } as? Map<*, *> ?: emptyMap<Any, Any>()
            val section = (parsed["i18n"] as? Map<*, *>) ?: parsed
            fun strings(value: Any?): Set<String> = (value as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
            fun sourceValues(source: String?): Set<String> {
                if (source == null || ':' !in source) return emptySet()
                val (filename, declaration) = source.split(':', limit = 2)
                val base = file?.parentFile ?: root
                val input = base.walkTopDown().onEnter { it.name !in setOf(".git", ".gradle", "build") }
                    .firstOrNull { it.isFile && (it.relativeTo(base).invariantSeparatorsPath == filename || it.name == filename) }
                    ?: return emptySet()
                val text = input.readText()
                val start = Regex("\\b(?:val|var|fun)\\s+" + Regex.escape(declaration) + "\\b[^=]*=").find(text)?.range?.last?.plus(1) ?: return emptySet()
                val expression = text.substring(start).trimStart()
                val end = expression.indexOf('\n').let { if (it < 0) expression.length else it }
                val firstLine = expression.substring(0, end)
                val opening = firstLine.indexOf('(')
                val body = if (opening >= 0) {
                    var depth = 0
                    var close = -1
                    for (i in opening until expression.length) {
                        when (expression[i]) {
                            '(' -> depth++
                            ')' -> if (--depth == 0) { close = i; break }
                        }
                    }
                    if (close >= 0) expression.substring(0, close + 1) else firstLine
                } else firstLine
                return Regex("\"([^\"\\r\\n]*)\"").findAll(body).map { it.groupValues[1] }.toSet()
            }
            val domains = (section["domains"] as? Map<*, *>)?.mapNotNull { (key, value) ->
                val entry = value as? Map<*, *> ?: return@mapNotNull null
                val name = key as? String ?: return@mapNotNull null
                val source = entry["values_from"] as? String
                name to Domain(strings(entry["values"]) + sourceValues(source), entry["localizer"] as? String ?: "", source)
            }?.toMap() ?: emptyMap()
            return Configuration(domains, strings(section["ui_sinks"]), strings(section["data_sinks"]), strings(section["user_facing_exceptions"]),
                (section["locales"] as? List<*>)?.mapNotNull { it as? String } ?: listOf("values", "values-in"))
        }
    }
}
