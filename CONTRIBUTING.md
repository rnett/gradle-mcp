# Contributing to Gradle MCP

Thank you for your interest in contributing! This guide covers the human contributor workflow. Agent-specific conventions and architectural constraints are documented in [AGENTS.md](./AGENTS.md) which may also be a valuable read for Humans.

## Reporting Bugs & Suggesting Features

Open a [GitHub Issue](https://github.com/rnett/gradle-mcp/issues) for bug reports, feature requests, or questions.
For features, describe the use case and the problem it solves.
For bugs, please include at least:

* The agent or caller involved (version too!)
* The Gradle version used
* OS
* A [Develocity Build Scan](https://docs.gradle.org/current/userguide/build_scans.html) of the failure, if at all possible
* The exact tool command ran, if applicable
* Recent/related logs from `~/.mcps/rnett-gradle-mcp/logs` - MAY CONTAIN SECRETS: check them or send them to me confidentially
* Ask your agent to prepare a bug report with all relevant information

If you plan to implement something substantial, please open an issue first to discuss the approach before investing time in a PR.

## Development setup

None. Gradle and the required JDKs will be automatically provisioned.

## Workflow

1. **Fork** the repository and create a feature branch from `main`.
2. **Make your changes** following the code style and conventions below.
3. **Run the quality check locally** before opening a PR:
   ```shell
   ./gradlew check
   ```
   This runs all tests, linting, and verification tasks. PRs that fail `check` will not be reviewed.
4. **Open a Pull Request** against `main` with a clear description of what changed and why.

## Code Style & Conventions

Write idiomatic Kotlin, using IntelliJ's builtin formatter.
Prefer Kotlin standard library types over Java alternatives, and coroutines over blocking concurrency.
All dependency versions go in `gradle/libs.versions.toml`.

## Project Structure

| Path               | Purpose                                                                                                         |
|--------------------|-----------------------------------------------------------------------------------------------------------------|
| `src/main/kotlin/` | MCP server and tool implementations                                                                             |
| `src/test/kotlin/` | Unit and integration tests                                                                                      |
| `repl-worker/`     | Subprocess that executes Kotlin snippets via the Kotlin scripting engine. Used for classpath and JVM isolation. |
| `repl-shared/`     | Data classes and protocol types shared between the main process and `repl-worker`                               |
| `skills/`          | Agent skills                                                                                                    |
| `docs/`            | Human-facing documentation                                                                                      |

## Skills

Skills in `skills/` are standalone agent skills distributed to end users. When modifying a tool, update any referencing skills to stay consistent. After any tool metadata or description change, run:

```shell
./gradlew :updateToolsList
```

See [docs/skills.md](./docs/skills.md) for the full list of skills.

## Tests

- **Fast tests**: `./gradlew test` — `check` is still required for all changes, but this provides a faster inner loop.
- **Full check**: `./gradlew check` — required before opening a PR.

Slow tests belong in `integrationTest`, not `test`. This is a performance concern, not an architectural one.

## Submitting Changes

- Keep PRs focused — one issue or concern per PR.
- Include a clear description of the problem being solved (if not explained in a linked issue) and the approach taken.
