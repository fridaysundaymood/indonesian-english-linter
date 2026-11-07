package io.github.i18nlint.cli

import kotlinx.serialization.json.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.LoaderOptions
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Properties
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.exitProcess

private val ruleIds = setOf("RawIdDisplay", "IdInTranslation", "ExceptionToUi", "LocalizedExceptionSwallowed", "MissingLocaleKey", "LocalizedDataLeak")
private val json = Json { prettyPrint = true }
internal data class Finding(val id: String, val severity: String, val message: String, val file: String, val line: Int, val snippet: String, val fix: String)
private data class Result(val status: String, val summary: String, val classification: String? = null, val evidence: List<JsonElement> = emptyList(), val artifacts: List<String> = emptyList())
private class Usage(message: String) : Exception(message)

fun main(args: Array<String>) {
    val started = Instant.now()
    val startNanos = System.nanoTime()
    val runId = UUID.randomUUID().toString()
    val operation = args.firstOrNull()?.takeIf { it == "lint" || it == "doctor" } ?: "unknown"
    val machine = args.contains("--json")
    val result = try {
        val options = parse(args.drop(1))
        when (operation) {
            "lint" -> lint(options, runId)
            "doctor" -> doctor(options)
            else -> throw Usage("Expected lint or doctor")
        }
    } catch (e: Usage) {
        Result("blocked", e.message ?: "Invalid arguments", "config")
    } catch (e: Exception) {
        log("error", e.message ?: e.javaClass.simpleName)
        Result("error", e.message ?: "Unexpected error", "environment")
    }
    val envelope = buildJsonObject {
        put("schema_version", "1"); put("tool", "i18n"); put("operation", operation); put("run_id", runId)
        put("status", result.status); put("summary", result.summary)
        result.classification?.let { put("classification", it) }
        put("evidence", JsonArray(result.evidence)); put("artifacts", JsonArray(result.artifacts.map(::JsonPrimitive)))
        put("started_at", started.toString()); put("duration_ms", (System.nanoTime() - startNanos) / 1_000_000)
    }
    if (machine) println(json.encodeToString(JsonObject.serializer(), envelope)) else {
        println("${result.status.uppercase()}: ${result.summary}")
        result.evidence.forEach { println(it) }
        result.artifacts.forEach { println("Artifact: $it") }
    }
    exitProcess(when (result.status) { "pass" -> 0; "fail", "flaky" -> 1; else -> 2 })
}

private fun parse(args: List<String>): Map<String, List<String>> {
    val flags = setOf("--json")
    val valued = setOf("--config", "--path", "--rules", "--baseline", "--baseline-create", "--baseline-update", "--report")
    val out = linkedMapOf<String, MutableList<String>>()
    var i = 0
    while (i < args.size) {
        val key = args[i++]
        if (key in flags) { out.getOrPut(key) { mutableListOf() }.add("true"); continue }
        if (key !in valued || i == args.size || args[i].startsWith("--")) throw Usage("Invalid or incomplete option: $key")
        out.getOrPut(key) { mutableListOf() }.add(args[i++])
    }
    for ((key, values) in out) if (key !in setOf("--path", "--report") && values.size > 1) throw Usage("Option $key may only be supplied once")
    return out
}

private fun doctor(options: Map<String, List<String>>): Result {
    if (options.keys.any { it != "--json" }) throw Usage("doctor accepts only --json")
    val sdk = findAndroidSdk(File(".").canonicalFile, System.getenv())
    val checks = listOf(
        Triple("java", Runtime.version().feature() >= 17, "Install JDK 17 or newer and set JAVA_HOME."),
        Triple("android_sdk", sdk != null, "Install the Android SDK and set ANDROID_SDK_ROOT or sdk.dir in local.properties."),
        Triple("gradle", commandExists(if (System.getProperty("os.name").startsWith("Windows")) "gradle.bat" else "gradle") || File("gradlew").isFile || File("gradlew.bat").isFile, "Install Gradle or use the project's Gradle wrapper."),
        Triple("lint", runCatching { Class.forName("com.android.tools.lint.Main") }.isSuccess, "Reinstall the CLI with Android Lint 32.5 dependencies.")
    )
    val evidence = checks.map { (name, ok, fix) -> buildJsonObject { put("check", name); put("status", if (ok) "pass" else "blocked"); if (!ok) put("fix", fix) } }
    val missing = checks.count { !it.second }
    return if (missing == 0) Result("pass", "Prerequisites available; AGP and Kotlin compatibility must be verified for the project.", evidence = evidence)
    else Result("blocked", "$missing prerequisite(s) unavailable", "environment", evidence)
}

