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

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.test {
    useJUnitPlatform()
}
