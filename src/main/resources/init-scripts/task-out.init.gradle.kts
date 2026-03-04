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

    val operations = java.util.concurrent.ConcurrentHashMap<Long, Pair<Long?, String?>>()

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
                            val hierarchy = mutableListOf<String>()
                            while (currentId != null) {
                                val op = operations[currentId]
                                val displayName = op?.second
                                if (displayName != null) {
                                    hierarchy.add(displayName)
                                    if (displayName.startsWith("Task :") || displayName.startsWith("Run Task :")) {
                                        taskPath = if (displayName.startsWith("Task :")) {
                                            displayName.substringAfter("Task ")
                                        } else {
                                            displayName.substringAfter("Run Task ")
                                        }
                                        break
                                    }
                                }
                                currentId = op?.first
                            }

                            if (taskPath != null) {
                                val lines = text.split("\n")
                                for (line in lines) {
                                    System.out.println("[gradle-mcp] [${taskPath}] [${event.category}]: ${line.trimEnd('\r')}")
                                }
                            } else {
                                // If we can't find a task path, it might be build-level output.
                                // We don't prefix it, so it will be handled as raw output in the provider.
                                // But let's log the hierarchy to help debug.
                                if (gradle.startParameter.projectProperties.containsKey("gradle-mcp.init-scripts.debug")) {
                                    System.err.println("[gradle-mcp-debug] No task path found for output. Category: ${event.category}, Text: ${text.take(20)}, Hierarchy: ${hierarchy.joinToString(" -> ")}")
                                }
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
            val id = buildOperation.id?.id
            if (id != null) {
                operations[id] = buildOperation.parentId?.id to buildOperation.displayName
            }
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
            val id = buildOperation.id?.id
            if (id != null) {
                operations.remove(id)
            }
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
