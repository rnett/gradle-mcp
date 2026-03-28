package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.fixtures.gradle.testJavaProject
import dev.rnett.gradle.mcp.tools.toOutputString
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestReportingTest {

    private fun createTestProvider(): DefaultGradleProvider {
        return DefaultGradleProvider(
            GradleConfiguration(),
            buildManager = BuildManager()
        )
    }

    @Test
    fun `verifies comprehensive test reporting including skips`() = runTest(timeout = 180.seconds) {
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
            val progressMessages = ConcurrentLinkedQueue<String>()

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

    @Test
    fun `verifies test summary grouping in output`() = runTest(timeout = 180.seconds) {
        val provider = createTestProvider()
        testJavaProject {
            file(
                "src/test/java/com/example/SuiteA.java", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;
                public class SuiteA {
                    @Test void testFail1() { fail("fail 1"); }
                    @Test void testFail2() { fail("fail 2"); }
                }
            """.trimIndent()
            )
            file(
                "src/test/java/com/example/SuiteB.java", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;
                public class SuiteB {
                    @Test void testFail3() { fail("fail 3"); }
                }
            """.trimIndent()
            )
        }.use { project ->
            val projectRoot = GradleProjectRoot(project.pathString())
            val runningBuild = provider.runTests(
                projectRoot = projectRoot,
                testPatterns = mapOf(
                    ":test" to setOf(
                        "com.example.SuiteA.testFail1",
                        "com.example.SuiteA.testFail2",
                        "com.example.SuiteB.testFail3"
                    )
                ),
                args = GradleInvocationArguments.DEFAULT
            )
            val result = runningBuild.awaitFinished()
            val output = result.toOutputString()

            assertTrue(output.contains("- com.example.SuiteA"), "Should contain SuiteA group. Output:\n$output")
            assertTrue(output.contains("    - testFail1"), "Should contain testFail1 under SuiteA. Output:\n$output")
            assertTrue(output.contains("    - testFail2"), "Should contain testFail2 under SuiteA. Output:\n$output")
            assertTrue(output.contains("- com.example.SuiteB"), "Should contain SuiteB group. Output:\n$output")
            assertTrue(output.contains("    - testFail3"), "Should contain testFail3 under SuiteB. Output:\n$output")
        }
    }
}
