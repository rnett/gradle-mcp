- Repl.
- Repl supports android source sets and android plugins (<9 and >9)
- [Requires Gradle 9.4] Capture test metadata events. Put k/v pairs in the test details, and make files accessible. Probably add a "includeMetadataFiles" glob to the details. Want to include each file as its own response for image support.
  This is Gradle 9.4+
  only, but the events are there already.
- Test with continuous builds
- Compose preview task, see src/docs/kotlin-compose-preview.md