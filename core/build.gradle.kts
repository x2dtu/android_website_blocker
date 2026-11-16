// Pure-Kotlin domain logic (schedules, block rules). No Android dependencies, so unit tests
// run on any JDK without the Android SDK or a device.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
