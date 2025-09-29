import io.ktor.plugin.features.DockerImageRegistry

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    `maven-publish`
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.vanniktech.maven.publish)
}

group = "dev.rnett.gradle-mcp"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.gradle.org/gradle/libs-releases")
}

application {
    mainClass.set("dev.rnett.gradle.mcp.Application")
}

dependencies {
    implementation(libs.gradle.tooling.api)

    implementation(libs.caffeine)
    implementation(libs.commons.io)

    implementation(libs.kotlin.reflect)
    implementation(libs.schema.kenerator.core)
    implementation(libs.schema.kenerator.serialization)
    implementation(libs.schema.kenerator.jsonschema)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mcp.sdk)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.di)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.config.yaml)

    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.test)
}

ktor {
    development = false
    docker {
        jreVersion = JavaVersion.VERSION_24
        externalRegistry = DockerImageRegistry.dockerHub(
            provider { "gradle-mcp-snapshots" },
            provider { "rnett" },
            providers.gradleProperty("dockerHubPassword")
        )
    }
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xannotation-default-target=param-property",
            "-Xcontext-parameters"
        )
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    languageVersion = JavaLanguageVersion.of(24)
}

buildConfig {
    packageName("dev.rnett.gradle.mcp")
    buildConfigField("APP_VERSION", provider { "${project.version}" })
}

tasks.shadowJar {
    archiveClassifier = ""
}

mavenPublishing {
    publishToMavenCentral()
    coordinates(group.toString(), project.name, project.version.toString())

    signAllPublications()

    pom {
        name.set("Gradle MCP")
        description.set("A MCP server for Gradle.")
        inceptionYear.set("2025")
        url.set("https://github.com/rnett/gradle-mcp/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("rnett")
                name.set("Ryan Nett")
                url.set("https://github.com/rnett/")
            }
        }
        scm {
            url.set("https://github.com/rnett/gradle-mcp/")
            connection.set("scm:git:git://github.com/rnett/gradle-mcp.git")
            developerConnection.set("scm:git:ssh://git@github.com/rnett/gradle-mcp.git")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("shadow") {
            from(components["shadow"])
        }
    }
}