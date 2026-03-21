# Design: Rename "Rendering" Progress to "Collecting"

## Implementation Details

### Init Script Changes

- **`dependencies-report.init.gradle.kts`**:
    - Replace all occurrences of "Rendering" with "Collecting" in progress reporting calls.
    - Update progress tracking variables and comments (`RESOLUTION + COLLECTING`, `Phase 4: Collecting`).
    - Use `realProject.path` to include the project context in the progress detail string.

### Progress Format

The new progress format follows the established convention for internal Gradle progress:
`[gradle-mcp] [PROGRESS] [Collecting]: CURRENT/TOTAL: [:path] configuration`

## Verification Plan

### Manual Verification

- Run `inspect_dependencies` on a multi-project Gradle repository.
- Observe the console output or progress notifications.
- Verify that "Collecting" is used instead of "Rendering".
- Verify that project paths (e.g., `[:app]`, `[:]`) are included in the progress messages.
