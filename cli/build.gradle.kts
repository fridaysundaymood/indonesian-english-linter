plugins {
    kotlin("jvm")
    application
    `maven-publish`
}

application {
    mainClass.set("io.github.i18nlint.cli.MainKt")
    applicationName = "i18n-lint"
}

val lintChecks by configurations.creating {
    isTransitive = false
}

dependencies {
    add(lintChecks.name, project(":lint-rules"))
    implementation("org.yaml:snakeyaml:2.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.networknt:json-schema-validator:1.5.6")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.16")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    from(rootProject.file("schema/i18n.v1.json"))
    from(lintChecks) {
        rename { "indonesian-english-lint-checks.jar" }
    }
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.jar)
    systemProperty("cliJar", tasks.jar.flatMap { it.archiveFile }.get().asFile.absolutePath)
}

tasks.jar {
    isZip64 = true
    entryCompression = ZipEntryCompression.DEFLATED
    archiveFileName.set("i18n-lint.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
}

publishing {
    publications { create<MavenPublication>("cli") { from(components["java"]) } }
}
