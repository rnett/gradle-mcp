// Captures the authoritative configuration-cache report path and re-emits it as a
// [MCP-CC-REPORT] <path> marker that the BuildExecutionService consumes verbatim.
// This is the authoritative counterpart to the server-side console fallback.
if (gradle.parent == null) {
    try {
        val gradleInternal = gradle as org.gradle.api.internal.GradleInternal
        val services = gradleInternal.services
        val loggingManager = services.get(org.gradle.internal.logging.LoggingManagerInternal::class.java)
        val buildOperationsListenerManager = services.get(org.gradle.internal.operations.BuildOperationListenerManager::class.java)

        val inListener = ThreadLocal<Boolean>()

        val reportHint = "See the complete report at "

        val listener = object : org.gradle.internal.logging.events.OutputEventListener {
            override fun onOutput(event: org.gradle.internal.logging.events.OutputEvent) {
                if (inListener.get() == true) return
                inListener.set(true)
                try {
                    if (event is org.gradle.internal.logging.events.RenderableOutputEvent) {
                        val buffer = StringBuilder()
                        val output = object : org.gradle.internal.logging.text.AbstractStyledTextOutput() {
                            override fun doAppend(text: String) {
                                buffer.append(text)
                            }
                        }
                        event.render(output)
                        val text = buffer.toString()
                        val hintIndex = text.indexOf(reportHint)
                        if (hintIndex >= 0) {
                            val path = text.substring(hintIndex + reportHint.length).trim()
                            if (path.isNotEmpty()) {
                                System.out.println("[MCP-CC-REPORT] $path")
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
                if (buildOperation.parentId == null) {
                    buildOperationsListenerManager.removeListener(this)
                    loggingManager.removeOutputEventListener(listener)
                }
            }
        }
        buildOperationsListenerManager.addListener(operationsListener)
    } catch (e: Throwable) {
        // Never fail the build; the server-side console fallback still captures the report path.
        logger.warn("Failed to set up gradle-mcp configuration-cache report capture", e)
    }
}
