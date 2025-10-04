package dev.rnett.gradle.mcp.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleInvocationArgumentsTest {

    @Test
    fun `default arguments are empty`() {
        val args = GradleInvocationArguments.DEFAULT
        assertTrue(args.additionalEnvVars.isEmpty())
        assertTrue(args.additionalSystemProps.isEmpty())
        assertTrue(args.additionalJvmArgs.isEmpty())
        assertTrue(args.additionalArguments.isEmpty())
        assertFalse(args.publishScan)
    }

    @Test
    fun `can create arguments with environment variables`() {
        val args = GradleInvocationArguments(
            additionalEnvVars = mapOf("KEY1" to "value1", "KEY2" to "value2")
        )
        assertEquals(2, args.additionalEnvVars.size)
        assertEquals("value1", args.additionalEnvVars["KEY1"])
        assertEquals("value2", args.additionalEnvVars["KEY2"])
    }

    @Test
    fun `can create arguments with system properties`() {
        val args = GradleInvocationArguments(
            additionalSystemProps = mapOf("prop1" to "value1", "prop2" to "value2")
        )
        assertEquals(2, args.additionalSystemProps.size)
        assertEquals("value1", args.additionalSystemProps["prop1"])
        assertEquals("value2", args.additionalSystemProps["prop2"])
    }

    @Test
    fun `can create arguments with JVM args`() {
        val args = GradleInvocationArguments(
            additionalJvmArgs = listOf("-Xmx512m", "-Xms256m")
        )
        assertEquals(2, args.additionalJvmArgs.size)
        assertTrue(args.additionalJvmArgs.contains("-Xmx512m"))
        assertTrue(args.additionalJvmArgs.contains("-Xms256m"))
    }

    @Test
    fun `can create arguments with additional arguments`() {
        val args = GradleInvocationArguments(
            additionalArguments = listOf("--info", "--stacktrace")
        )
        assertEquals(2, args.additionalArguments.size)
        assertTrue(args.additionalArguments.contains("--info"))
        assertTrue(args.additionalArguments.contains("--stacktrace"))
    }

    @Test
    fun `publishScan flag can be set`() {
        val args = GradleInvocationArguments(publishScan = true)
        assertTrue(args.publishScan)
    }

    @Test
    fun `allAdditionalArguments includes scan when publishScan is true`() {
        val args = GradleInvocationArguments(
            additionalArguments = listOf("--info"),
            publishScan = true
        )
        assertTrue(args.allAdditionalArguments.contains("--scan"))
        assertTrue(args.allAdditionalArguments.contains("--info"))
        assertEquals(2, args.allAdditionalArguments.size)
    }

    @Test
    fun `allAdditionalArguments does not duplicate scan flag`() {
        val args = GradleInvocationArguments(
            additionalArguments = listOf("--scan", "--info"),
            publishScan = true
        )
        assertEquals(1, args.allAdditionalArguments.count { it == "--scan" })
    }

    @Test
    fun `allAdditionalArguments does not include scan when publishScan is false`() {
        val args = GradleInvocationArguments(
            additionalArguments = listOf("--info"),
            publishScan = false
        )
        assertFalse(args.allAdditionalArguments.contains("--scan"))
        assertTrue(args.allAdditionalArguments.contains("--info"))
    }

    @Test
    fun `can combine two argument sets with plus operator`() {
        val args1 = GradleInvocationArguments(
            additionalEnvVars = mapOf("KEY1" to "value1"),
            additionalSystemProps = mapOf("prop1" to "value1"),
            additionalJvmArgs = listOf("-Xmx512m"),
            additionalArguments = listOf("--info"),
            publishScan = false
        )
        val args2 = GradleInvocationArguments(
            additionalEnvVars = mapOf("KEY2" to "value2"),
            additionalSystemProps = mapOf("prop2" to "value2"),
            additionalJvmArgs = listOf("-Xms256m"),
            additionalArguments = listOf("--stacktrace"),
            publishScan = true
        )

        val combined = args1 + args2

        assertEquals(2, combined.additionalEnvVars.size)
        assertEquals("value1", combined.additionalEnvVars["KEY1"])
        assertEquals("value2", combined.additionalEnvVars["KEY2"])

        assertEquals(2, combined.additionalSystemProps.size)
        assertEquals("value1", combined.additionalSystemProps["prop1"])
        assertEquals("value2", combined.additionalSystemProps["prop2"])

        assertEquals(2, combined.additionalJvmArgs.size)
        assertTrue(combined.additionalJvmArgs.contains("-Xmx512m"))
        assertTrue(combined.additionalJvmArgs.contains("-Xms256m"))

        assertEquals(2, combined.additionalArguments.size)
        assertTrue(combined.additionalArguments.contains("--info"))
        assertTrue(combined.additionalArguments.contains("--stacktrace"))

        assertTrue(combined.publishScan)
    }

    @Test
    fun `plus operator uses OR logic for publishScan`() {
        val args1 = GradleInvocationArguments(publishScan = false)
        val args2 = GradleInvocationArguments(publishScan = false)
        assertFalse((args1 + args2).publishScan)

        val args3 = GradleInvocationArguments(publishScan = true)
        val args4 = GradleInvocationArguments(publishScan = false)
        assertTrue((args3 + args4).publishScan)
        assertTrue((args4 + args3).publishScan)

        val args5 = GradleInvocationArguments(publishScan = true)
        val args6 = GradleInvocationArguments(publishScan = true)
        assertTrue((args5 + args6).publishScan)
    }

    @Test
    fun `plus operator overwrites env vars with same key`() {
        val args1 = GradleInvocationArguments(additionalEnvVars = mapOf("KEY" to "value1"))
        val args2 = GradleInvocationArguments(additionalEnvVars = mapOf("KEY" to "value2"))
        val combined = args1 + args2
        assertEquals("value2", combined.additionalEnvVars["KEY"])
    }

    @Test
    fun `plus operator overwrites system props with same key`() {
        val args1 = GradleInvocationArguments(additionalSystemProps = mapOf("prop" to "value1"))
        val args2 = GradleInvocationArguments(additionalSystemProps = mapOf("prop" to "value2"))
        val combined = args1 + args2
        assertEquals("value2", combined.additionalSystemProps["prop"])
    }
}

