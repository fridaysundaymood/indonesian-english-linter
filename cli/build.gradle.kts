plugins {
    kotlin("jvm")
    application
    `maven-publish`
}

application {
    mainClass.set("io.github.i18nlint.cli.MainKt")
    applicationName = "i18n-lint"
}

dependencies {
    implementation(project(":lint-rules"))
    implementation("com.android.tools.lint:lint:32.5.0")
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
}

tasks.test { useJUnitPlatform() }

tasks.jar {
    isZip64 = true
    archiveFileName.set("i18n-lint.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    manifest { attributes["Lint-Registry-v2"] = "dev.androidkit.i18n.I18nIssueRegistry" }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
}

publishing {
    publications { create<MavenPublication>("cli") { from(components["java"]) } }
}
