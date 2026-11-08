package io.github.i18nlint.cli

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertNotNull

class ProjectPathsTest {
    @TempDir lateinit var directory: Path

    @Test fun `SDK falls back to project local properties`() {
        val project = Files.createDirectory(directory.resolve("project"))
        val sdk = Files.createDirectory(project.resolve("sdk"))
        project.resolve("local.properties").writeText("sdk.dir=sdk\n")
        assertEquals(sdk.toFile(), findAndroidSdk(project.toFile(), emptyMap()))
        val other = Files.createDirectory(directory.resolve("other-sdk"))
        assertEquals(other.toFile(), findAndroidSdk(project.toFile(), mapOf("ANDROID_HOME" to other.toString())))
        assertEquals(sdk.toFile(), findAndroidSdk(project.toFile(), mapOf("ANDROID_SDK_ROOT" to directory.resolve("missing").toString())))
        project.resolve("local.properties").writeText("")
        assertNull(findAndroidSdk(project.toFile(), emptyMap()))
    }

    @Test fun `finding locations and snippets resolve against analyzed project`() {
        val project = Files.createDirectory(directory.resolve("project"))
        val source = project.resolve("Example.kt")
        source.writeText("first\nsecond\n")
        val xml = directory.resolve("lint.xml")
        xml.writeText("""<issues><issue id="RawIdDisplay" severity="Warning" message="example"><location file="Example.kt" line="2"/></issue></issues>""")
        val finding = readFindings(xml.toFile(), project.toFile()).single()
        assertEquals("Example.kt", finding.file)
        assertEquals("second", finding.snippet)
        xml.writeText("""<issues><issue id="RawIdDisplay" severity="Warning" message="example"><location file="${source.toString().replace("\\", "/")}" line="2"/></issue></issues>""")
        assertEquals("Example.kt", readFindings(xml.toFile(), project.toFile()).single().file)
    }
    @Test fun `raw identifier evidence exposes domain and provenance from lint message`() {
        val xml = directory.resolve("lint.xml")
        xml.writeText("""<issues><issue id="RawIdDisplay" severity="Warning" message="Localize identifier from domain paymentMethod before displaying it. Provenance: value Cash -&gt; variable method."><location file="Example.kt" line="2"/></issue></issues>""")
        val finding = readFindings(xml.toFile(), directory.toFile()).single()
        val evidence = findingEvidence(finding)
        assertEquals("paymentMethod", evidence.getValue("domain").jsonPrimitive.content)
        assertEquals("value Cash -> variable method", evidence.getValue("provenance").jsonPrimitive.content)
        assertEquals(finding.message, evidence.getValue("message").jsonPrimitive.content)

        assertFalse(findingEvidence(finding.copy(message = "Unstructured message")).containsKey("domain"))
        assertFalse(findingEvidence(finding.copy(id = "IdInTranslation")).containsKey("domain"))
    }
    @Test fun `baselines stay in project and contain issues XML`() {
        val project = Files.createDirectory(directory.resolve("baseline-project"))
        val outside = directory.resolve("outside.xml")
        outside.writeText("<issues/>")
        assertFails { resolveBaseline(project.toFile(), outside.toString(), "--baseline") }
        assertFails { resolveBaseline(project.toFile(), "../escape.xml", "--baseline-create") }
        assertFails { resolveBaseline(project.toFile(), "baseline.txt", "--baseline-create") }
        val baseline = project.resolve("baseline.xml")
        baseline.writeText("<other/>")
        assertFails { resolveBaseline(project.toFile(), "baseline.xml", "--baseline") }
        baseline.writeText("<issues/>")
        assertNotNull(resolveBaseline(project.toFile(), "baseline.xml", "--baseline"))
        val generated = project.resolve("generated.xml")
        generated.writeText("<other/>")
        assertFails { writeBaseline(project.toFile(), baseline.toFile(), generated.toFile(), "--baseline-update", baselineIdentity(baseline.toFile())) }
        assertEquals("<issues/>", Files.readString(baseline))
    }

    @Test fun `baseline paths reject symbolic link components`() {
        val project = Files.createDirectory(directory.resolve("symlink-project"))
        val real = Files.createDirectory(project.resolve("real"))
        val baseline = real.resolve("baseline.xml")
        baseline.writeText("<issues/>")
        val link = project.resolve("link")
        val fileLink = project.resolve("linked.xml")
        org.junit.jupiter.api.Assumptions.assumeTrue(runCatching {
            Files.createSymbolicLink(link, real)
            Files.createSymbolicLink(fileLink, baseline)
        }.isSuccess, "Symbolic links unavailable")
        assertFails { resolveBaseline(project.toFile(), "link/baseline.xml", "--baseline") }
        assertFails { resolveBaseline(project.toFile(), "link/new.xml", "--baseline-create") }
        assertFails { resolveBaseline(project.toFile(), "linked.xml", "--baseline-update") }
    }

    @Test fun `baseline create and update reject intervening changes`() {
        val project = Files.createDirectory(directory.resolve("race-project"))
        val generated = project.resolve("generated.xml")
        generated.writeText("<issues/>")
        val baseline = resolveBaseline(project.toFile(), "baseline.xml", "--baseline-create")
        baseline.writeText("<issues client=\"other\"/>")
        assertFails { writeBaseline(project.toFile(), baseline, generated.toFile(), "--baseline-create", null) }
        val identity = baselineIdentity(baseline)
        baseline.writeText("<issues client=\"changed\"/>")
        assertFails { writeBaseline(project.toFile(), baseline, generated.toFile(), "--baseline-update", identity) }
        assertEquals("<issues client=\"changed\"/>", baseline.readText())
    }

    @Test fun `wrapper discovery does not search project ancestors`() {
        val project = Files.createDirectory(directory.resolve("wrapper-project"))
        directory.resolve("gradlew").writeText("parent")
        assertNull(findWrapper(project.toFile(), "gradlew"))
        project.resolve("gradlew").writeText("project")
        assertEquals(project.resolve("gradlew").toFile(), findWrapper(project.toFile(), "gradlew"))
    }
}
