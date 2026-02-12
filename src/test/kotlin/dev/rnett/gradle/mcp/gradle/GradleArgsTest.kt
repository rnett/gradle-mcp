package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.utils.EnvProvider
import kotlinx.serialization.json.Json
import kotlin.test.Test

class GradleInvocationArgumentsTest {

    @Test
    fun `default arguments are empty`() {
        val args = GradleInvocationArguments.DEFAULT
        assert(args.additionalEnvVars.isEmpty())
        assert(args.additionalSystemProps.isEmpty())
        assert(args.additionalJvmArgs.isEmpty())
        assert(args.additionalArguments.isEmpty())
        assert(!args.publishScan)
    }

    @Test
    fun `can create arguments with environment variables`() {
        val args = GradleInvocationArguments(
            additionalEnvVars = mapOf("KEY1" to "value1", "KEY2" to "value2")
        )
        assert(args.additionalEnvVars.size == 2)
        assert(args.additionalEnvVars["KEY1"] == "value1")
        assert(args.additionalEnvVars["KEY2"] == "value2")
    }

    @Test
    fun `can create arguments with system properties`() {
        val args = GradleInvocationArguments(
            additionalSystemProps = mapOf("prop1" to "value1", "prop2" to "value2")
        )
        assert(args.additionalSystemProps.size == 2)
        assert(args.additionalSystemProps["prop1"] == "value1")
        assert(args.additionalSystemProps["prop2"] == "value2")
    }

    @Test
    fun `can create arguments with JVM args`() {
        val args = GradleInvocationArguments(
            additionalJvmArgs = listOf("-Xmx512m", "-Xms256m")
        )
        assert(args.additionalJvmArgs.size == 2)
        assert(args.additionalJvmArgs.contains("-Xmx512m"))
        assert(args.additionalJvmArgs.contains("-Xms256m"))
    }

    @Test
    fun `can create arguments with additional arguments`() {
        val args = GradleInvocationArguments(
            additionalArguments = listOf("--info", "--stacktrace")
        )
        assert(args.additionalArguments.size == 2)
        assert(args.additionalArguments.contains("--info"))
        assert(args.additionalArguments.contains("--stacktrace"))
    }

    @Test
    fun `publishScan flag can be set`() {
        val args = GradleInvocationArguments(publishScan = true)
        assert(args.publishScan)
    }

    @Test
    fun `allAdditionalArguments includes scan when publishScan is true`() {
        val args = GradleInvocationArguments(
            additionalArguments = listOf("--info"),
            publishScan = true
        )
        assert(args.allAdditionalArguments.contains("--scan"))
        assert(args.allAdditionalArguments.contains("--info"))
        assert(args.allAdditionalArguments.size == 2)
    }

    @Test
    fun `allAdditionalArguments does not duplicate scan flag`() {
        val args = GradleInvocationArguments(
            additionalArguments = listOf("--scan", "--info"),
            publishScan = true
        )
        assert(args.allAdditionalArguments.count { it == "--scan" } == 1)
    }

    @Test
    fun `allAdditionalArguments does not include scan when publishScan is false`() {
        val args = GradleInvocationArguments(
            additionalArguments = listOf("--info"),
            publishScan = false
        )
        assert(!args.allAdditionalArguments.contains("--scan"))
        assert(args.allAdditionalArguments.contains("--info"))
    }

    @Test
    fun `can combine two argument sets with plus operator`() {
        val args1 = GradleInvocationArguments(
            additionalEnvVars = mapOf("KEY1" to "value1"),
            additionalSystemProps = mapOf("prop1" to "value1"),
            additionalJvmArgs = listOf("-Xmx512m"),
            additionalArguments = listOf("--info"),
            publishScan = false,
            envSource = EnvSource.NONE
        )
        val args2 = GradleInvocationArguments(
            additionalEnvVars = mapOf("KEY2" to "value2"),
            additionalSystemProps = mapOf("prop2" to "value2"),
            additionalJvmArgs = listOf("-Xms256m"),
            additionalArguments = listOf("--stacktrace"),
            publishScan = true,
            envSource = EnvSource.SHELL
        )

        val combined = args1 + args2

        assert(combined.additionalEnvVars.size == 2)
        assert(combined.additionalEnvVars["KEY1"] == "value1")
        assert(combined.additionalEnvVars["KEY2"] == "value2")

        assert(combined.additionalSystemProps.size == 2)
        assert(combined.additionalSystemProps["prop1"] == "value1")
        assert(combined.additionalSystemProps["prop2"] == "value2")

        assert(combined.additionalJvmArgs.size == 2)
        assert(combined.additionalJvmArgs.contains("-Xmx512m"))
        assert(combined.additionalJvmArgs.contains("-Xms256m"))

        assert(combined.additionalArguments.size == 2)
        assert(combined.additionalArguments.contains("--info"))
        assert(combined.additionalArguments.contains("--stacktrace"))

        assert(combined.publishScan)
        assert(combined.envSource == EnvSource.SHELL)
    }

