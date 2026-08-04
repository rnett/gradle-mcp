# Dependency Verification

Dependency verification authenticates artifact bytes and publisher identity via `gradle/verification-metadata.xml`. It is **conditional guidance**, not a baseline recommendation: only enable it when the user explicitly asks for supply-chain hardening. It is distinct from dependency locking (`gradle.lockfile`), which pins resolved versions; the two solve different problems and neither replaces the other.

Report these UX costs honestly before enabling verification:
- Every dependency change (version bump, added library, transitive change) triggers a metadata maintenance step, often requiring editing `verification-metadata.xml` or regenerating checksums.
- Dependency updates become more involved and can fail on missing or stale metadata, adding friction to routine upgrade workflows.
- Failure modes from missing or stale metadata can block resolution, requiring manual review or metadata regeneration to unblock.

**Anti-pattern:** presenting verification as a default every build should adopt, or disabling it to unblock a build. If verification is enabled, **never** use `--dependency-verification=off` or lenient mode to unblock a build — missing metadata, bad checksums, or untrusted signatures require review rather than silent disabling.

Read `gradle/wrapper/gradle-wrapper.properties` before applying version-sensitive advice; verification configuration and tooling behavior change across Gradle versions.

## `verification-metadata.xml` Structure

Gradle writes verification state to `gradle/verification-metadata.xml`. The file has two top-level sections:

- **`<verification-metadata>`**: the root element carrying the schema version and a `dependencies` element.
- **`<dependencies>`**: the set of **`<trusted-key>`** and **`<component>`** entries that declare the verification policy for specific components.

A `component` entry pins the verification mode and trust for one module coordinate (a group or a full group:name). A `trusted-key` entry pins a PGP key fingerprint, optionally scoped to a group, that Gradle requires signatures to match.

Gradle writes or updates this file when you generate it via the `--write-verification-metadata` command-line option. Do not hand-edit it casually; regenerate and review the diff as a deliberate supply-chain step.

## PGP Key Handling

PGP signatures let Gradle verify that a published module was signed by a trusted key. Management is PGP-key-centric:

- **Trusted keys** are declared as `<trusted-key id="fingerprint" group="..."/>` entries in `verification-metadata.xml`.
- **Generating metadata** instructs Gradle to discover and record the signatures and trusted keys for the resolved graph, so a new build trusts exactly the keys that signed its declared dependencies.
- **Trusting a specific key** adds its fingerprint to the trusted-key set, scoped narrowly (by group) when a key signs only some modules.

Treat a fingerprint as a supply-chain control: pin the canonical 40-hex-digit fingerprint, scope trusted keys to the groups they actually sign, and review any newly added key alongside the dependency change that introduced it.

**Anti-pattern:** trusting a broad key fingerprint with no group scope, or adding trusted keys without reviewing the dependency that pulled the new publisher in.

## Checksums

Checksums verify artifact bytes against a recorded hash, independent of any signature. `verification-metadata.xml` records per-component checksums that Gradle validates during resolution.

- **Trusted checksums** are written as `<component group="..." name="..." version="..."><sha256 value="..."/></component>` entries.
- **Generating metadata** records checksums for the resolved graph; a later resolution fails if the artifact bytes no longer match.

Checksums and PGP keys are complementary: a checksum confirms byte identity; a signature confirms publisher identity. Record both when a module carries a signature, because a checksum alone does not tell you who published the artifact.

**Anti-pattern:** relying on checksums alone to establish publisher trust, or treating a changed checksum as a reason to silently disable verification rather than investigating the artifact change.

## CI Workflows

CI is where verification earns its value and where its maintenance cost is most visible. A verification-aware CI pipeline:

1. **Commits `verification-metadata.xml`** so every job verifies against the same pinned policy.
2. **Runs normal builds with verification enabled**, failing on missing metadata, bad checksums, or untrusted signatures.
3. **Regenerates metadata in a review-gated path only** — a separate dependency-update job or explicit developer command — never as an unattended verification side effect.
4. **Trains on missing/stale metadata as a review trigger**, not a reason to use `--dependency-verification=off`.

Keep verification out of ordinary verification commands' failure path only when it is explicitly enabled; the normal CI safety net is that a locked-and-verified build fails loudly on supply-chain change rather than silently accepting it.

**Anti-pattern:** combining `--write-verification-metadata` with an unattended pipeline, or committing a regenerated metadata file without reviewing the new keys and checksums it introduces.

## Generating, Updating, and Maintaining Metadata

`--write-verification-metadata` bootstraps the metadata file. What it records depends on what is collected in the run: a checksum-only pass records per-component `sha256` values; a signatures pass additionally records the trusted keys and PGP data. Regeneration is a deliberate, review-gated supply-chain step — never an unattended side effect of a routine build.

- **Updating** is either automatic (regenerate and review the diff) or manual (edit entries by hand for a precise change). Keep `gradle/verification-metadata.xml` in VCS so every job verifies against the same pinned policy.
- **Key servers/keyrings:** during generation, Gradle fetches trusted public keys from configured key servers. Export the keys used into a local keyring for offline or faster verification, and refresh missing keys as needed — this is the PGP trust-maintenance surface you own.
- **Scope:** verification configuration is **global** to a build — a single file applies to the root project, all subprojects, and `buildSrc`. Included builds use the *current* build's verification config (their own metadata is ignored), and adding an included build may require updating the current build's metadata.

Troubleshooting map for verification failures:

- **Missing metadata** → regenerate (the entry was never recorded or dropped).
- **Incorrect checksum** → investigate the artifact change first; only correct the entry once you are confident the new bytes are legitimate.
- **Untrusted or failed signature** → trust the correct key (narrowly scoped); never disable verification to unblock.

## Verification Modes and Relaxing Enforcement

Gradle verifies artifacts and, with the `verify-metadata` flag enabled, metadata files (POM, Ivy descriptors, Gradle Module Metadata) too. Missing or stale metadata fails resolution — see the troubleshooting map above.

Production-safe relaxation is SCOPED. Skipping javadoc/source verification for a specific component, or narrowing the set of trusted keys, keeps the rest of the graph enforced. Never use `--dependency-verification=off` or lenient mode as an unblock — lenient mode exists only as a temporary cleanup aid during a deliberate migration, exercised for a bounded time, then hardened back. This is the same anti-pattern guidance in the [CI Workflows](#ci-workflows) section above: a failing verification is a review trigger, not a reason to silent-disable.

## Locking vs Verification

| Control | What it authenticates | Failure mode |
|---|---|---|
| Dependency locking (`gradle.lockfile`) | Pins resolved versions for reproducibility | A locked graph refuses a changed version |
| Dependency verification (`verification-metadata.xml`) | Authenticates artifact bytes and publisher identity | Verification fails on missing metadata, bad checksums, or untrusted signatures |

They are independent: locking makes the resolved graph reproducible; verification makes the downloaded artifacts trustworthy. A lockfile is not proof that an artifact is trustworthy, and verification does not make the graph reproducible. Size the two controls to the requirement: lock for deterministic builds, verify only under an explicit supply-chain hardening request.

**More info:**
- Dependency verification: `gradle_docs(path="userguide/dependency_verification.md")`
- Dependency locking: `gradle_docs(path="userguide/dependency_locking.md")`
- Locking basics live in `authoring-gradle-builds`'s [Dependency Locking](../authoring-gradle-builds/references/dependency-locking.md); the locking-vs-verification distinction is also retained in [Dependencies and Catalogs](../authoring-gradle-builds/references/dependencies-and-catalogs.md).
- Enabling security features: `gradle_docs(path="userguide/security.md")`.
