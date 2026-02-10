import org.gradle.internal.logging.events.RenderableOutputEvent
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent

if (gradle.startParameter.projectProperties.containsKey("gradle-mcp.init-scripts.hello")) {
    println("Gradle MCP init script task-out.init.gradle.kts loaded")
}

try {
    val gradleInternal = gradle as org.gradle.api.internal.GradleInternal
    val services = gradleInternal.services
    val loggingManager = services.get(org.gradle.internal.logging.LoggingManagerInternal::class.java)
    val buildOperationsListenerManager = services.get(org.gradle.internal.operations.BuildOperationListenerManager::class.java)
    val styledTextOutputFactory = services.get(org.gradle.internal.logging.text.StyledTextOutputFactory::class.java)
    val styledTextOutput = styledTextOutputFactory.create("gradle-mcp")

    val buildOperationExecutor = services.get(org.gradle.internal.operations.BuildOperationProgressEventEmitter::class.java)

    val inListener = ThreadLocal<Boolean>()

    val operations = mutableMapOf<Long?, Pair<Long?, String?>>()

    val listener = object : org.gradle.internal.logging.events.OutputEventListener {
        override fun onOutput(event: org.gradle.internal.logging.events.OutputEvent) {
            if (inListener.get() == true) return
            inListener.set(true)
            try {
                if (event is org.gradle.internal.logging.events.RenderableOutputEvent) {
                    val buffer = StringBuilder()
                    val o = object : org.gradle.internal.logging.text.AbstractStyledTextOutput() {
                        override fun doAppend(text: String) {
                            buffer.append(text)
                        }
                    }
                    event.render(o)
                    val text = buffer.toString().trim()
                    if (text.isNotBlank()) {
                        if (!text.startsWith("[gradle-mcp]")) {
                            var currentId = event.buildOperationId?.id
                            var taskPath: String? = null
                            while (currentId != null) {
                                val op = operations[currentId]
                                val displayName = op?.second
                                if (displayName != null && (displayName.startsWith("Task :") || displayName.startsWith("Run Task :"))) {
                                    taskPath = if (displayName.startsWith("Task :")) {
                                        displayName.substringAfter("Task ")
                                    } else {
                                        displayName.substringAfter("Run Task ")
                                    }
                                    break
                                }
                                currentId = op?.first
                            }

                            if (taskPath != null) {
                                System.out.println("[gradle-mcp] [${taskPath}] [${event.category}]: ${text}")
                            }
                        }
                    }
                }
            } finally {
                inListener.set(false)
            }
        }
    }

    loggingManager.addOutputEventListener(listener)

    val operationsListener = object : org.gradle.internal.operations.BuildOperationListener {
        override fun started(
            buildOperation: org.gradle.internal.operations.BuildOperationDescriptor,
            startEvent: org.gradle.internal.operations.OperationStartEvent
        ) {
            operations[buildOperation.id?.id] = buildOperation.parentId?.id to buildOperation.displayName
        }

        override fun progress(
            operationIdentifier: org.gradle.internal.operations.OperationIdentifier,
            progressEvent: org.gradle.internal.operations.OperationProgressEvent
        ) {

        }

        override fun finished(
            buildOperation: org.gradle.internal.operations.BuildOperationDescriptor,
            finishEvent: org.gradle.internal.operations.OperationFinishEvent
        ) {
            operations.remove(buildOperation.id?.id)
            if (buildOperation.parentId == null) {
                buildOperationsListenerManager.removeListener(this)
                loggingManager.removeOutputEventListener(listener)
            }
        }
    }

    buildOperationsListenerManager.addListener(operationsListener)
} catch (e: Throwable) {
    // detected in provider
    logger.warn("Failed to set up gradle-mcp output capturing", e)
}
