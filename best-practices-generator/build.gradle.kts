plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.jsoup)
    implementation(libs.flexmark.html2md)

    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