class GradleProjectRootTest {

    @Test
    fun `can create project root from string`() {
        val root = GradleProjectRoot("/path/to/project")
        assertEquals("/path/to/project", root.projectRoot)
    }

    @Test
    fun `project root preserves path exactly as given`() {
        val path = "C:\\Users\\test\\project"
        val root = GradleProjectRoot(path)
        assertEquals(path, root.projectRoot)
    }
}

class GradleProjectPathTest {

    @Test
    fun `default project path is root`() {
        val path = GradleProjectPath.DEFAULT
        assertEquals(":", path.path)
        assertTrue(path.isRootProject)
    }

    @Test
    fun `can create root project path`() {
        val path = GradleProjectPath(":")
        assertEquals(":", path.path)
        assertTrue(path.isRootProject)
    }

    @Test
    fun `empty string is treated as root project`() {
        val path = GradleProjectPath("")
        assertEquals(":", path.path)
        assertTrue(path.isRootProject)
    }

    @Test
    fun `blank string is treated as root project`() {
        val path = GradleProjectPath("   ")
        assertEquals(":   ", path.path)
        assertTrue(path.isRootProject)
    }

    @Test
    fun `can create subproject path`() {
        val path = GradleProjectPath("subproject")
        assertEquals(":subproject", path.path)
        assertFalse(path.isRootProject)
    }

    @Test
    fun `can create nested subproject path`() {
        val path = GradleProjectPath("parent:child")
        assertEquals(":parent:child", path.path)
        assertFalse(path.isRootProject)
    }

    @Test
    fun `leading colon is normalized`() {
        val path = GradleProjectPath(":subproject")
        assertEquals(":subproject", path.path)
        assertFalse(path.isRootProject)
    }

    @Test
    fun `multiple leading colons are normalized`() {
        val path = GradleProjectPath(":::subproject")
        assertEquals(":subproject", path.path)
        assertFalse(path.isRootProject)
    }

    @Test
    fun `trailing colons are removed`() {
        val path = GradleProjectPath("subproject:")
        assertEquals(":subproject", path.path)
        assertFalse(path.isRootProject)
    }

    @Test
    fun `taskPath creates proper task path for root project`() {
        val path = GradleProjectPath(":")
        assertEquals(":help", path.taskPath("help"))
        assertEquals(":build", path.taskPath("build"))
    }

    @Test
    fun `taskPath creates proper task path for subproject`() {
        val path = GradleProjectPath("subproject")
        assertEquals(":subproject:help", path.taskPath("help"))
        assertEquals(":subproject:build", path.taskPath("build"))
    }

    @Test
    fun `taskPath handles task with leading colon`() {
        val path = GradleProjectPath("subproject")
        assertEquals(":subproject:help", path.taskPath(":help"))
    }

    @Test
    fun `taskPath handles nested subproject`() {
        val path = GradleProjectPath("parent:child")
        assertEquals(":parent:child:test", path.taskPath("test"))
    }

    @Test
    fun `toString returns path`() {
        val path = GradleProjectPath("subproject")
        assertEquals(":subproject", path.toString())
    }
}
