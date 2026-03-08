## Why

When a Gradle build is canceled (e.g. by the user or due to timeout), it can be difficult to know which tests were currently running and might have been interrupted. Highlighting in-progress tests at the time of cancellation provides better
visibility into the test suite's state and helps developers understand what was being executed when the build stopped.

## What Changes

- Add a mechanism to track and report tests that are currently running.
- When a build is canceled or fails, display an "in-progress" status for tests that were started but have not yet completed.

## Capabilities

### New Capabilities

- `in-progress-tests`: Tracking and displaying tests that are currently running when a build is canceled or fails.

### Modified Capabilities

## Impact

- Changes to how we process Gradle test events to keep track of started and finished tests.
- Changes to the output or status reporting mechanism to show in-progress tests upon build cancellation.
- Impact on the test event tracking logic in the Gradle MCP server.