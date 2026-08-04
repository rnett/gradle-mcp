# Artifact Publishing

Configure JVM library publication with `maven-publish` and `signing`. The current Maven Central destination is the Central Publisher Portal at `central.sonatype.com`; make that Portal flow the primary release recipe.

**Default:** Publish a complete, signed Maven publication with sources, Javadoc, and a validation-ready POM. Upload it from CI to the Portal's OSSRH-compatible staging endpoint with Central Portal user tokens, then complete the Portal handoff through an automated API or plugin step.

**Anti-patterns:** Do not use the retired OSSRH service, its old hosts or credentials, or a browser-dependent staging lifecycle. Do not publish a thin POM, commit signing keys, put tokens in `build.gradle.kts`, or make signing mandatory for every local development build.

## Apply the plugins and package source documentation

Apply the built-in plugins declaratively. For a Java component, enable both source and Javadoc artifacts so consumers and the Portal receive the complete library distribution.

```kotlin
plugins {
    `java-library`
    `maven-publish`
    signing
}

java {
    withSourcesJar()
    withJavadocJar()
}
```

Do not add `sourcesJar` or `javadocJar` tasks manually when the Java plugin already provides these options. Keep shared plugin and version policy in the existing convention-plugin or version-catalog structure rather than duplicating it in each publication script. See the frozen corpus entry [Use Kotlin DSL](best-practices/use-kotlin-dsl.md) for the project-wide authoring default and [Use the latest minor version of Gradle](best-practices/use-the-latest-minor-version-of-gradle.md) for version currency.

## Preserve module metadata and reproducible archives

Publish from the relevant component, for example `from(components["java"])`, and keep Gradle Module Metadata (`.module`) alongside the POM or Ivy metadata so consumers retain variant and capability information. The `.module` file carries the variant, capability, and dependency information Gradle consumers use for variant-aware resolution, while the POM serves Maven consumers. `maven-publish` emits `.module` alongside the POM by default when publishing from a component, so the operative rule is don't strip or suppress it: Gradle-only consumers gain, Maven consumers are unaffected. Prefer Gradle's reproducible archive defaults; do not add timestamp or ordering workarounds unless the target wrapper's documentation requires them. Configure publication metadata lazily, before the publication is created; eager publication APIs were removed in Gradle 9.0.

**Version-sensitive field-guide rule:** Read `gradle/wrapper/gradle-wrapper.properties` before applying these rules.

## Define a complete Maven publication

Create the publication from the Java component. Set coordinates from the project model or the existing convention; do not hard-code a second, conflicting version source. The Central Portal validates POM metadata, so provide all required identity and provenance fields.

```kotlin
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = "com.example"
            artifactId = "library"
            version = project.version.toString()

            pom {
                name.set("Library")
                description.set("A concise description of the library's public purpose.")
                url.set("https://github.com/example/library")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("maintainer")
                        name.set("Maintainer Name")
                        email.set("maintainer@example.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/example/library.git")
                    developerConnection.set("scm:git:ssh://git@github.com/example/library.git")
                    url.set("https://github.com/example/library")
                }
            }
        }
    }
}
```

**POM checklist:** `name`, `description`, `url`, at least one `license`, at least one `developer`, and `scm` must describe the actual artifact. Use HTTPS project and license URLs. Do not leave placeholder values in a release publication or rely on Gradle's generated defaults for Portal validation.

## Customize what gets published

**Shape a component's variants.** Components from the Java plugins (Java, Java Library, Java Platform) implement `AdhocComponentWithVariants`. Two distinct levers, BOTH called on the component, NOT on the publication:
- `addVariantsFromConfiguration(config) { ... }` adds variants from a consumable configuration to the component; this is how a registered feature variant is included in the publication. Inside the action, map dependencies with `mapToMavenScope("runtime")` or `mapToOptional()`.
- `withVariantsFromConfiguration(config) { ... }` modifies variants already added to the component, e.g. `skip()` the sources variant; it never adds variants.

```kotlin
val component = components["java"] as AdhocComponentWithVariants
component.withVariantsFromConfiguration(configurations["sourcesElements"]) {
    skip()
}
```

**Publish additional artifacts.** Attach extras with `artifact(...)` on the publication ONLY when metadata is irrelevant — such artifacts are published "out of context" and are not referenced in Gradle Module Metadata or the POM.

**Anti-pattern:** reaching for `artifact(...)` when consumers must discover the artifact; prefer adding a variant to a component.

**Publish a custom (ad hoc) component.** For artifact sets that aren't a standard Java component, the documented route is: obtain the factory via `extensions.getByType(PublishingExtension).softwareComponentFactory` (an accessor on the publishing extension since Gradle 9.2; the file's existing version-sensitive field-guide rule governs older wrappers), create with `adhoc("name")`, register it with `components.add(component)`, attach variants via `addVariantsFromConfiguration(...)`, then publish with `from(components["name"])`.

Full options, including conditional publishing and publish-task configuration, are in `gradle_docs(path="userguide/publishing_customization.md")`.

## Sign only when release key material is available

Maven Central releases require signatures, but local authoring and `publishToMavenLocal` should not fail merely because a developer has no release key. Configure an in-memory key from Gradle properties or environment variables and add signing only when both key and password are present.

```kotlin
val signingKey = providers.gradleProperty("signingKey")
    .orElse(providers.environmentVariable("SIGNING_KEY"))
val signingPassword = providers.gradleProperty("signingPassword")
    .orElse(providers.environmentVariable("SIGNING_PASSWORD"))

signing {
    if (signingKey.isPresent && signingPassword.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), signingPassword.get())
        sign(publishing.publications["mavenJava"])
    }
}
```

