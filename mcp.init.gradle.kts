import org.gradle.kotlin.dsl.support.serviceOf

initscript {
    repositories {
        mavenCentral()
        mavenLocal()
    }
    dependencies {
        val mcpVersion = startParameter.projectProperties.getOrElse("gradle.mcp.version") { "+" }
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