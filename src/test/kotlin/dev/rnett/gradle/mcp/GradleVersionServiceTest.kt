package dev.rnett.gradle.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GradleVersionServiceTest {

    private fun createMockClient(jsonResponse: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient {
        val mockEngine = MockEngine { request ->
            respond(
                content = jsonResponse,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(DI.json)
            }
        }
    }

    @Test
    fun `resolveVersion resolves current to fetched version`() = runTest {
        val client = createMockClient("""{"version": "8.7"}""")
        val service = DefaultGradleVersionService(client)

        val resolved = service.resolveVersion("current")
        assertEquals("8.7", resolved)
    }

    @Test
    fun `resolveVersion uses cache for subsequent calls`() = runTest {
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            respond(
                content = """{"version": "8.7"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(DI.json) }
        }
        val service = DefaultGradleVersionService(client)

        service.resolveVersion("current")
        service.resolveVersion("current")

        assertEquals(1, callCount)
    }

    @Test
    fun `resolveVersion returns concrete version as-is`() = runTest {
        val client = createMockClient("""{"version": "8.7"}""")
        val service = DefaultGradleVersionService(client)

        val resolved = service.resolveVersion("8.6")
        assertEquals("8.6", resolved)
    }

    @Test
    fun `resolveVersion falls back to BuildConfig version on network error`() = runTest {
        val client = createMockClient("Error", HttpStatusCode.InternalServerError)
        val service = DefaultGradleVersionService(client)

        val resolved = service.resolveVersion("current")
        assertEquals(BuildConfig.GRADLE_VERSION, resolved)
    }
}
