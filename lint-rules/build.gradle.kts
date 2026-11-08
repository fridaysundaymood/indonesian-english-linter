plugins {
    kotlin("jvm")
    `maven-publish`
}

kotlin { jvmToolchain(17) }

dependencies {
    compileOnly("com.android.tools.lint:lint-api:32.5.0")
    implementation("org.yaml:snakeyaml:2.3")
    implementation("com.networknt:json-schema-validator:1.5.6")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.16")
    testImplementation("com.android.tools.lint:lint-api:32.5.0")
    testImplementation("com.android.tools.lint:lint-tests:32.5.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.processResources { from(rootProject.file("schema/i18n.v1.json")) }

tasks.jar {
    manifest { attributes("Lint-Registry-v2" to "dev.androidkit.i18n.I18nIssueRegistry") }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

publishing { publications { create<MavenPublication>("maven") { from(components["java"]) } } }
