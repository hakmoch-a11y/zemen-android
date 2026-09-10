plugins {
    kotlin("jvm")
}

// This module has NO Android Gradle Plugin, NO Android dependency at
// all — that's the whole point (see android-sdk/README.md's "Why two
// modules"). It's also the only module that was actually compiled and
// unit-tested in the environment this SDK was originally built in; the
// android module could not be — see the README's verification section.

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

kotlin {
    jvmToolchain(17)
}


plugins.apply("maven-publish")

publishing {
    publications {
        register<MavenPublication>("maven") {
            groupId = "com.github.zemenai"
            artifactId = "zemen-ai-sdk-core"
            version = project.rootProject.version.toString()
            from(components["java"])
        }
    }
}
