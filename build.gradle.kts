plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.20"
    id("io.ktor.plugin") version "3.3.0"
    `maven-publish`
    id("com.github.gmazzo.buildconfig") version "5.6.8"
    id("com.vanniktech.maven.publish") version "0.34.0"
}

group = "dev.rnett.gradle-mcp"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.gradle.org/gradle/libs-releases")
}

application {
    mainClass.set("dev.rnett.gradle.mcp.Application ")
}

dependencies {
    implementation("org.gradle:gradle-tooling-api:9.1.0")

    implementation("com.github.ben-manes.caffeine:caffeine:3.2.2")
    implementation("commons-io:commons-io:2.20.0")

    implementation(kotlin("reflect"))
    implementation("io.github.smiley4:schema-kenerator-core:2.4.0")
    implementation("io.github.smiley4:schema-kenerator-serialization:2.4.0")
    implementation("io.github.smiley4:schema-kenerator-jsonschema:2.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("io.modelcontextprotocol:kotlin-sdk:0.7.2")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-di")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-config-yaml")

    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(kotlin("test"))
}

ktor {
    development = false
    docker {
        jreVersion = JavaVersion.VERSION_24
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

    signAllPublications()

    coordinates("com.example.mylibrary", "mylibrary-runtime", "1.0.3-SNAPSHOT")

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