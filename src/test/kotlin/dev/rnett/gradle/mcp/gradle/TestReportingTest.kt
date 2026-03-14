package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.gradle.fixtures.testJavaProject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestReportingTest {

    private fun createTestProvider(): DefaultGradleProvider {
        return DefaultGradleProvider(
            GradleConfiguration(
                maxConnections = 5,
                ttl = 60.seconds,
                allowPublicScansPublishing = false
            ),
            buildManager = BuildManager()
        )
    }

    @Test
    fun `verifies comprehensive test reporting including skips and cancellations`() = runTest(timeout = 180.seconds) {
        val provider = createTestProvider()
        testJavaProject {
            file(
                "src/test/java/com/example/ReportingTest.java", """
                package com.example;
                
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.Assumptions;
                import org.junit.jupiter.api.Disabled;
                import static org.junit.jupiter.api.Assertions.*;
                
                class ReportingTest {
                    @Test
                    void testPass() {
                        assertTrue(true);
                    }
                    
                    @Test
                    void testFail() {
                        fail("Expected failure");
                    }
                    
                    @Test
                    void testAssumption() {
                        Assumptions.assumeTrue(false, "Skipped by assumption");
                    }
                    
                    @Disabled("Disabled by annotation")
                    @Test
                    void testDisabled() {
                    }
                    
                    @Test
                    void testHang() throws InterruptedException {
                        System.out.println("Starting hang test...");
                        Thread.sleep(60000);
                    }
                }
            """.trimIndent()
            )
        }.use { project ->
            val projectRoot = GradleProjectRoot(project.pathString())

            // 1. Run build normally and check PASS, FAIL, SKIP (Assumption), SKIP (Disabled)
            // Note: We'll skip the hang test for now using a pattern or just letting it run in a separate build.
            // Actually, let's run them all and cancel after the first 4 are likely done, 
            // but that's flaky. Better to run them separately.

            val runningBuild = provider.runTests(
                projectRoot = projectRoot,
                testPatterns = mapOf(
                    ":test" to setOf(
                        "com.example.ReportingTest.testPass",
                        "com.example.ReportingTest.testFail",
                        "com.example.ReportingTest.testAssumption",
                        "com.example.ReportingTest.testDisabled"
                    )
                ),
                args = GradleInvocationArguments.DEFAULT
            )
            val result = runningBuild.awaitFinished()

            val testResults = result.testResults
            assertTrue(testResults.passed.any { it.testName.contains("testPass") }, "testPass should pass")
            assertTrue(testResults.failed.any { it.testName.contains("testFail") }, "testFail should fail")

            // Both assumption failures and @Disabled show up as SKIPPED in Tooling API
            val skippedNames = testResults.skipped.map { it.testName }
            assertTrue(skippedNames.any { it.contains("testAssumption") }, "testAssumption should be skipped. Found: $skippedNames")
            assertTrue(skippedNames.any { it.contains("testDisabled") }, "testDisabled should be skipped. Found: $skippedNames")

            // 2. Run and cancel to check CANCELLED
            val cancellingBuild = provider.runTests(
                projectRoot = projectRoot,
                testPatterns = mapOf(":test" to setOf("com.example.ReportingTest.testHang")),
                args = GradleInvocationArguments.DEFAULT
            )

            // Wait for test to start
            var started = false
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < 20000) {
                if (cancellingBuild.testResultsInternal.results(System.currentTimeMillis()).inProgress.any { it.testName.contains("testHang") }) {
                    started = true
                    break
                }
                kotlinx.coroutines.delay(100)
            }
            assertTrue(started, "testHang did not start in time")

            cancellingBuild.stop()
            val cancelResult = cancellingBuild.awaitFinished()

            assertTrue(cancelResult.testResults.cancelled.any { it.testName.contains("testHang") }, "testHang should be cancelled")
        }
    }

    @Test
    fun `verifies real-time test progress reporting`() = runTest(timeout = 180.seconds) {
        val provider = createTestProvider()
        testJavaProject {
            file(
                "src/test/java/com/example/ReportingTest.java", """
                package com.example;
                
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;
                
                class ReportingTest {
                    @Test
                    void testPass() {
                        assertTrue(true);
                    }
                    
                    @Test
                    void testFail() {
                        fail("Expected failure");
                    }
                }
            """.trimIndent()
            )
        }.use { project ->
            val projectRoot = GradleProjectRoot(project.pathString())
            val progressMessages = java.util.concurrent.ConcurrentLinkedQueue<String>()

            val runningBuild = provider.runTests(
                projectRoot = projectRoot,
                testPatterns = mapOf(
                    ":test" to setOf(
                        "com.example.ReportingTest.testPass",
                        "com.example.ReportingTest.testFail"
                    )
                ),
                args = GradleInvocationArguments.DEFAULT,
                progress = ProgressReporter { _, _, msg ->
                    if (msg != null) progressMessages.add(msg)
                }
            )
            runningBuild.awaitFinished()

            val messages = progressMessages.toList()
            val hasPass = messages.any { it.contains(Regex("pass: 1")) }
            val hasFail = messages.any { it.contains(Regex("fail: 1")) }
            val hasCombined = messages.any { it.contains(Regex("pass: 1.*fail: 1")) }

            assertTrue(hasPass || hasFail || hasCombined, "Should have seen test progress in messages: $messages")
        }
    }
}