private fun commandExists(name: String): Boolean = (System.getenv("PATH") ?: "").split(File.pathSeparator).any { File(it, name).isFile }

internal fun findAndroidSdk(root: File, environment: Map<String, String>): File? {
    val properties = Properties()
    val localProperties = File(root, "local.properties")
    if (localProperties.isFile) localProperties.inputStream().use { properties.load(it) }
    return sequenceOf(environment["ANDROID_SDK_ROOT"], environment["ANDROID_HOME"], properties.getProperty("sdk.dir"))
        .filterNotNull().filter { it.isNotBlank() }
        .map { File(it).let { path -> if (path.isAbsolute) path else File(root, it) } }
        .firstOrNull { it.isDirectory }
}

private fun lint(options: Map<String, List<String>>, runId: String): Result {
    val cwd = File(".").canonicalFile
    val paths = options["--path"]?.map { File(it).canonicalFile } ?: listOf(cwd)
    if (paths.any { !it.exists() }) throw Usage("Every --path must exist")
    if (paths.any { !it.isDirectory }) return Result("unsupported", "Standalone lint requires Android project directories, not individual source files; supply project metadata and --path directories.", "environment")
    val root = paths.first()
    val config = options["--config"]?.single()?.let { File(it).canonicalFile } ?: run {
        val candidates = listOf(File(root, "android-kit.yaml"), File(root, "i18n-lint.yaml")).filter { it.isFile }
        if (candidates.size > 1) throw Usage("Both configuration files exist; select one with --config")
        candidates.singleOrNull()
    }
    if (config != null) validateConfig(config)
    val selected = options["--rules"]?.single()?.split(',')?.map { it.trim() }?.toSet() ?: ruleIds
    if (selected.isEmpty() || selected.any { it !in ruleIds }) throw Usage("Unknown or empty rule selection")
    val reports = options["--report"] ?: listOf("markdown", "sarif")
    if (reports.any { it !in setOf("markdown", "sarif") }) throw Usage("--report must be markdown or sarif")
    val baselineOptions = listOf("--baseline", "--baseline-create", "--baseline-update").filter { it in options }
    if (baselineOptions.size > 1) throw Usage("Choose only one baseline action")
    val baseline = baselineOptions.singleOrNull()?.let { File(options.getValue(it).single()).canonicalFile }
    if (baselineOptions.singleOrNull() == "--baseline" && baseline?.isFile != true) throw Usage("Baseline does not exist")
    if (baselineOptions.singleOrNull() == "--baseline-create" && baseline?.exists() == true) throw Usage("Baseline already exists")
    if (baselineOptions.singleOrNull() == "--baseline-update" && baseline?.isFile != true) throw Usage("Baseline to update does not exist")
    val sdk = findAndroidSdk(root, System.getenv())
    if (sdk == null) return Result("blocked", "Android SDK unavailable; set ANDROID_SDK_ROOT, ANDROID_HOME, or sdk.dir in local.properties.", "environment")
    if (paths.any { !File(it, "AndroidManifest.xml").isFile && !File(it, "src/main/AndroidManifest.xml").isFile && !File(it, "settings.gradle").isFile && !File(it, "settings.gradle.kts").isFile })
        return Result("unsupported", "Android project metadata is missing; provide project directories containing a manifest or Gradle settings.", "environment")
    val relativeDir = ".android-kit/runs/$runId/i18n"
    val dir = File(root, relativeDir).also { it.mkdirs() }
    val xml = File(dir, "lint.xml")
    val sarif = File(dir, "lint.sarif")
    val baselineAction = baselineOptions.singleOrNull()
    val baselineInput = if (baselineAction == "--baseline") baseline else if (baseline != null) File(dir, "new-baseline.xml") else null
    val rulesStream = object {}.javaClass.getResourceAsStream("/indonesian-english-lint-checks.jar")
        ?: return Result("unsupported", "Custom lint rules jar is unavailable; run the packaged CLI distribution.", "environment")
    val rulesJar = Files.createTempFile("i18n-lint-checks-", ".jar").toFile()
    try {
        rulesStream.use { input -> rulesJar.outputStream().use { input.copyTo(it) } }
    val gradleProject = File(root, "settings.gradle").isFile || File(root, "settings.gradle.kts").isFile ||
        File(root, "build.gradle").isFile || File(root, "build.gradle.kts").isFile
    val command = if (gradleProject) {
        if (paths.size != 1) return Result("unsupported", "Gradle analysis accepts one project directory per run.", "environment")
        val windows = System.getProperty("os.name").startsWith("Windows")
        val wrapperName = if (windows) "gradlew.bat" else "gradlew"
        val wrapper = generateSequence(root) { it.parentFile }.map { File(it, wrapperName) }.firstOrNull { it.isFile }
        val executable = wrapper?.absolutePath ?: if (commandExists(if (windows) "gradle.bat" else "gradle")) {
            if (windows) "gradle.bat" else "gradle"
        } else return Result("blocked", "Gradle is unavailable; install Gradle or provide a Gradle wrapper.", "environment")
        fun quoted(file: File): String = "'" + file.absolutePath.replace("\\", "\\\\").replace("'", "\\'") + "'"
        val init = File(dir, "lint-init.gradle")
        val rules = selected.sorted().joinToString(", ") { "'$it'" }
        val baselineSetting = baselineInput?.let { "lint.baseline = p.file(${quoted(it)})" } ?: ""
        atomicWrite(init, """
            gradle.beforeProject { p ->
                p.afterEvaluate {
                    def android = p.extensions.findByName('android')
                    if (android != null) {
                        if (!p.pluginManager.hasPlugin('dev.androidkit.i18n-lint')) {
                            p.dependencies.add('lintChecks', p.files(${quoted(rulesJar)}))
                        }
                        def lint = android.lint
                        lint.checkOnly.clear()
                        lint.checkOnly.addAll([$rules])
                        lint.abortOnError = false
                        lint.xmlReport = true
                        lint.xmlOutput = p.file(${quoted(xml)})
                        lint.sarifReport = ${"sarif" in reports}
                        lint.sarifOutput = p.file(${quoted(sarif)})
                        $baselineSetting
                    }
                }
            }
        """.trimIndent())
        (if (windows) mutableListOf("cmd", "/c", executable) else mutableListOf(executable)).apply {
            addAll(listOf("--no-daemon", "-p", root.absolutePath, "-I", init.absolutePath, "lint"))
        }
    } else {
        val java = File(System.getProperty("java.home"), "bin/${if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"}")
        val classpath = System.getProperty("java.class.path").split(File.pathSeparator).joinToString(File.pathSeparator) { File(it).absolutePath }
        mutableListOf(java.path, "-cp", classpath, "com.android.tools.lint.Main", "--check", selected.sorted().joinToString(","), "--xml", xml.absolutePath, "--exitcode").apply {
            if ("sarif" in reports) addAll(listOf("--sarif", sarif.absolutePath))
            if (baselineInput != null) addAll(listOf("--baseline", baselineInput.absolutePath))
            addAll(paths.map { it.absolutePath })
        }
    }
    val builder = ProcessBuilder(command).directory(root).redirectErrorStream(true)
    builder.environment()["ANDROID_HOME"] = sdk.absolutePath
    builder.environment()["ANDROID_SDK_ROOT"] = sdk.absolutePath
    config?.let { builder.environment()["I18N_LINT_CONFIG"] = it.absolutePath }
    if (!gradleProject) builder.environment()["LINT_JARS"] = rulesJar.absolutePath
    val process = builder.start()
    process.inputStream.bufferedReader().useLines { lines -> lines.forEach { log("info", it) } }
    val code = process.waitFor()
    if (baselineAction != null && baselineAction != "--baseline" && baselineInput?.isFile == true && (code == 0 || code == 1)) {
        atomicWrite(baseline!!, baselineInput.readText())
    }
    if (baselineAction != null && baselineAction != "--baseline" && baselineInput?.isFile != true) return Result("unsupported", "Android Lint did not create the requested baseline; inspect stderr.", "environment")
    if (!xml.isFile) return Result("unsupported", "Android Lint did not produce a report (exit $code); check SDK, project metadata, and lint compatibility.", "environment")
    val allFindings = readFindings(xml, root)
    if (allFindings.any { it.id !in ruleIds }) return Result("unsupported", "Android Lint reported an analysis or configuration issue: ${allFindings.first { it.id !in ruleIds }.message}", "environment")
    val findings = allFindings.filter { it.id in selected }
    val evidence = findings.map(::findingEvidence)
    val artifacts = mutableListOf("$relativeDir/lint.xml")
    if ("markdown" in reports) {
        val text = buildString { append("# Localization lint\n\n"); if (findings.isEmpty()) append("No findings.\n") else findings.forEach { append("- **${it.id}** (${it.severity}) `${it.file}:${it.line}`: ${it.message.replace("\n", " ")}\n") } }
        atomicWrite(File(dir, "report.md"), text)
        artifacts.add("$relativeDir/report.md")
    }
    if ("sarif" in reports && sarif.isFile) artifacts.add("$relativeDir/lint.sarif")
    atomicWrite(File(dir, "index.json"), json.encodeToString(JsonObject.serializer(), buildJsonObject { put("artifacts", JsonArray(artifacts.map(::JsonPrimitive))) }) + "\n")
    artifacts.add("$relativeDir/index.json")
    if ("sarif" in reports && !sarif.isFile) return Result("unsupported", "Android Lint did not produce the requested SARIF report.", "environment", evidence, artifacts)
    if (code != 0 && findings.isEmpty()) return Result("unsupported", "Android Lint exited $code without actionable findings; inspect stderr.", "environment", evidence, artifacts)
    return Result(if (findings.isEmpty()) "pass" else "fail", "${findings.size} finding(s)", if (findings.isEmpty()) null else "app", evidence, artifacts)
    } finally {
        rulesJar.delete()
    }
}

