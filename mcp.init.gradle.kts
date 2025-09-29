import org.gradle.kotlin.dsl.support.serviceOf

initscript {

    val gradleHomeProperties = startParameter.gradleUserHomeDir.resolve("gradle.properties").inputStream().use { java.util.Properties().apply { load(it) } }
    fun getProperty(property: String): String? {
        return startParameter.projectProperties[property] ?: gradleHomeProperties[property]?.toString() ?: System.getProperty(property)
    }

    repositories {
        mavenCentral() {
            mavenContent { includeModule("dev.rnett.gradle-mcp", "gradle-mcp") }
        }
        if (getProperty("gradle.mcp.repositories.local") != null) {
            mavenLocal() {
                mavenContent { includeModule("dev.rnett.gradle-mcp", "gradle-mcp") }
            }
        }
        if (getProperty("gradle.mcp.repositories.snapshots") != null) {
            maven("https://central.sonatype.com/repository/maven-snapshots") {
                mavenContent { snapshotsOnly() }
                mavenContent { includeModule("dev.rnett.gradle-mcp", "gradle-mcp") }
            }
        }
    }
    dependencies {
        val mcpVersion = getProperty("gradle.mcp.version") ?: "+"
        classpath("dev.rnett.gradle-mcp:gradle-mcp:${mcpVersion}")
    }
}

val mcpClass = Class.forName("dev.rnett.gradle.mcp.Application")

val startRequest = startParameter.taskRequests.firstOrNull { it.projectPath == null && it.rootDir == null && it.args.getOrNull(0) == "gradle-mcp" }
if (startRequest != null) {
    val args = startRequest.args.drop(1)

    val execService = serviceOf<ExecOperations>()

    execService.javaexec {
        environment(System.getenv())
        maxHeapSize = "512m"
        minHeapSize = "256m"
        systemProperties = System.getProperties().mapKeys { it.key.toString() }
        classpath(mcpClass.protectionDomain.codeSource.location.toURI())
        mainClass.set("dev.rnett.gradle.mcp.Application")
        standardInput = System.`in`
        standardOutput = System.out
        errorOutput = System.err
        args(args)
    }.rethrowFailure().assertNormalExitValue()

    throw GradleException("MCP Server closed - this is not actually an error")
}