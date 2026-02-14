package dev.rnett.gradle.mcp.tools.repl

import dev.rnett.gradle.mcp.BuildConfig
import dev.rnett.gradle.mcp.gradle.fixtures.testGradleProject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AndroidCompose8ReplIntegrationTest : BaseReplIntegrationTest() {

    private fun setupAndroidProject(agpVersion: String) {
        initProject(testGradleProject {
            val androidHome = System.getenv("ANDROID_HOME") ?: "${System.getProperty("user.home")}\\AppData\\Local\\Android\\Sdk"
            file("local.properties", "sdk.dir=${androidHome.replace("\\", "\\\\")}")
            file("gradle.properties", "android.useAndroidX=true")

            settings(
                """
                pluginManagement {
                    repositories {
                        google()
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }
                dependencyResolutionManagement {
                    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                    repositories {
                        google()
                        mavenCentral()
                    }
                }
                rootProject.name = "android-app"
            """.trimIndent()
            )

            buildScript(
                """
            plugins {
                id("com.android.application") version "$agpVersion"
                kotlin("android") version "${BuildConfig.KOTLIN_VERSION}"
                id("org.jetbrains.kotlin.plugin.compose") version "${BuildConfig.KOTLIN_VERSION}"
            }
            
            android {
                namespace = "com.example.app"
                compileSdk = ${BuildConfig.ANDROID_COMPILE_SDK}
                
                defaultConfig {
                    applicationId = "com.example.app"
                    minSdk = ${BuildConfig.ANDROID_MIN_SDK}
                    targetSdk = ${BuildConfig.ANDROID_TARGET_SDK}
                    versionCode = 1
                    versionName = "1.0"
                }
                
                buildFeatures {
                    compose = true
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }

            kotlin {
                jvmToolchain(17)
            }
            
            dependencies {
                implementation("androidx.compose.ui:ui:${BuildConfig.ANDROIDX_COMPOSE_VERSION}")
                implementation("androidx.compose.material:material:${BuildConfig.ANDROIDX_COMPOSE_VERSION}")
                implementation("androidx.compose.ui:ui-tooling-preview:${BuildConfig.ANDROIDX_COMPOSE_VERSION}")
                implementation("androidx.activity:activity-compose:${BuildConfig.ANDROIDX_ACTIVITY_COMPOSE_VERSION}")
            }
        """.trimIndent()
            )

            file(
                "src/main/AndroidManifest.xml", """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application>
                    </application>
                </manifest>
            """.trimIndent()
            )

            file(
                "src/main/kotlin/com/example/app/MainActivity.kt", """
                package com.example.app
                import androidx.compose.material.Text
                import androidx.compose.runtime.Composable

                @Composable
                fun App() {
                    Text("Hello Android Compose")
                }
            """.trimIndent()
            )
        })
    }

    @Test
    @Order(1)
    fun `Android Compose REPL with AGP 8`() = runTest(timeout = 10.minutes) {
        setupAndroidProject(BuildConfig.AGP_8_VERSION)
        startRepl(projectPath = ":", sourceSet = "main")
    }
}
