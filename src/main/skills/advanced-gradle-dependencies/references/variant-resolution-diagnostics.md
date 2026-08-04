# Variant Resolution Diagnostics

Diagnose why a variant was or was not selected during dependency resolution. This is the operate half of the diagnose-to-fix loop: run the authoritative reports first, identify the attribute or rule mismatch, then author the smallest correction and re-diagnose. Hand off read-only graph inspection to `using-gradle` when the question is the winner or the resolved graph, not the variant model.

Read `gradle/wrapper/gradle-wrapper.properties` before version-sensitive advice: the variant model, attribute matching, and report output change across Gradle versions.

## Attributes and the Variant Model

A variant is a consumable configuration carrying a set of attributes (typed key/value pairs). Resolution selects a producer variant by comparing the producer's outgoing attributes against the attributes the consumer's resolvable configuration requests. Built-in attributes (e.g. `org.gradle.usage`, `org.gradle.libraryelements`, `org.gradle.category`) cover the common axes; custom attributes express project-specific distinctions.

**Default:** keep the attribute set minimal and shared across producer and consumer (define custom attributes in a convention plugin or shared model). Add a fallback/default attribute value on the producer so an unqualified consumer still matches.

**Anti-pattern:** inventing ad hoc string keys per project (each with its own `Attribute.of` type), or overloading a built-in attribute to carry project-specific meaning.

**Standard attribute axes to recognize while diagnosing** (each carries ecosystem compatibility semantics baked in):
- `org.gradle.usage` — the main purpose (`java-api` is compatible with `java-runtime`, not the reverse).
- `org.gradle.category` — the kind of component (e.g. `library`, `documentation`).
- `org.gradle.libraryelements` — contents of a library (e.g. `jar` compatible with `classes`).
- `org.gradle.dependency.bundling` — whether dependencies are `external` or `embedded`.
- `org.gradle.jvm.version` (lower is compatible with higher) and `org.gradle.jvm.environment` (e.g. `standard-jvm`, `android`).
- `org.gradle.docstype` — contents of a documentation variant.

Custom attributes are created as typed attributes via `Attribute.of(...)`. A mismatch between the requested attribute's TYPE and the producer's declared TYPE is a hidden cause of no-matching-variant failures: the names look identical, but Gradle treats them as distinct attributes.

Variant names are cosmetic — matching is purely attribute-based. Never diagnose by variant name alone; always read the attributes.

Maven POM and Ivy modules are mapped onto Gradle variants: a POM maps to `api`/`runtime` elements, and an Ivy module maps to one variant per configuration. Reach for this when an attribute puzzle involves an external module rather than a project you control.

## Compatibility Rules vs Disambiguation Rules

These two rule families operate at different stages of variant selection and are frequently confused:

- **Compatibility rules** decide whether a producer variant can satisfy the consumer at all. A consumer attribute is compatible when the requested value is equal to, or a subtype of, the producer's candidate value, or when the producer carries the fallback/default value for that attribute. An attribute present on only one side does not disqualify a variant.
- **Disambiguation rules** decide which of several compatible variants wins. When more than one variant is compatible, Gradle prefers the candidate whose value is most specific (and not the fallback). If multiple variants remain tied, resolution fails with an ambiguous-variant error.

A "no matching variant" error means no candidate is compatible (a compatibility problem). An "ambiguous variant" error means too many candidates remain tied (a disambiguation problem). Diagnosing which stage failed determines the correct rule to author.

**Anti-pattern:** adding a disambiguation rule to fix a compatibility failure, or adding a compatibility default to break a disambiguation tie. Each must be applied to the correct stage.

## The `outgoingVariants` Report

`outgoingVariants` prints every consumable variant a project exposes with its attributes, capabilities, and artifacts. Use it to inspect what a producer actually offers before authoring a rule:

```text
:producer:outgoingVariants
```

Capture the task output with the Gradle MCP `gradle` tool and `captureTaskOutput` for the reporting task. Read the attributes each variant carries and confirm whether a fallback/default value is present.

**Use it when:** a no-matching-variant or ambiguous-variant error names a producer you control, and you need the authoritative list of what it exposes.

## `dependencyInsight --all-variants`

`dependencyInsight` explains why a specific dependency was selected. For variant work, the key is the `--all-variants` flag, which shows the candidate variants considered for that dependency and which attributes each carries, making the compatibility/disambiguation failure explicit:

