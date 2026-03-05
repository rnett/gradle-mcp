package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.fixtures.GradleProjectFixture
import dev.rnett.gradle.mcp.gradle.fixtures.testGradleProject
import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import dev.rnett.gradle.mcp.mcp.fixtures.McpServerFixture
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.Root
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.core.scope.Scope
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class GradleProgressIntegrationTest : BaseMcpServerTest() {

    private lateinit var _project: GradleProjectFixture

    override fun Scope.createProvider(): GradleProvider {
        return DefaultGradleProvider(
            config = get(),
            initScriptProvider = DefaultInitScriptProvider(tempDir.resolve("init-scripts").createDirectories()),
            buildManager = get()
        )
    }

    override fun createFixture(): McpServerFixture {
        return McpServerFixture(
            clientCapabilities = ClientCapabilities(
                roots = ClientCapabilities.Roots(listChanged = true),
                elicitation = buildJsonObject { }
            ),
            koinModules = listOf(createTestModule())
        )
    }

    @BeforeTest
    override fun setup() = runTest {
        _project = testGradleProject()
        super.setup()
        server.setServerRoots(Root(_project.path().toUri().toString(), "root"))
    }

    @AfterTest
    override fun cleanup() = runTest {
        _project.close()
        super.cleanup()
    }

    @Test
    fun `gradle build emits progress percentages and phase prefixes`() = runTest(timeout = 120.seconds) {
        val progressNotifications = ConcurrentLinkedQueue<ProgressNotification.Params>()

        server.client.setNotificationHandler<ProgressNotification>(Method.Defined.NotificationsProgress) { notification: ProgressNotification ->
            progressNotifications.add(notification.params)
            CompletableDeferred(Unit)
        }

        // Run build
        server.client.request<CallToolResult>(
            CallToolRequest(
                name = ToolNames.GRADLE,
                arguments = buildJsonObject {
                    put("commandLine", JsonArray(listOf(JsonPrimitive("help"))))
                    put("projectRoot", _project.path().absolutePathString())
                },
                _meta = buildJsonObject {
                    put("progressToken", "test-token")
                }
            )
        )

        val notifications = progressNotifications.toList()

        // Verify we got some progress notifications
        assertTrue(notifications.isNotEmpty(), "Should have received progress notifications")

        // Verify phase prefixes
        val hasConfiguring = notifications.any { it.message?.contains("[CONFIGURING]") == true }

        assertTrue(hasConfiguring, "Should have seen [CONFIGURING] phase")

        // Verify percentage values (0.0 to 1.0)
        notifications.forEach { params ->
            if (params.total != null && params.total!! > 0) {
                assertTrue(params.progress >= 0.0 && params.progress <= params.total!!, "Progress ${params.progress} should be between 0 and ${params.total} (message: ${params.message})")
            }
        }
    }
}
