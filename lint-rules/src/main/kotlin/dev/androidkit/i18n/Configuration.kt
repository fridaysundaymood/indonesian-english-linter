package dev.androidkit.i18n

import com.android.tools.lint.detector.api.Context
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Files

internal data class Domain(val values: Set<String>, val localizer: String, val source: String?)
internal data class Configuration(
    val domains: Map<String, Domain>, val uiSinks: Set<String>, val dataSinks: Set<String>,
    val userExceptions: Set<String>, val locales: List<String>
) {
    companion object {
        fun load(context: Context): Configuration {
            val project = context.project.dir.canonicalFile
            val root = context.client.getRootDir()?.canonicalFile
            val rootHasConfig = root != null && project.toPath().startsWith(root.toPath()) &&
                (File(root, "i18n-lint.yaml").isFile || File(root, "android-kit.yaml").isFile)
            return load(if (rootHasConfig) root!! else project)
        }

        fun load(project: File): Configuration {
            val selected = System.getenv("I18N_LINT_CONFIG")?.let(::File)
            val root = project.canonicalFile
            val candidates = listOf(File(root, "i18n-lint.yaml"), File(root, "android-kit.yaml")).filter { it.isFile }
            require(selected != null || candidates.size <= 1) { "Select one i18n configuration explicitly" }
            val file = selected ?: candidates.firstOrNull()
            val parsed = if (file == null) emptyMap<Any, Any>() else {
                val data = file.inputStream().use { Yaml(SafeConstructor(LoaderOptions())).load<Any>(it) }
                data as? Map<*, *> ?: throw IllegalArgumentException("Configuration must be a mapping")
            }
            val section = if (file?.name == "android-kit.yaml") {
                parsed["i18n"] ?: throw IllegalArgumentException("Missing i18n section")
            } else parsed["i18n"] ?: parsed
            if (file != null) {
                val stream = Configuration::class.java.getResourceAsStream("/i18n.v1.json")
                    ?: error("Bundled i18n schema is missing")
                val schema = stream.use { JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(it) }
                val errors = schema.validate(ObjectMapper().valueToTree<JsonNode>(section))
                require(errors.isEmpty()) { "Invalid i18n configuration: ${errors.joinToString("; ")}" }
            }
            val config = section as Map<*, *>
            fun strings(value: Any?): Set<String> = (value as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
            fun sourceValues(source: String?): Set<String> {
                if (source == null) return emptySet()
                require(':' in source) { "Invalid values_from: $source" }
                val (filename, declaration) = source.split(':', limit = 2)
                require(filename.isNotBlank() && declaration.isNotBlank()) { "Invalid values_from: $source" }
                val base = (file?.absoluteFile?.parentFile ?: root).canonicalFile.toPath()
                val relative = java.nio.file.Paths.get(filename)
                require(!relative.isAbsolute && relative.none { it.toString() == ".." }) { "values_from must stay within the configuration directory" }
                val direct = base.resolve(relative).normalize()
                if (!Files.isRegularFile(direct)) return emptySet()
                require(direct.toRealPath().startsWith(base)) { "values_from must stay within the configuration directory" }
                val input = direct.toFile()
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
            val domains = (config["domains"] as? Map<*, *>)?.mapNotNull { (key, value) ->
                val entry = value as? Map<*, *> ?: return@mapNotNull null
                val name = key as? String ?: return@mapNotNull null
                val source = entry["values_from"] as? String
                name to Domain(strings(entry["values"]) + sourceValues(source), entry["localizer"] as? String ?: "", source)
            }?.toMap() ?: emptyMap()
            return Configuration(domains, strings(config["ui_sinks"]), strings(config["data_sinks"]), strings(config["user_facing_exceptions"]),
                (config["locales"] as? List<*>)?.mapNotNull { it as? String } ?: listOf("values", "values-in"))
        }
    }
}
