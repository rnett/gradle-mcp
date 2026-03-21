# Proposal: Rename "Rendering" Progress to "Collecting"

## Summary

Rename the "Rendering" progress category in the dependency report to "Collecting" to better reflect its multi-purpose nature and avoid confusion with UI rendering.

## Problem

The dependency report init script uses the term "Rendering" for the phase where it formats and outputs dependency information. This is confusing because:

1. It's often used for things other than just rendering (e.g., gathering metadata).
2. It conflicts with the mental model of "UI Rendering" in other parts of the system (like Compose).
3. The progress messages lack project context, making them less helpful in multi-project builds.

## Solution

1. Rename the progress category from "Rendering" to "Collecting".
2. Update internal labels and comments to match the new terminology (e.g., Phase 4).
3. Include the project path in the progress detail message (e.g., `[Collecting] [:app] implementation`).

## Impact

- Clearer progress feedback during dependency report generation.
- Better disambiguation in multi-project Gradle builds.
- Improved alignment with the actual work being performed in that phase.
