package dev.rnett.gradle.mcp.tools

import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.notExists

object GradlePathUtils {

    fun getExistingPath(path: String): Path {
        val path = try {
            Path(path).toRealPath()
        } catch (e: NoSuchFileException) {
            throw IllegalArgumentException("Path ${path} does not exist", e)
        }
        if (path.notExists()) {
            throw IllegalArgumentException("Path $path does not exist")
        }
        return path
    }

    fun getExistingDirPath(root: GradleProjectRoot): Path {
        val path = getExistingPath(root.projectRoot)
        if (!path.isDirectory()) {
            throw IllegalArgumentException("Path $path is not a directory")
        }
        return path
    }

    fun isGradleProjectDir(path: Path): Boolean {
        if (path.notExists() || !path.isDirectory())
            return false

        val dirName = path.name

        return path.listDirectoryEntries("*.gradle*").any {
            it.isRegularFile() && (it.name.startsWith("build.gradle") || it.name.startsWith("$dirName.gradle"))
        }
    }

    fun isGradleRootProjectDir(path: Path): Boolean {
        if (path.notExists() || !path.isDirectory())
            return false

        return path.listDirectoryEntries("gradlew*").any {
            it.isRegularFile() && (it.name == "gradlew" || it.name == "gradlew.bat")
        }
    }
}