Supply `signingKey` and `signingPassword` through an uncommitted user-level `gradle.properties` file or CI secret variables. Never print, persist, or commit the armored key, password, or Portal token. A release task must run with signing material present; conditional signing is a local-development guard, not permission to upload unsigned artifacts.

**Anti-pattern:** Do not call `useGpgCmd()` unconditionally in a shared build when CI has no configured GPG executable. Do not make `signing.required { true }` global if the build must support unsigned local publication; apply release-only enforcement in the release pipeline or release publication convention.

## Publish to the Central Publisher Portal

Sonatype's current Gradle guidance has no official first-party Gradle plugin for direct Portal publishing. For a build that uses Gradle's built-in `maven-publish`, use the Central Portal's OSSRH-compatible staging endpoint as the upload repository, not the retired OSSRH service:

```kotlin
publishing {
    repositories {
        maven {
            name = "centralPortal"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = providers.gradleProperty("centralPortalUsername")
                    .orElse(providers.environmentVariable("CENTRAL_PORTAL_USERNAME"))
                    .get()
                password = providers.gradleProperty("centralPortalPassword")
                    .orElse(providers.environmentVariable("CENTRAL_PORTAL_PASSWORD"))
                    .get()
            }
        }
    }
}
```

The `ossrh-staging-api.central.sonatype.com` host is the Central Portal's OSSRH-compatible staging endpoint, a compatibility shim for Maven-like clients. It is not the retired OSSRH service. Authenticate the repository with the Central Portal token username and token password; old service credentials are not valid.

After `publish`, make the deployment visible in the Portal through the compatibility service's documented manual API endpoint, or use a compatible automation plugin in CI. Sonatype lists `io.github.gradle-nexus.publish-plugin` among plugins tested for compatibility with this service, but does not present it as an official or required recommendation. Treat it as an optional automation mechanism, not as the canonical workflow name. If it is used, configure it with Portal tokens and the compatibility endpoint, and keep the close/upload handoff automated and release-only.

The compatibility service documentation explains that Gradle's built-in `maven-publish` requires the documented manual handoff because it only sends Maven repository `PUT` requests. Prefer the Portal API handoff in CI over interactive operator steps. See [Portal OSSRH Staging API](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api) for the endpoint, token authentication, and handoff contract, and [Central Portal Gradle guidance](https://central.sonatype.org/publish/publish-portal-gradle) for the current plugin-status and community-plugin guidance.

## Verification handoff

Before any remote publication, inspect the generated POM, module metadata, source JAR, Javadoc JAR, and signatures from a local publication. Hand off execution and inspection of `publishToMavenLocal` to `using-gradle`; this reference defines the authoring contract and does not run Gradle tasks.

For those authoring distributable binary plugins, see [Plugin Development](plugin-development.md) for the specific requirements of the Gradle Plugin Portal, marker artifacts, and plugin-ID resolution.

Handoff checklist:

1. Ask `using-gradle` to run `publishToMavenLocal` for the intended project.
2. Ask `using-gradle` to inspect the local Maven repository output and generated POM.
3. Confirm coordinates, metadata, sources, Javadoc, and signatures before supplying Portal credentials.
4. Run the Portal publication only from a controlled release environment with the signing key and Central Portal token present.
5. Verify that the CI handoff reports the Portal deployment status and fails on a rejected or dropped deployment.

See [using-gradle research](../../using-gradle/references/research.md) for the single home of Gradle MCP documentation mechanics and execution handoffs.

## Version notes

- **Gradle 9.x:** Bias to the current 9.x minor. Use declarative plugins, lazy providers, `maven-publish`, `signing`, and the Java component model shown here.
- **Gradle 8.x:** The publication and signing APIs used here remain supported. Verify the wrapper-specific DSL and plugin versions before changing release logic.
- **Gradle 7.x:** `maven-publish`, `signing`, `withSourcesJar()`, and `withJavadocJar()` are available in supported Java builds, but older plugin conventions may require version-compatible syntax. Preserve an existing convention plugin or applied-script pattern rather than introducing a newer API without checking the wrapper.
- **All versions:** The retired OSSRH service is not a Gradle-version compatibility fallback. Use the Central Portal flow and Portal credentials for Gradle 7, 8, and 9.

## More info

- Gradle publication model: `gradle_docs(path="userguide/publishing_maven.md")`
- Publishing setup: `gradle_docs(path="userguide/publishing_setup.md")`
- Gradle Module Metadata: `gradle_docs(path="userguide/publishing_gradle_module_metadata.md")`
- Publishing customization: `gradle_docs(path="userguide/publishing_customization.md")`
- Signing publications: `gradle_docs(path="userguide/publishing_signing.md")`
- Gradle MCP documentation lookup: `gradle_docs`
- Gradle MCP task execution handoff: `gradle`
- Central Portal sunset announcement: https://central.sonatype.org/news/20250326_ossrh_sunset.
- Central Portal migration and current service: https://central.sonatype.org/pages/ossrh-eol.
- Central Portal publishing guide: https://central.sonatype.org/publish/publish-portal-guide.
- Central Portal Gradle/community options: https://central.sonatype.org/publish/publish-portal-gradle.
- Portal OSSRH-compatible staging endpoint: https://central.sonatype.org/publish/publish-portal-ossrh-staging-api.
