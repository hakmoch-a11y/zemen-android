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
                description.set(
                    "Zemen AI Android SDK for integrating AI chat and generic actions into Android applications."
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
