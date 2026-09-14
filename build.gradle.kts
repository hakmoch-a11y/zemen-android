group = "com.github.zemenai"
version = providers.gradleProperty("VERSION_NAME").orElse("1.0.1").get()

plugins {
    id("com.android.library") version "8.5.2" apply false
    kotlin("android") version "1.9.25" apply false
    kotlin("jvm") version "1.9.25" apply false
}
