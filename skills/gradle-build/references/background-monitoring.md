# Background Monitoring Patterns

This guide provides advanced patterns for monitoring and managing long-running background builds using `gradlew` and `inspect_build`.

## Common Monitoring Patterns

### 1. Waiting for a Log Message

The most common pattern for background builds (like dev servers) is to wait for a specific log message that indicates the build is ready.

```json
{
  "buildId": "BUILD_ID",
  "wait": 60,
  "waitFor": "Started Application"
}
```

- **`wait`**: Max seconds to wait for the message.
- **`waitFor`**: A regex pattern to match in the build logs.

### 2. Waiting for Task Completion

If you want to wait for a specific task to finish in a background build.

```json
{
  "buildId": "BUILD_ID",
  "wait": 120,
  "waitForTask": ":app:assemble"
}
```

- **`waitForTask`**: The path of the task to wait for.

### 3. Monitoring Progress

To check the current status of a background build without waiting.

```json
{
  "buildId": "BUILD_ID",
  "include": ["summary"]
}
```

- This returns the current state (e.g., `RUNNING`, `SUCCESS`, `FAILED`), start time, and duration.

### 4. Inspecting Active Builds

To see all currently running background builds.

```json
{} // Call inspect_build with no arguments
```

- This returns the dashboard, which lists all active builds and their `buildId`s.

## Advanced Management

### 1. Stopping a Background Build

Always stop background builds when they are no longer needed to free up resources.

```json
{
  "stopBuildId": "BUILD_ID"
}
```

### 2. Continuous Builds

For continuous builds (e.g., `gradle build --continuous`), use the background pattern and wait for the "Waiting for changes" message.

```json
// Start the build
{
  "commandLine": ["build", "--continuous"],
  "background": true
}

// Wait for the first build to finish
{
  "buildId": "BUILD_ID",
  "wait": 120,
  "waitFor": "Waiting for changes"
}
```

### 3. Handling Timeouts

If a build takes longer than the `wait` time, `inspect_build` will return the current status. You can then call it again with a new `wait` time if needed.

## Troubleshooting Background Builds

- **Build Fails Immediately**: If a background build fails quickly, check the `failures` and `console` output using `inspect_build`.
- **Log Message Not Found**: Ensure the `waitFor` regex is correct and that the message is actually being printed to the console.
- **Resource Exhaustion**: If you have too many background builds running, stop the ones you don't need using `stopBuildId`.
