package dev.rnett.gradle.mcp.dependencies.search

import java.nio.file.Path

enum class OsType(val key: String) {
    Windows("windows"),
    MacOS("macos"),
    Linux("linux");

    val libPrefix: String get() = if (this == Windows) "" else "lib"
    val libExtension: String
        get() = when (this) {
            MacOS -> "dylib"
            Windows -> "dll"
            Linux -> "so"
        }

    companion object {
        fun current(): OsType {
            val osName = System.getProperty("os.name").lowercase()
            return when {
                osName.contains("mac") -> MacOS
                osName.contains("win") -> Windows
                else -> Linux
            }
        }
    }
}

object TreeSitterUtils {
    fun getCSymbol(name: String): String = when (name) {
        "csharp" -> "c_sharp"
        "c++" -> "cpp"
        else -> name
    }

    fun platformKey(): String {
        val os = OsType.current()
        val key = os.key

        val osArch = System.getProperty("os.arch").lowercase()
        val arch = when {
            osArch.contains("aarch64") || osArch.contains("arm64") -> if (key == "macos") "arm64" else "aarch64"
            osArch.contains("amd64") || osArch.contains("x86_64") -> "x86_64"
            else -> osArch
        }

        return "$key-$arch"
    }

    fun libPath(cacheDir: Path, name: String): Path {
        val cSymbol = getCSymbol(name)
        val libName = "tree_sitter_$cSymbol"
        val os = OsType.current()
        return cacheDir.resolve("${os.libPrefix}$libName.${os.libExtension}")
    }
}
