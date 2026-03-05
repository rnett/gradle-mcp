## 1. Create Init Script

- [x] 1.1 Create `src/main/resources/init-scripts/scans.init.gradle.kts`.
- [x] 1.2 Implement logic in the init script to handle both new (`com.gradle.develocity`) and older (`com.gradle.enterprise`) plugins.
- [x] 1.3 Add logic to gracefully do nothing if the build already has a DV/GE plugin configured.
- [x] 1.4 Configure the plugin extensions (`develocity` or `gradleEnterprise`) to accept the Terms of Service.
- [x] 1.5 Register a `buildScanPublished` hook in the init script to output the scan URL with a predictable marker (e.g., `[MCP-BUILD-SCAN] <url>`).

## 2. Update Execution Logic

- [x] 2.1 Update the `gradle` execution logic (in `dev.rnett.gradle.mcp.gradle.GradleProvider` or related setup) to locate and append the `scans.init.gradle.kts` init script to the Gradle arguments when `publishScan == true` or when the
  arguments include `--scan`.
- [x] 2.2 Remove `GradleScanTosAcceptRequest`, `onScansTosRequest` interaction, and the `tosAccepter` prompt logic from `GradleProvider.kt`.
- [x] 2.3 Clean up any related interactive prompt dependencies or interceptors used specifically for TOS acceptance.

## 3. Update Scan Capture

- [x] 3.1 Update `dev.rnett.gradle.mcp.gradle.build.GradleBuildOutInterpreter` to remove the regex parsing for the Develocity TOS interactive prompts.
- [x] 3.2 Update `GradleBuildOutInterpreter` (or related build output parser) to detect the new predictable marker (`[MCP-BUILD-SCAN] <url>`) and capture the scan URL.
- [x] 3.3 Ensure the captured scan URL is added to `runningBuild.publishedScansInternal` and verify it's correctly reported by the build inspection endpoints.

## 4. Testing & Verification

- [x] 4.1 Run the project tests and fix any test failures caused by removing `tosAccepter` or modifying `GradleBuildOutInterpreter`.
- [x] 4.2 Add or update tests to verify the init script is injected when `publishScan == true`.
- [x] 4.3 Manually or programmatically verify that a Gradle build invoked with `publishScan == true` successfully publishes a scan and captures the URL without hanging on the TOS prompt.
