- Why are all init scripts being used for builds (e.g. the repl one for non repl builds)
- Repl init script had errors on mac.
- Errors int he daemon was stopped (cached connection?) should be recoverable
- Repl.
- Repl supports android source sets and android plugins (<9 and >9)
- Consider how logging in repl should be handled for my code vs user code.
- [Requires Gradle 9.4] Capture test metadata events. Put k/v pairs in the test details, and make files accessible. Probably add a "includeMetadataFiles" glob to the details. Want to include each file as its own response for image support.
  This is Gradle 9.4+
  only, but the events are there already.
- Test with continuous builds
- Compose preview task, see src/docs/kotlin-compose-preview.md