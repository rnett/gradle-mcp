package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.nio.file.Path
import kotlin.test.assertNotNull

class TreeSitterConcurrencyTest : KoinTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: Path

    private lateinit var languageProvider: TreeSitterLanguageProvider

    private lateinit var koinApp: KoinApplication
    override fun getKoin(): Koin = koinApp.koin

    @BeforeEach
    fun setup() {
        val env = GradleMcpEnvironment(tempDir.resolve("mcp"))
        koinApp = koinApplication {
            modules(module {
                single { env }
                single { HttpClient(CIO) }
                single { ParserDownloader(get()) }
                single { TreeSitterLanguageProvider(get()) }
                single { TreeSitterDeclarationExtractor(get()) }
            })
        }
        languageProvider = getKoin().get()
    }

    @AfterEach
    fun tearDown() {
        getKoin().getOrNull<HttpClient>()?.close()
        koinApp.close()
    }

    @Test
    fun `test concurrent language requests`() = runBlocking {
        val languages = listOf("java", "kotlin", "java", "kotlin", "java", "kotlin")
        val deferred = languages.map { name ->
            async(Dispatchers.Default) {
                languageProvider.getLanguage(name)
            }
        }
        val results = deferred.awaitAll()
        results.forEach { assertNotNull(it) }
    }

    @Test
    fun `test concurrent extractSymbols initialization`() = runBlocking {
        val extractor = getKoin().get<TreeSitterDeclarationExtractor>()
        val deferred = List(10) {
            async(Dispatchers.Default) {
                extractor.extractSymbols("class X", "kt")
            }
        }
        val results = deferred.awaitAll()
        results.forEach { assertNotNull(it) }
    }
}