```text
:app:dependencyInsight --dependency <module> --configuration <configuration> --all-variants
```

Run it through the Gradle MCP `gradle` tool with `captureTaskOutput` for the `dependencyInsight` task. Compare the failing configuration's requested attributes against each candidate variant's attributes to locate the mismatch.

## Reading the `dependencies` and `dependencyInsight` Reports

The two reports answer different halves of the same question — the `dependencies` tree tells **what** resolved, `dependencyInsight` tells **why**:

- `dependencies` renders the resolved tree per configuration. Read the annotations: `(*)` marks a repeated transitive subtree (expanded only once, not re-expanded each time), `(c)` marks a dependency CONSTRAINT rather than a dependency, and `(n)` marks an element that could not be resolved. Narrow with `--configuration` (abbreviated names like `tRC` are accepted); enumerate configurations and their roles with the `resolvableConfigurations` report.
- `dependencyInsight` takes `--dependency`, `--configuration`, `--single-path` (render only a minimal path to the dependency), and `--all-variants`. It explains why a version/variant was selected and where it came from.

Decode the selection-reasons vocabulary to locate the lever:
- **"Was requested"** — a declaration, often with a `because(...)` note (or dynamic/rich-version variants).
- **"By conflict resolution"** — multiple distinct versions were requested and the highest won.
- **"By constraint"** and **"By ancestor"** — a constraint or a rich version's `strictly` participated in selection.
- **"Selected by rule"** / **"Rejection ... by rule"** — a selection rule overrode or rejected a candidate.
- **"Forced"** — an enforced platform or resolution strategy pinned the version.

A Build Scan (`--scan`) renders the same tree and insights as a searchable, shareable report.

### Variant-Aware Sharing Between Projects

Sharing a non-default artifact between projects is a producer/consumer pairing built entirely on attributes:

- **Producer:** expose an extra variant with a custom `consumable` configuration carrying distinguishing attributes, and attach the artifact with `artifacts { add(...) }`. Inspect what the project actually offers with the `outgoingVariants` report.
- **Consumer:** keep a declaration-only (`dependencyScope`-style) configuration and a `resolvable` configuration wired through `extendsFrom`, requesting the same attributes as the producer's extra variant. Verify the pairing with `resolvableConfigurations`.
- **Fallback:** when a producer lacks the special variant, register an `AttributeCompatibilityRule` on `attributesSchema` so the requested value stays compatible with the plain default (e.g. `instrumented-jar` ↔ `jar`).
- **Troubleshoot** a failed resolve by confirming attribute compatibility on both sides, that the artifact is actually declared, and that no conflicting configuration shadows it.

## Diagnose-to-Fix Loop

1. Read the wrapper version; record the failing configuration and its requested attributes.
2. Run `dependencyInsight --all-variants` for the failing dependency and configuration.
3. If a producer you control is involved, run `outgoingVariants` on it to enumerate its exposed variants.
4. Classify the failure: no matching variant (compatibility) vs ambiguous variant (disambiguation).
5. Author the smallest correction on the correct side:
   - Producer: add a fallback/default attribute value, or align the variant's attributes.
   - Consumer: request the attribute the producer actually exposes, or add a disambiguating attribute to break a tie.
6. Re-run `dependencyInsight --all-variants` to confirm the variant now matches and no ambiguity remains.

**More info:**
- Attribute matching and variant-aware resolution: `gradle_docs(path="userguide/variant_model.md")`
- Attribute compatibility and disambiguation rules: `gradle_docs(path="userguide/variant_attributes.md")`
- Viewing and debugging dependencies: `gradle_docs(path="userguide/viewing_debugging_dependencies.md")`
- Declaring configurations and attributes: `gradle_docs(path="userguide/declaring_configurations.md")`
- Variant selection and attribute matching: `gradle_docs(path="userguide/variant_aware_resolution.md")`
- Sharing outputs between projects (consumable configurations and attributes): `gradle_docs(path="userguide/how_to_share_outputs_between_projects.md")`
- Dependency configurations (roles and the declarable set): `gradle_docs(path="userguide/dependency_configurations.md")`
- Authoring the configuration and variant model: `authoring-gradle-builds`'s [Configurations and Variants](../authoring-gradle-builds/references/configurations-and-variants.md)
- Graph and winner inspection: `inspect_dependencies`; `dependencyInsight` via the `gradle` tool.
