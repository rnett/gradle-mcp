package dev.rnett.gradle.mcp.gradle.dependencies

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object MockGradleSourceZip {
    fun generate(version: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val root = "gradle-$version/"

            // Add root gradlew file
            zos.putNextEntry(ZipEntry("${root}gradlew"))
            zos.write("Mock gradlew script".toByteArray())
            zos.closeEntry()

            // Add a subproject source file
            zos.putNextEntry(ZipEntry("${root}subprojects/core/src/main/kotlin/org/gradle/api/Project.kt"))
            zos.write("package org.gradle.api\ninterface Project".toByteArray())
            zos.closeEntry()

            // Add build-logic file to test exclusions
            zos.putNextEntry(ZipEntry("${root}build-logic/src/main/kotlin/build-logic.kt"))
            zos.write("package buildlogic".toByteArray())
            zos.closeEntry()
        }
        return baos.toByteArray()
    }
}
