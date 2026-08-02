# Build Environment

Use this reference to inspect and reproduce the evaluated environment of an existing Gradle build. Operate configuration; do not author build logic. Route edits to properties, init scripts, toolchains, proxies, or other build configuration to `authoring-gradle-builds`.

## Resolve configuration by kind and precedence

Do not apply "nearest file wins" folklore. First classify the value, then identify its source and precedence.

| Channel | Documented priority, highest first | Sources and locations |
|---|---|---|
| Configuration channels | Command-line options > system properties > Gradle properties > environment variables | Gradle's `Priority for configurations` table; do not merge these channels into one property ladder. |
| Project properties | `-P` > `-Dorg.gradle.project.name` > `gradle.properties` > `ORG_GRADLE_PROJECT_name` | Gradle's `Project properties` section; file-backed values resolve `GRADLE_USER_HOME/gradle.properties` > project-root `gradle.properties` > `<GRADLE_HOME>/gradle.properties`. |
| Gradle properties | Command-line options override file-backed values; file locations resolve user > project > installation | `<GRADLE_USER_HOME>/gradle.properties`, `./gradle.properties`, and `<GRADLE_HOME>/gradle.properties`. |
| System properties | Command-line `-D` or `systemProp.*` file entries | Keep JVM/Gradle system properties distinct from project properties; the priority table identifies project-root file entries for this channel. |
| Environment variables | Environment-sourced configuration, including `JAVA_HOME` | `ORG_GRADLE_PROJECT_name` is the environment mechanism for a project property, not a universal override. |

**Do this:** record the wrapper version, `GRADLE_USER_HOME`, relevant property names, and explicit command-line controls before comparing runs. Run `properties` on the relevant project to inspect effective project values. Filter captured output rather than dumping it into a shared log.

**Anti-pattern:** infer one effective value from one file, or treat a user-home setting as evidence of project reproducibility.

**Gradle 9.4 wrapper check:** Read `gradle/wrapper/gradle-wrapper.properties` before applying precedence guidance. Wrapper properties control wrapper bootstrap and are not another Gradle property source; once the wrapper launches Gradle, use the documented command-line, system-property, Gradle-property, and environment-variable precedence for the build.

**More info:**
- `gradle_docs`: `tag:userguide`, path `userguide/build_environment.md`, terms `Priority for configurations`, `Available mechanisms`
- Effective project properties: `gradle` and `captureTaskOutput`

## Property channels and locations

Use project properties for values consumed by build logic and system properties for Gradle or JVM controls.

| Channel | Example | Use when | Do not use for |
|---|---|---|---|
| Project property | `-Penv=ci`, `ORG_GRADLE_PROJECT_env=ci`, `gradle.properties` | The build model explicitly consumes an input. | JVM flags or Gradle launcher controls. |
| System property | `-Dorg.gradle.daemon=true`, `-Dhttp.proxyHost=proxy.example` | Gradle, the JVM, or code explicitly reads a system property. | A project input that should be visible as a project property. |

Inspect effective values with `:properties` or `:<project>:properties`; use `--property <name>` where the wrapper supports it, otherwise capture and filter the full report. `-P` and `-D` are invocation-visible inputs, so record their names without recording secret values.

Choose locations deliberately:

- Put reproducible, non-secret project inputs in the checkout's `gradle.properties`.
- Put personal machine defaults in `GRADLE_USER_HOME/gradle.properties`.
- Use `-P`, `-D`, or environment-derived properties for ephemeral invocation and CI inputs.
- Never hide project-critical behavior in a user file. Never commit credentials or secret values.

**Anti-pattern:** move a failing project's required property into a personal user file until the local build passes. That makes the failure non-reproducible.

**More info:**
- `gradle_docs`: `tag:userguide`, path `userguide/build_environment.md`, terms `The gradle.properties file`, `Project properties`, `System properties and environment variables`
- Property inspection: `gradle` and `captureTaskOutput`

## Environment variables

Capture names and safe metadata, not values that may contain credentials or tokens.

| Variable | Actually controls | JVM boundary / caution |
|---|---|---|
| `GRADLE_USER_HOME` | The Gradle User Home containing caches, daemon state, wrapper distributions, init scripts, properties, build-cache state, and downloaded JDKs. | It changes the state universe for cache and daemon comparisons. |
| `GRADLE_OPTS` | JVM options for the Gradle client and, where applicable, Gradle process startup. | Do not equate it with project properties or assume it tunes forked workers. Compare it with daemon settings. |
| `JAVA_OPTS` | JVM options for the Gradle client JVM. | It does not configure the Gradle daemon JVM. |
| `JAVA_HOME` | The Java installation used to launch the Gradle client, subject to Gradle compatibility. | It is not the project's compile/test toolchain. |
| `ORG_GRADLE_PROJECT_name` | Project property `name`. | Treat the value as sensitive; use it for CI-supplied inputs without printing it. |
| `NO_COLOR` | Disables color in supported command output. | It is an output control, not a logging-level or diagnostic control. |

**Do this:** take a redacted environment snapshot containing variable names, `JAVA_HOME` path metadata, wrapper version, `GRADLE_USER_HOME`, and selected non-secret flags. Redact proxy credentials, tokens, passwords, cookies, signing material, and full secret-bearing command lines.

**Anti-pattern:** print all environment variables, all properties, or a full build scan URL into a shared diagnostic artifact. A scan or log can exfiltrate more than the failing task requires.

