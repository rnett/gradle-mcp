package dev.rnett.gradle.mcp.tools.repl

import java.io.File
import java.util.Base64
import kotlin.test.fail

object ImageAssert {
    private val resourcesDir = File("src/test/resources/test-images").absoluteFile

    fun assertImage(actualBase64: String, resourceName: String) {
        val actualBytes = Base64.getDecoder().decode(actualBase64)
        val resourceFile = File(resourcesDir, resourceName)

        if (!resourceFile.exists()) {
            resourcesDir.mkdirs()
            resourceFile.writeBytes(actualBytes)
            fail("Test image resource '$resourceName' did not exist. It has been created at ${resourceFile.absolutePath}. Please review it and run the test again.")
        }

        val expectedBytes = resourceFile.readBytes()

        // Use ImageIO for a more robust comparison if byte-for-byte fails, 
        // but for now let's try strict byte comparison as it's cleaner if it works.
        if (!actualBytes.contentEquals(expectedBytes)) {
            // If they are not equal, we might want to write the actual to a temp file for debugging
            val actualFile = File.createTempFile("actual-", "-$resourceName")
            actualFile.writeBytes(actualBytes)
            fail("Image content mismatch for '$resourceName'. Actual image saved to ${actualFile.absolutePath}. Expected size: ${expectedBytes.size}, Actual size: ${actualBytes.size}")
        }
    }
}