internal fun findingEvidence(f: Finding): JsonObject = buildJsonObject {
    put("id", f.id); put("severity", f.severity); put("file", f.file); put("line", f.line)
    put("message", f.message); put("snippet", f.snippet); put("suggested_fix", f.fix)
    if (f.id == "RawIdDisplay") {
        val match = Regex("^Localize identifier from domain (.+?) before displaying it\\. Provenance: (.+)\\.$").matchEntire(f.message)
        if (match != null) {
            put("domain", match.groupValues[1])
            put("provenance", match.groupValues[2])
        }
    }
}

internal fun validateConfig(file: File) {
    if (!file.isFile) throw Usage("Configuration file does not exist: $file")
    val data = try { Yaml(SafeConstructor(LoaderOptions())).load<Any>(file.readText()) } catch (e: Exception) { throw Usage("Invalid YAML configuration: ${e.message}") }
    val root = data as? Map<*, *> ?: throw Usage("Configuration must be a mapping")
    val section = if (file.name == "android-kit.yaml") root["i18n"] ?: throw Usage("Missing i18n section") else root["i18n"] ?: root
    val schemaStream = object {}.javaClass.getResourceAsStream("/i18n.v1.json") ?: error("Bundled i18n schema is missing")
    val schema = schemaStream.use { JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(it) }
    val errors = schema.validate(ObjectMapper().valueToTree<com.fasterxml.jackson.databind.JsonNode>(section))
    if (errors.isNotEmpty()) throw Usage("Invalid i18n configuration: ${errors.joinToString("; ")}")
}

