# Capability: mcp-context-progress

## MODIFIED Requirements

### Requirement: Improved progress reporting API
Each tool call SHALL own a `ProgressNotificationPipeline` with a lazy `ProgressReporter` send surface. Nested services SHALL receive only `ProgressReporter`, and the pipeline SHALL close from the handler adapter's `finally` block on every exit path.

#### Scenario: Progress updates preserve bounded behavior
- **WHEN** a reporter accepts progress updates
- **THEN** progress SHALL use a bounded channel with replay-equivalent capacity 10, extra capacity 50, and `DROP_OLDEST`
- **AND** `transformLatest` SHALL produce four animated emissions 500 ms apart
- **AND** emissions SHALL be sampled every 100 ms unless sampling is disabled

### Requirement: Conditional progress emission
Direct progress sends SHALL require a progress token, while generic notification sends SHALL not require one. The pipeline SHALL enqueue both kinds into one bounded notification channel with replay-equivalent capacity 0, extra capacity 500, and `DROP_OLDEST`.

#### Scenario: Notification routing preserves correlation
- **WHEN** a notification is enqueued with request extra
- **THEN** one normally dispatched collector SHALL deliver it through `RequestHandlerExtra.sendNotification` in enqueue order
- **AND** the captured request correlation SHALL be retained
- **WHEN** no request extra exists
- **THEN** delivery SHALL use only the invoking `ClientConnection`

#### Scenario: Pipeline closes deterministically
- **WHEN** a tool returns, fails, is cancelled, or fails during input decoding
- **THEN** the adapter SHALL close the pipeline in `finally`
- **AND** subsequent reporter, direct-progress, and generic-notification sends SHALL be ignored
- **AND** `close()` SHALL be non-suspending, idempotent, mark closed before cancellation, drop queued items, and permit at most one already-dequeued notification to finish
