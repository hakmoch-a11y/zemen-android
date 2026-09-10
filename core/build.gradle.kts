plugins {
    kotlin("jvm")
    `maven-publish`
}

group = rootProject.group
version = providers.gradleProperty("VERSION_NAME")
    .orElse(rootProject.version.toString())
    .get()

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])

            groupId = rootProject.group.toString()
            artifactId = "zemen-ai-core"
            version = project.version.toString()

            pom {
                name.set("Zemen AI Core")
                description.set(
                    "Platform-independent Zemen AI SDK core."
                )
                url.set(
                    "https://github.com/hakmoch-a11y/zemen-android"
                )

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                scm {
                    url.set(
                        "https://github.com/hakmoch-a11y/zemen-android"
                    )
                    connection.set(
                        "scm:git:https://github.com/hakmoch-a11y/zemen-android.git"
                    )
                    developerConnection.set(
                        "scm:git:ssh://git@github.com/hakmoch-a11y/zemen-android.git"
                    )
                }
            }
        }
    }
}