**Version notes:** `NO_COLOR` and the current client/daemon distinctions are documented in current Gradle guidance. For Gradle 7.x, retain the established `GRADLE_USER_HOME`, `JAVA_HOME`, `GRADLE_OPTS`, and `JAVA_OPTS` semantics, then verify newer output controls against the wrapper version.

**More info:**
- `gradle_docs`: `tag:userguide`, path `userguide/build_environment.md`, terms `Environment variables`, `System properties and environment variables`
- Safe build queries: `query_build`

## JVM ownership boundary

Classify the failing JVM before changing memory, Java versions, or flags.

| Owner | Primary controls | Does not control |
|---|---|---|
| Gradle client JVM | `JAVA_HOME`, `JAVA_OPTS`, relevant `GRADLE_OPTS` | The project's compiler, test worker, or application JVM. |
| Gradle build/daemon JVM | `org.gradle.jvmargs`, daemon compatibility, `org.gradle.java.home` where applicable | Forked test workers and application processes. |
| Compile/test toolchain | Project toolchain configuration and selected JDK | Whether the Gradle launcher itself can start. Authoring changes belong to `authoring-gradle-builds`. |
| Test worker or application JVM | Test task worker settings, `JavaExec`/application settings, plugin-specific configuration | The Gradle daemon heap. Authoring changes belong to `authoring-gradle-builds`. |

`org.gradle.jvmargs` tunes the Gradle build and daemon JVM. It does **not** tune forked test workers or application JVMs. `JAVA_OPTS` tunes the client JVM. `GRADLE_OPTS` is a Gradle process-startup channel and is not a substitute for `org.gradle.jvmargs`. `org.gradle.java.home` selects a Gradle JVM for the build when supported; project toolchains select JDKs for compilation and related tasks. Keep these diagnoses separate.

Toolchain provisioning is a repository, vendor, and platform problem as well as a Gradle configuration problem. Verify the provisioning source, vendor availability, platform/architecture support, network or credentials, and the resolved JDK before changing build logic.

**Do this:** compare `JAVA_HOME`, `org.gradle.java.home`, `org.gradle.jvmargs`, `GRADLE_OPTS`, daemon identity, and the project toolchain independently. Change only the owner of the failing process.

**Anti-pattern:** change a compile toolchain to repair a launcher error, or increase `org.gradle.jvmargs` to fix an out-of-memory test worker.

**Version notes:** Gradle 9 documents daemon JVM criteria and auto-provisioning more explicitly. For Gradle 7.x, use the launcher `JAVA_HOME`, `org.gradle.java.home`, and project toolchain model; do not apply 9.x-only criteria without checking the wrapper docs.

**More info:**
- `gradle_docs`: `tag:userguide`, path `userguide/build_environment.md`, term `Gradle properties`
- `gradle_docs`: `tag:userguide`, path `userguide/gradle_daemon.md`, term `The Gradle Client vs. the Gradle Daemon`
- `gradle_docs`: `tag:userguide`, path `userguide/config_gradle.md`, term `Changing JVM settings for the build VM`

## Proxy and network configuration

Use standard JVM proxy properties in the appropriate Gradle property channel for the network path under test:

```text
systemProp.http.proxyHost=proxy.example
systemProp.http.proxyPort=8080
systemProp.https.proxyHost=proxy.example
systemProp.https.proxyPort=8080
systemProp.http.nonProxyHosts=localhost|127.*|[::1]
```

Test wrapper/bootstrap, plugin resolution, and dependency resolution as separate paths. A proxy that works for one path is not proof that all network access uses the same configuration.

**Do this:** use a secret manager or ephemeral environment injection for proxy credentials, then verify the affected path with a narrow operation.

**Anti-pattern:** put `systemProp.*.proxyPassword`, tokens, or encoded credentials in tracked `gradle.properties`, command examples, build scans, or ordinary diagnostic output.

**Version notes:** Standard `systemProp.http[s].proxy*` mechanisms are stable across Gradle 7, 8, and 9; wrapper/bootstrap and plugin-resolution behavior remains version- and configuration-specific. For Gradle 7.x, use the same property names and verify the exact path.

**More info:**
- `gradle_docs`: `tag:userguide`, path `userguide/networking.md`, term `Accessing the web through a proxy`

## Hidden global inputs and secure diagnosis

Inspect `GRADLE_USER_HOME/init.d` and any `--init-script` supplied to the invocation when behavior differs from the checkout. Init scripts and global configuration can add repositories, tasks, listeners, properties, or JVM-affecting behavior without appearing in project files.

**Do this:** record the presence and paths of init scripts, user-home properties, wrapper version, and safe environment metadata. Use `query_build` for structured evidence. Use `--scan` only when publication is authorized; treat scan terms, destination, environment data, proxy details, and credentials as policy-sensitive.

**Anti-pattern:** edit an init script or global configuration while operating an existing build. Hand off all authoring changes to `authoring-gradle-builds`.

**Version notes:** Init-script mechanics are stable across Gradle 7, 8, and 9, but APIs and configuration details are version-sensitive. For Gradle 7.x, inspect only and verify the wrapper-specific documentation.

**More info:**
- `gradle_docs`: `tag:userguide`, path `userguide/build_environment.md`, terms `Gradle initialization scripts`, `Available mechanisms`
- Structured build diagnostics: `query_build`