    @Test
    fun `plus operator preserves non-default envSource`() {
        val args1 = GradleInvocationArguments(envSource = EnvSource.SHELL)
        val args2 = GradleInvocationArguments(envSource = EnvSource.INHERIT)
        assert((args1 + args2).envSource == EnvSource.SHELL)

        val args3 = GradleInvocationArguments(envSource = EnvSource.INHERIT)
        val args4 = GradleInvocationArguments(envSource = EnvSource.NONE)
        assert((args3 + args4).envSource == EnvSource.NONE)
    }

    @Test
    fun `actualEnvVars uses correct source`() = kotlinx.coroutines.test.runTest {
        val inheritedEnv = mapOf("SOURCE_INHERITED" to "INHERITED")
        val shellEnv = mapOf("SOURCE_SHELL" to "SHELL")

        val testProvider = object : EnvProvider {
            override fun getInheritedEnvironment() = inheritedEnv
            override fun getShellEnvironment() = shellEnv
        }

        val inheritArgs = GradleInvocationArguments(envSource = EnvSource.INHERIT)
        val actualInherit = inheritArgs.actualEnvVars(testProvider)
        assert(inheritedEnv.all { (k, v) -> actualInherit[k] == v })

        val shellArgs = GradleInvocationArguments(envSource = EnvSource.SHELL)
        val actualShell = shellArgs.actualEnvVars(testProvider)
        assert(shellEnv.all { (k, v) -> actualShell[k] == v })

        val noneArgs = GradleInvocationArguments(envSource = EnvSource.NONE)
        assert(noneArgs.actualEnvVars(testProvider) == emptyMap<String, String>())

        val additionalArgs = GradleInvocationArguments(
            envSource = EnvSource.NONE,
            additionalEnvVars = mapOf("EXTRA" to "VAR")
        )
        assert(additionalArgs.actualEnvVars(testProvider) == mapOf("EXTRA" to "VAR"))
    }

    @Test
    fun `plus operator uses OR logic for publishScan`() {
        val args1 = GradleInvocationArguments(publishScan = false)
        val args2 = GradleInvocationArguments(publishScan = false)
        assert(!(args1 + args2).publishScan)

        val args3 = GradleInvocationArguments(publishScan = true)
        val args4 = GradleInvocationArguments(publishScan = false)
        assert((args3 + args4).publishScan)
        assert((args4 + args3).publishScan)

        val args5 = GradleInvocationArguments(publishScan = true)
        val args6 = GradleInvocationArguments(publishScan = true)
        assert((args5 + args6).publishScan)
    }

    @Test
    fun `plus operator overwrites env vars with same key`() {
        val args1 = GradleInvocationArguments(additionalEnvVars = mapOf("KEY" to "value1"))
        val args2 = GradleInvocationArguments(additionalEnvVars = mapOf("KEY" to "value2"))
        val combined = args1 + args2
        assert(combined.additionalEnvVars["KEY"] == "value2")
    }

    @Test
    fun `plus operator overwrites system props with same key`() {
        val args1 = GradleInvocationArguments(additionalSystemProps = mapOf("prop" to "value1"))
        val args2 = GradleInvocationArguments(additionalSystemProps = mapOf("prop" to "value2"))
        val combined = args1 + args2
        assert(combined.additionalSystemProps["prop"] == "value2")
    }

