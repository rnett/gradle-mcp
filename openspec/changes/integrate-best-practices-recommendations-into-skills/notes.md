## Body highlight selection

The 13 hardest-to-figure-out entries selected from the recommendation document's `Why (incl. why hard to figure out)` field are:

1. Keep expensive work out of configuration
2. Model initialization, configuration, and execution separately
3. Use configuration avoidance throughout the model
4. Propagate laziness with providers and managed properties
5. Read providers only at an execution boundary
6. Wire cross-project behavior through model relationships
7. Avoid `afterEvaluate` and `projectsEvaluated` as configuration mechanisms (version-sensitive)
8. Distinguish `set(null)` from an absent provider
9. Interpret task outcomes before claiming work occurred
10. Respect dependency cache TTL versus `--refresh-dependencies` (version-sensitive)
11. Match daemon identity before using `--status` or `--stop`
12. Verify the wrapper distribution checksum
13. Treat `--scan` as metadata publication

High-severity cross-cutting entries are also surfaced in the two hub bodies where they are operationally relevant. Detailed guidance, do/don't snippets, and `gradle_docs` hints remain in the linked authored references.

## Placement and traceability audit

- `[Writes build logic]` entries map to `authoring-gradle-builds` references by topic: lifecycle and configuration work to `build-lifecycle.md`; task realization and execution-boundary providers to `custom-tasks.md`; managed values to `managed-types-and-providers.md`; public services to `advanced-configuration.md`; project relationships to `convention-plugins.md`; dependencies and repositories to `dependencies-and-catalogs.md`; plugin and JVM concerns to `plugin-development.md` and `jdk-toolchains.md`; variants to `configurations-and-variants.md`; and specialized delivery topics to the existing authored routing references.
- `[Runs builds]` entries map to `using-gradle` references by topic: execution and outcomes to `running-builds.md`; cache, daemon, wrapper, scan, and diagnostic hazards to `troubleshooting.md`; dependency freshness and graph provenance to `dependencies.md`; JVM and property ownership to `build-environment.md`; tests to `testing.md`; and documentation or source investigation to `research.md`.
- Every entry with a do/don't snippet is kept in an authored reference, not in a hub body. The frozen `references/best-practices/` corpus is not edited or restated.
- Version-sensitive guidance retains its marker and points back to the wrapper-version check in the relevant hub and reference.

## Version boundary

The source field guide is based on Gradle 9.6.1 documentation, while this project wrapper is Gradle 9.4.1. Agents must read `gradle/wrapper/gradle-wrapper.properties` before applying any version-sensitive entry and resolve the matching `gradle_docs` path or tag.


## Post-integration audit

- An independent coverage audit drove these additions under the semantic-coverage criterion: required knowledge must be present and usable, not verbatim parity with the source report.
- Dependency security, publishing, plugin authoring, upgrade migration, settings, JVM execution, and test-configuration gaps were filled only where the authored references were missing them.
- The frozen `references/best-practices/` corpus remains unchanged; authored references link to it for detailed rationale.
