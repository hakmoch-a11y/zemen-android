import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure

extensions.configure<PublishingExtension> {
    publications {
        register<MavenPublication>("release") {
            groupId = rootProject.group.toString()
            artifactId = "zemen-ai-android"
            version = project.findProperty("VERSION_NAME")?.toString()
                ?: rootProject.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Zemen AI Android SDK")
                description.set("Zemen AI Android SDK for AI-assisted chat and generic host-app actions")
                url.set("https://github.com/hakmoch-a11y/zemen-android")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/hakmoch-a11y/zemen-android.git")
                    developerConnection.set("scm:git:ssh://github.com/hakmoch-a11y/zemen-android.git")
                    url.set("https://github.com/hakmoch-a11y/zemen-android")
                }
            }
        }
    }
}