    @Test
    fun `EnvSource serialization is case insensitive`() {
        val json = Json { decodeEnumsCaseInsensitive = true }
        assert(json.decodeFromString<EnvSource>("\"shell\"") == EnvSource.SHELL)
        assert(json.decodeFromString<EnvSource>("\"SHELL\"") == EnvSource.SHELL)
        assert(json.decodeFromString<EnvSource>("\"ShElL\"") == EnvSource.SHELL)
        assert(json.decodeFromString<EnvSource>("\"inherit\"") == EnvSource.INHERIT)
        assert(json.decodeFromString<EnvSource>("\"none\"") == EnvSource.NONE)
    }

    @Test
    fun `requestedInitScripts is not serialized`() {
        val args = GradleInvocationArguments(
            requestedInitScripts = listOf("test-script")
        )
        val json = Json.encodeToString(GradleInvocationArguments.serializer(), args)
        assert(!json.contains("requestedInitScripts"))
        assert(!json.contains("test-script"))

        val decoded = Json.decodeFromString(GradleInvocationArguments.serializer(), json)
        assert(decoded.requestedInitScripts.isEmpty())
    }

    @Test
    fun `plus operator combines requestedInitScripts`() {
        val args1 = GradleInvocationArguments(requestedInitScripts = listOf("script1"))
        val args2 = GradleInvocationArguments(requestedInitScripts = listOf("script2"))
        val combined = args1 + args2
        assert(combined.requestedInitScripts == listOf("script1", "script2"))
    }
}

class GradleProjectRootTest {

    @Test
    fun `can create project root from string`() {
        val root = GradleProjectRoot("/path/to/project")
        assert(root.projectRoot == "/path/to/project")
    }

    @Test
    fun `project root preserves path exactly as given`() {
        val path = "C:\\Users\\test\\project"
        val root = GradleProjectRoot(path)
        assert(root.projectRoot == path)
    }
}

class GradleProjectPathTest {

    @Test
    fun `default project path is root`() {
        val path = GradleProjectPath.DEFAULT
        assert(path.path == ":")
        assert(path.isRootProject)
    }

    @Test
    fun `can create root project path`() {
        val path = GradleProjectPath(":")
        assert(path.path == ":")
        assert(path.isRootProject)
    }

    @Test
    fun `empty string is treated as root project`() {
        val path = GradleProjectPath("")
        assert(path.path == ":")
        assert(path.isRootProject)
    }

    @Test
    fun `blank string is treated as root project`() {
        val path = GradleProjectPath("   ")
        assert(path.path == ":   ")
        assert(path.isRootProject)
    }

    @Test
    fun `can create subproject path`() {
        val path = GradleProjectPath("subproject")
        assert(path.path == ":subproject")
        assert(!path.isRootProject)
    }

    @Test
    fun `can create nested subproject path`() {
        val path = GradleProjectPath("parent:child")
        assert(path.path == ":parent:child")
        assert(!path.isRootProject)
    }

    @Test
    fun `leading colon is normalized`() {
        val path = GradleProjectPath(":subproject")
        assert(path.path == ":subproject")
        assert(!path.isRootProject)
    }

    @Test
    fun `multiple leading colons are normalized`() {
        val path = GradleProjectPath(":::subproject")
        assert(path.path == ":subproject")
        assert(!path.isRootProject)
    }

    @Test
    fun `trailing colons are removed`() {
        val path = GradleProjectPath("subproject:")
        assert(path.path == ":subproject")
        assert(!path.isRootProject)
    }

    @Test
    fun `taskPath creates proper task path for root project`() {
        val path = GradleProjectPath(":")
        assert(path.taskPath("help") == ":help")
        assert(path.taskPath("build") == ":build")
    }

    @Test
    fun `taskPath creates proper task path for subproject`() {
        val path = GradleProjectPath("subproject")
        assert(path.taskPath("help") == ":subproject:help")
        assert(path.taskPath("build") == ":subproject:build")
    }

    @Test
    fun `taskPath handles task with leading colon`() {
        val path = GradleProjectPath("subproject")
        assert(path.taskPath(":help") == ":subproject:help")
    }

    @Test
    fun `taskPath handles nested subproject`() {
        val path = GradleProjectPath("parent:child")
        assert(path.taskPath("test") == ":parent:child:test")
    }

    @Test
    fun `toString returns path`() {
        val path = GradleProjectPath("subproject")
        assert(path.toString() == ":subproject")
    }
}
