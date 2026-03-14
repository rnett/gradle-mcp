package dev.rnett.gradle.mcp.utils

object OS {
    val name: String = System.getProperty("os.name").lowercase()
    val isWindows: Boolean = name.contains("win")
    val isMac: Boolean = name.contains("mac")
    val isLinux: Boolean = name.contains("nix") || name.contains("nux") || name.contains("aix")
}
