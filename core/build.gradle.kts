// Pure-Kotlin domain logic (schedules, block rules). No Android dependencies, so unit tests
// run on any JDK without the Android SDK or a device.
// Note: Android lint doesn't check this module. Stick to java.time APIs from Java 8 (e.g. avoid
// Duration.toMinutesPart, LocalDate.ofInstant) so it runs on the app's minSdk.
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
