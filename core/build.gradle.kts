import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure

plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

kotlin {
    jvmToolchain(17)
}

plugins.apply("maven-publish")

extensions.configure<PublishingExtension> {
    publications {
        register<MavenPublication>("maven") {
            groupId = "com.github.zemenai"
            artifactId = "zemen-ai-sdk-core"
            version = project.rootProject.version.toString()
            from(components["java"])
        }
    }
}
