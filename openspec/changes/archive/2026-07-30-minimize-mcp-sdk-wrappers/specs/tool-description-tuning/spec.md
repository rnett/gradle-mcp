## MODIFIED Requirements

### Requirement: Clear Auto-Detection Boundaries
Parameter descriptions for auto-detected values MUST clearly state when auto-detection applies and when explicit specification is required. Ambiguous terms like "usually" or "typically" MUST be replaced with specific conditions. `projectRoot` is not auto-detected from MCP roots: its description MUST state that a nonblank explicit path is expanded, resolved, and normalized first, a nonblank `GRADLE_MCP_PROJECT_ROOT` is the sole fallback and is processed the same way, and absence of both causes a clear `IllegalArgumentException`.

#### Scenario: Agent decides whether to specify `projectRoot`
- **WHEN** an agent reads the `projectRoot` parameter description
- **THEN** it clearly understands that a nonblank explicit path takes precedence and is expanded, resolved, and normalized
- **AND** it knows that `GRADLE_MCP_PROJECT_ROOT` is used only when the explicit value is absent
- **AND** it knows that missing both values fails clearly without MCP roots auto-detection.
