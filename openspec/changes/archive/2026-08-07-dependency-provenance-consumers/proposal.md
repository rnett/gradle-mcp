## Why

AI agents need reliable answers to two dependency questions: why Gradle resolved a dependency to a particular version, and which dependencies directly consume a component. The current dependency report exposes provenance fields and a forward `children` graph, but it has no reverse edge for identifying consumers.

## What Changes

- Extend `inspect_dependencies` with an `includeConsumers` parameter that defaults to `false`.
- Make `includeConsumers=true` imply `onlyDirect=false` for that invocation so inversion uses the full resolved graph. If the caller explicitly also passes `onlyDirect=true`, `includeConsumers` wins and the response includes `"onlyDirect overridden to false for consumers inversion"`.
- When requested, compute direct reverse dependency edges in one inversion pass and expose them as lightweight consumer records, including nullable `variant`, instead of nested dependency objects.
- Deduplicate reverse edges by component identity, source configuration, and variant, using a synthetic parent identity fallback that folds GAV, variant, and id, while preserving distinct direct parents across diamond and cycle shapes.
- Add guidance to the `advanced-gradle-dependencies` skill so agents route version-provenance, reverse-consumer, and blast-radius questions to the appropriate Gradle data.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `dependency-filtering`: Extend `inspect_dependencies` post-processing and output with an optional reverse `consumers` edge containing direct parent dependencies.
- `advanced-gradle-dependencies`: Add decision-table guidance for answering version-provenance, reverse-consumer, and blast-radius questions.

## Impact

The dependency model and `inspect_dependencies` tool contract gain an additive, opt-in reverse edge. Dependency report processing must invert the resolved graph safely for diamonds and cycles without paying that cost when the option is disabled. The shipped `advanced-gradle-dependencies` skill and generated tool documentation must be updated, with focused unit and integration coverage for the new behavior.
