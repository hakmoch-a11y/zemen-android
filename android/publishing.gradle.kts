plugins {
    id("maven-publish")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.zemenai"
            artifactId = "zemen-ai-android"
            version = project.findProperty("VERSION_NAME")?.toString() ?: "1.0.0"
            afterEvaluate { from(components["release"]) }
        }
    }
}
