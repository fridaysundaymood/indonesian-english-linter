package dev.androidkit.i18n

import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ResourceDetector : Detector(), Detector.XmlScanner {
    private data class Resource(val context: XmlContext, val element: Element)
    private val resources = linkedMapOf<String, MutableMap<String, Resource>>()
    private var configuration: Configuration? = null
    override fun getApplicableElements(): Collection<String> = listOf("string", "plurals", "string-array")

    override fun visitElement(context: XmlContext, element: Element) {
        if (!element.hasAttribute("name") || element.getAttribute("translatable") == "false") return
        val folder = context.file.parentFile.name
        val key = element.tagName + "/" + element.getAttribute("name")
        resources.getOrPut(folder) { linkedMapOf() }[key] = Resource(context, element)
        val config = configuration ?: Configuration.load(context.project.dir).also { configuration = it }
        if (folder == "values" || folder !in config.locales) return
        val values = config.domains.values.flatMap { it.values }.filter { it.isNotBlank() }
        val matched = values.firstOrNull { Regex("(?<![\\p{L}\\p{N}_])" + Regex.escape(it) + "(?![\\p{L}\\p{N}_])").containsMatchIn(element.textContent) }
        if (matched != null) context.report(ID_TRANSLATION, element, context.getLocation(element), "Translate canonical identifier '$matched'.")
    }

    override fun afterCheckRootProject(context: Context) {
        val config = configuration ?: Configuration.load(context.project.dir)
        for (locale in config.locales.filter { it != "values" }) {
            val translated = resources[locale].orEmpty()
            for ((key, base) in resources["values"].orEmpty()) {
                val other = translated[key]
                val message = when {
                    other == null -> "$key is missing from $locale."
                    formats(base.element) != formats(other.element) -> "$key has incompatible format arguments in $locale."
                    else -> null
                } ?: continue
                val target = other ?: base
                target.context.report(MISSING_LOCALE, target.element, target.context.getLocation(target.element), message)
            }
        }
        resources.clear()
        configuration = null
    }

    private fun formats(element: Element): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        var next = 1
        val pattern = Regex("%(?:(\\d+)\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?([a-zA-Z%])")
        for (match in pattern.findAll(element.textContent)) {
            val type = match.groupValues[2].lowercase()
            if (type == "%" || type == "n") continue
            val index = match.groupValues[1].toIntOrNull() ?: next++
            val family = when (type) { "d", "o", "x" -> "integer"; "e", "f", "g", "a" -> "float"; else -> type }
            val key = "$index:$family"
            result[key] = (result[key] ?: 0) + 1
        }
        return result
    }
}
