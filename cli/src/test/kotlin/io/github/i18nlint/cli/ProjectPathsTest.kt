package io.github.i18nlint.cli

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
