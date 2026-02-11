plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.power.assert)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

kotlin {
    jvmToolchain(17)
}
