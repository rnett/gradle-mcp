## MODIFIED Requirements

### Requirement: Authoritative Documentation Routing
The skill MUST route every major authoring topic to a verified version-scoped `gradle_docs` tag and path hint, and MUST NOT embed published `docs.gradle.org` URLs or `gradle-mcp.rnett.dev` tool-documentation pointers as documentation citations. Advanced authoring topics MUST include service injection, build services, value sources, project isolation, build lifecycle, Kotlin DSL, managed types/providers (including incubating dataflow actions), binary plugin development with TestKit and publishing, Java builds and source sets/annotation processing, configurations and feature variants with variant-aware sharing, and build-cache/configuration-cache authoring and debugging. Cache and isolation enablement, persistent configuration, and operational outcome reading route to `using-gradle`; cacheability and configuration-cache-safe authoring remain here.

#### Scenario: Route an authoring topic through the tool
- **WHEN** an agent needs guidance on dependencies, modules and settings, convention plugins, custom tasks, build lifecycle, Kotlin DSL, managed types/providers, toolchains, Kotlin compiler options, Java builds, configurations and variants, testing, plugin development and TestKit, publishing, locking, CI, build scans, or advanced configuration
- **THEN** the relevant reference provides a `gradle_docs` tag and path hint and routes exclusively through `gradle_docs`, with no published `docs.gradle.org` URL and no `gradle-mcp.rnett.dev` pointer

#### Scenario: Route advanced authoring topics
- **WHEN** an agent needs to implement service injection, a build service, a value source, or project-isolation-compatible logic
- **THEN** it is routed to the advanced authoring reference and its verified `gradle_docs` tag and path hint rather than an undocumented or generic recommendation

#### Scenario: Research a migration target version
- **WHEN** an agent performs a Gradle upgrade whose target version differs from the project's wrapper
- **THEN** the upgrading guidance directs the agent to query `gradle_docs` with `tag:upgrading` (and `tag:release-notes`) using an explicit `version="<target>"`, and warns that a coarse version such as `"8"` fails and that omitting `version` resolves to the wrapper rather than the target
