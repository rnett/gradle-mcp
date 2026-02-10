package dev.rnett.gradle.mcp.mcp.fixtures

import dev.rnett.gradle.mcp.gradle.BackgroundBuildManager
import dev.rnett.gradle.mcp.gradle.BuildResults
import dev.rnett.gradle.mcp.gradle.GradleProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class BaseMcpServerTest {

    @TempDir
    lateinit var tempDir: Path

    lateinit var server: McpServerFixture
    protected val provider = mockk<GradleProvider>(relaxed = true)

    protected open val backgroundBuildManager: BackgroundBuildManager = BackgroundBuildManager()
    protected open val buildResults: BuildResults = BuildResults(backgroundBuildManager)

    @BeforeTest
    fun setup() = runTest {
        every { provider.backgroundBuildManager } returns backgroundBuildManager
        every { provider.buildResults } returns buildResults
        server = McpServerFixture(provider)
        server.start()
    }

    @AfterTest
    fun cleanup() = runTest {
        server.close()
    }
}