## 1. Update Default Paths in Code

- [x] 1.1 In `src/main/kotlin/dev/rnett/gradle/mcp/Utils.kt`, change the fallback default in `GradleMcpEnvironment.fromEnv()` from `${user.home}/.gradle-mcp` to `${user.home}/.mcps/rnett-gradle-mcp`
- [x] 1.2 In `src/main/resources/logback.xml`, change both occurrences of `${user.home}/.gradle-mcp/logs` to `${user.home}/.mcps/rnett-gradle-mcp/logs`

## 2. Update Documentation

- [x] 2.1 In `README.md`, replace all references to `~/.gradle-mcp` with `~/.mcps/rnett-gradle-mcp`
- [x] 2.2 In `docs/index.md`, replace all references to `~/.gradle-mcp` with `~/.mcps/rnett-gradle-mcp`
- [x] 2.3 In `CONTRIBUTING.md`, replace all references to `~/.gradle-mcp` with `~/.mcps/rnett-gradle-mcp`

## 3. Verify

- [x] 3.1 Run `./gradlew test` and confirm all tests pass
