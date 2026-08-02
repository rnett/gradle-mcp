# Build Scans

A Build Scan is a published, shareable record of a Gradle invocation. It can expose build performance, task outcomes, dependency resolution, environment data, and failure diagnostics. Publication is an external side effect, not local logging.

**Default:** Treat scan publication as opt-in. Use the `com.gradle.develocity` plugin in `settings.gradle.kts` when a repository needs centralized policy, consistent metadata, or CI publication rules. Use ad-hoc `--scan` only for an explicitly authorized diagnostic or publication.

**Anti-patterns:** Do not add `--scan` to every local command, silently publish scans from CI, or promise that a scan is private because the build itself is private. Do not put secrets, tokens, credentials, source contents, or personal data into custom scan values or tags.

## Choose the Publication Path

### Declarative Develocity plugin

Apply the Develocity plugin in `settings.gradle.kts`, not a project `build.gradle.kts`, when scan behavior is a build-wide concern. Pin a tested plugin version and keep the version compatible with the wrapper. The exact plugin DSL and terms properties are version-sensitive; consult the plugin's versioned documentation before copying a configuration.

```kotlin
// settings.gradle.kts
plugins {
    id("com.gradle.develocity") version "<tested-version>"
}

develocity {
    buildScan {
        publishAlways()
        value("ci", providers.gradleProperty("ci").orNull ?: "false")
    }
}
```

Use `publishOnFailure()` instead of `publishAlways()` when failure diagnostics are sufficient and successful-build publication is not approved. Gate publication with the CI environment or an explicit property when the policy requires it. Keep custom values low-cardinality and non-sensitive.

### Ad-hoc `--scan`

Use the command-line flag when the invocation owner intentionally requests publication and the build does not need repository-wide scan policy:

```text
gradlew.bat :app:check --scan
```

`--scan` is not equivalent to merely enabling the plugin. It requests publication for that invocation and can trigger Terms-of-Service handling. A plugin can configure capture and publication policy, while an ad-hoc flag is a per-invocation decision. Do not claim that applying the plugin alone publishes every build unless the configured policy actually does so.

## Terms of Service and Non-Interactive Use

An interactive `--scan` invocation can stop for a Terms-of-Service acceptance prompt before publication. This is expected behavior, not a build failure caused by the task graph. Never make an unattended CI job depend on an interactive answer.

For a non-interactive, authorized publication, accept the prompt with the documented scan system property:

```text
gradlew.bat :app:check --scan -Dscan.termsOfServiceAgree=yes
```

The Develocity plugin can also configure the agreement through its build-scan settings. Verify the plugin's version-specific property names and accepted values before putting plugin DSL or system properties in CI. Do not suppress the prompt by guessing a system property, piping arbitrary input, or checking a terms agreement into source control without an owner-approved policy.

**Default:** Have CI fail clearly when publication is requested but consent is not configured. Record the consent decision in CI configuration or protected environment policy, not in a developer-local workaround.

**Anti-pattern:** Add `--scan` to a shared command template and rely on a human to accept terms on the first run. That creates a hanging or non-reproducible pipeline and may publish data without an explicit CI decision.

## What a Scan Captures

Use a scan to inspect, as applicable to the Gradle and Develocity versions in use:

- **Build execution:** task graph, task outcomes, timeline, critical path, configuration and execution timing, and performance signals.
- **Dependency resolution:** requested and selected versions, conflicts, repositories, and dependency-resolution diagnostics.
- **Failures:** failed task context, stack traces, build output, and environment clues useful for reproducing a failure.
- **Environment:** Gradle version, JVM details, operating system, build parameters, and CI metadata exposed by the build or plugin.
- **Custom metadata:** labels, values, links, and tags configured by build logic or CI.

A scan is not a complete source archive, but its metadata can still identify projects, branches, paths, dependency coordinates, environment details, and custom values. Review the service's current retention, access, and privacy terms before publishing. Do not add credentials or raw secrets to scan metadata, command-line arguments, environment-derived values, or build output. Redact or avoid sensitive paths and identifiers when policy requires it.

## Publish from CI

Publish only when the pipeline has an explicit scan policy. Choose one of these controlled patterns:

```kotlin
// settings.gradle.kts
plugins {
    id("com.gradle.develocity") version "<tested-version>"
}

develocity {
    buildScan {
        publishOnFailure()
        if (providers.environmentVariable("CI").orNull == "true") {
            value("ci", "true")
        }
    }
}
```

The example expresses the policy shape, not a universal plugin-version contract. Validate the exact Develocity DSL against the plugin version selected by the build. In CI:

1. Decide whether to publish always or only on failure.
2. Configure non-interactive Terms-of-Service acceptance through the approved version-specific mechanism.
3. Ensure scan publication credentials or service endpoint policy are available without printing secrets.
4. Capture the published URL from Gradle's output and expose it as a protected job artifact or failure annotation.
5. Restrict visibility and retention according to the organization's build-data policy.

**Anti-pattern:** Publish every scan publicly to simplify debugging. A scan URL can grant access to more build metadata than the job log, and a URL copied into a public issue may be effectively permanent.

## Gradle MCP Publication

The Gradle MCP `gradle` tool exposes `invocationArguments.publishScan`. Set it to `true` only when the caller explicitly authorizes scan publication and the invoked build can satisfy the applicable Terms of Service non-interactively. Keep it `false` for ordinary introspection and verification.

The option controls the Gradle invocation's scan publication behavior; it does not replace repository-level Develocity configuration, consent policy, privacy review, or secret handling. Report the resulting scan URL only through an approved channel.

## Version notes

- **Gradle 9.x:** Prefer the current Develocity plugin and versioned DSL documentation, and keep the wrapper and plugin versions tested together. Treat `--scan` as an explicit publication request. Current Gradle documentation is the authority for scan capture and Terms-of-Service behavior.
- **Gradle 8.x:** The Develocity/Gradle Enterprise transition and plugin DSL details are version-sensitive. Preserve the same consent and privacy defaults, but verify property names and plugin compatibility against the selected plugin version.
- **Gradle 7.x:** Build scans and command-line publication are supported. Older builds may use historical Gradle Enterprise terminology or plugin coordinates; use the plugin's versioned documentation rather than copying a current DSL blindly. If the plugin cannot be used, retain ad-hoc `--scan` only as an explicitly authorized fallback.

**More info:**
- Build scans, publication, capture, and Terms of Service: query `gradle_docs` with `tag:userguide`, path `userguide/build_scans.md`, term `Terms of Service`
- Gradle MCP invocation and `publishScan`: `gradle`
- Gradle MCP documentation lookup for version-matched verification: `gradle_docs`