internal fun readFindings(file: File, root: File): List<Finding> {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("issue")
    return (0 until nodes.length).map { index ->
        val issue = nodes.item(index) as org.w3c.dom.Element
        val location = issue.getElementsByTagName("location").item(0) as? org.w3c.dom.Element
        val filename = location?.getAttribute("file") ?: ""
        val source = File(filename).let { if (it.isAbsolute) it else File(root, filename) }
        val path = runCatching { root.canonicalFile.toPath().relativize(source.canonicalFile.toPath()).toString() }.getOrDefault(filename)
        val line = location?.getAttribute("line")?.toIntOrNull() ?: 0
        val snippet = if (filename.isNotEmpty() && line > 0) runCatching { source.useLines { it.drop(line - 1).firstOrNull().orEmpty() } }.getOrDefault("") else ""
        Finding(issue.getAttribute("id"), issue.getAttribute("severity"), issue.getAttribute("message"), path, line, snippet, issue.getAttribute("explanation"))
    }
}

private fun log(level: String, message: String) {
    System.err.println(buildJsonObject { put("level", level); put("message", message) }.toString())
}

private fun atomicWrite(file: File, text: String) {
    Files.createDirectories(file.parentFile.toPath())
    val temp = Files.createTempFile(file.parentFile.toPath(), ".report-", ".tmp")
    try {
        Files.writeString(temp, text)
        try { Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    } finally { Files.deleteIfExists(temp) }
}
