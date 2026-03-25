---
name: gradle_mcp_progress_reporting_expert
description: >-
  Best practices for high-UX progress reporting across parallel operations and long-running Gradle tasks within the Gradle MCP project.
metadata:
  author: rnett
  version: "1.0"
---

# Skill: Gradle MCP Progress Reporting Expert

This skill provides expert guidance on implementing and managing progress reporting within the Gradle MCP project, including patterns for background builds and long-running operations.

## Core Reporting Guidelines

- **Always Report Progress**: ALWAYS send progress commands/notifications (`ProgressReporter`) when implementing long-running operations or tool handlers.
- **Initial Feedback**: Always report an initial "Starting..." or "Preparing..." progress status (0.0) before invoking a long-running operation to prevent the user from perceiving the tool as "stuck".
- **Reporting Frequency**: Do NOT artificially limit reports (e.g., `if count % 100`) in trackers or producers; throttled delivery is handled authoritatively at the top level.
- **Accuracy Policy**: Progress reporting does not have to be perfect—some slight or temporary inaccuracies are acceptable. Focus on overall user experience.

## Advanced Patterns

- **Parallel Strategy**: When designing progress for parallel operations, prioritize stable activity messaging and independent phase ranges over jittery percentages.
- **Merging Progress**: Base merging progress across multiple search providers on the total document count across all providers rather than the number of providers themselves.
- **Internal Gradle Progress**: For long-running Gradle tasks in init scripts, explicitly report progress during slow internal operations (e.g., dependency resolution) using the format:
  `[gradle-mcp] [PROGRESS] [CATEGORY]: CURRENT/TOTAL: MESSAGE`.
- **Filtering Differentiation**: When filtering dependencies in init scripts, distinguish between "skipped by filter" and "up-to-date" to prevent false reporting.
- **Job Management**: When managing background collection jobs, always use `job.cancelAndJoin()` to ensure clean termination.

## Thread Safety & Consistency

- **Eventual Consistency**: Progress trackers handle updates from multiple Gradle listener threads. Eventual consistency is acceptable; avoid heavy synchronization.
- **State Consolidation**: Prefer consolidating sub-task progress into a single state object within the tracker.
