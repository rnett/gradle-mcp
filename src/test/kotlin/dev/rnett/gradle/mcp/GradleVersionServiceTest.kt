package dev.rnett.gradle.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GradleVersionServiceTest {

    private fun createMockClient(handler: suspend MockRequestHandleScope.(request: io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData): HttpClient {
        val mockEngine = MockEngine { request -> handler(request) }
        return HttpClient(mockEngine) {
            install(HttpTimeout) // needed for the per-request timeout capability to fire in tests
            install(ContentNegotiation) {
                json(DI.json)
            }
        }
    }

    private fun createMockClient(jsonResponse: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient =
        createMockClient {
            respond(
                content = jsonResponse,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
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
            install(HttpTimeout)
            install(ContentNegotiation) { json(DI.json) }
        }
        val service = DefaultGradleVersionService(client)

        service.resolveVersion("current")
        service.resolveVersion("current")

        assertEquals(1, callCount)
    }

    @Test
    fun `resolveVersion returns concrete version as-is`() = runTest {
        var callCount = 0
        val client = createMockClient {
            callCount++
            respond(
                content = """{"version": "8.7"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DefaultGradleVersionService(client)

        val resolved = service.resolveVersion("8.6")

        assertEquals("8.6", resolved)
        assertEquals(0, callCount)
    }

    @Test
    fun `resolveLatestStable falls back to bundled version on error`() = runTest {
        val client = createMockClient("Error", HttpStatusCode.InternalServerError)
        val service = DefaultGradleVersionService(client)

        val resolution = service.resolveLatestStable()

        assertEquals(BuildConfig.GRADLE_VERSION, resolution.version)
        assertEquals(LatestStableGradleVersion.Source.BUNDLED_FALLBACK, resolution.source)
        assertEquals(BuildConfig.GRADLE_VERSION, service.resolveVersion(null))
    }

    @Test
    fun `fallback is not cached and recovers on next successful fetch`() = runTest {
        var callCount = 0
        val client = createMockClient { _ ->
            callCount++
            if (callCount == 1) {
                respond("Error", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(
                    content = """{"version": "9.7.0"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val service = DefaultGradleVersionService(client)

        val first = service.resolveLatestStable()
        val second = service.resolveLatestStable()
        val third = service.resolveLatestStable()

        assertEquals(BuildConfig.GRADLE_VERSION, first.version)
        assertEquals(LatestStableGradleVersion.Source.BUNDLED_FALLBACK, first.source)
        assertEquals("9.7.0", second.version)
        assertEquals(LatestStableGradleVersion.Source.FETCHED_LIVE, second.source)
        assertEquals(2, callCount)
        assertEquals("9.7.0", third.version)
        assertEquals(2, callCount)
    }

    @Test
    fun `request timeout results in bundled fallback`() = runTest {
        val client = createMockClient {
            delay(1_000)
            respond(
                content = """{"version": "9.7.0"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = DefaultGradleVersionService(client, versionRequestTimeoutMillis = 100)

        val resolution = service.resolveLatestStable()

        assertEquals(BuildConfig.GRADLE_VERSION, resolution.version)
        assertEquals(LatestStableGradleVersion.Source.BUNDLED_FALLBACK, resolution.source)
    }
}
