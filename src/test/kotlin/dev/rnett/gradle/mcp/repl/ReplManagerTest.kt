package dev.rnett.gradle.mcp.repl

import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ReplManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private val config1 = ReplConfig(classpath = listOf("a.jar"))
    private val config2 = ReplConfig(classpath = listOf("b.jar"))

    private fun createDummyJar(path: Path) {
        java.util.zip.ZipOutputStream(path.toFile().outputStream()).use {
            val manifest = java.util.jar.Manifest()
            manifest.mainAttributes[java.util.jar.Attributes.Name.MANIFEST_VERSION] = "1.0"
            // We need a main class that exists in the dummy jar, but we don't really have one.
            // Alternatively, we just use a script and change how DefaultReplManager starts it.
            // But DefaultReplManager is hardcoded to use java -jar.
            // Let's just use a real class from the current classpath as main class!
            manifest.mainAttributes[java.util.jar.Attributes.Name.MAIN_CLASS] = DummyMain::class.java.name

            it.putNextEntry(java.util.zip.ZipEntry("META-INF/MANIFEST.MF"))
            manifest.write(it)
            it.closeEntry()

            // Add the class file
            val classResource = DummyMain::class.java.name.replace(".", "/") + ".class"
            val classBytes = this::class.java.classLoader.getResourceAsStream(classResource)!!.readAllBytes()
            it.putNextEntry(java.util.zip.ZipEntry(classResource))
            it.write(classBytes)
            it.closeEntry()
        }
    }

    class DummyMain {
        companion object {
            @JvmStatic
            fun main(args: Array<String>) {
                Thread.sleep(10000)
            }
        }
    }

    private fun createManager(): DefaultReplManager {
        val jarPath = tempDir.resolve("repl-worker.jar")
        createDummyJar(jarPath)

        val provider = mockk<BundledJarProvider>()
        every { provider.extractJar(any()) } returns jarPath

        return DefaultReplManager(provider)
    }

    @Test
    fun `getOrCreateProcess creates new process when none exists`() {
        val manager = createManager()
        // Use a command that exits immediately or just 'java -version' to avoid issues with dummy jar
        // Actually, DefaultReplManager uses 'java -jar', so we need a jar that at least doesn't crash immediately or we just check if process was started.
        // We can use a script if we want, but 'java -jar' is hardcoded.
        // Let's just check if it attempts to start.

        val process = manager.getOrCreateProcess("session1", config1, "java")

        try {
            assertNotNull(process)
        } finally {
            manager.closeAll()
        }
    }

    @Test
    fun `getOrCreateProcess returns same process for same session and config`() {
        val manager = createManager()
        val process1 = manager.getOrCreateProcess("session1", config1, "java")
        val process2 = manager.getOrCreateProcess("session1", config1, "java")

        try {
            assertSame(process1, process2)
        } finally {
            manager.closeAll()
        }
    }

    @Test
    fun `getOrCreateProcess creates new process when config changes`() {
        val manager = createManager()
        val process1 = manager.getOrCreateProcess("session1", config1, "java")
        val process2 = manager.getOrCreateProcess("session1", config2, "java")

        try {
            assertNotSame(process1, process2)
            assertFalse(process1.isAlive)
        } finally {
            manager.closeAll()
        }
    }

    @Test
    fun `terminateSession kills the process`() {
        val manager = createManager()
        val process = manager.getOrCreateProcess("session1", config1, "java")

        manager.terminateSession("session1")
        assertFalse(process.isAlive)
    }

    @Test
    fun `closeAll kills all processes`() {
        val manager = createManager()
        val process1 = manager.getOrCreateProcess("session1", config1, "java")
        val process2 = manager.getOrCreateProcess("session2", config1, "java")

        manager.closeAll()

        assertFalse(process1.isAlive)
        assertFalse(process2.isAlive)
    }
}
