- Capture test metadata events. Put k/v pairs in the test details, and make files accessible. Probably add a "includeMetadataFiles" glob to the details. Want to include each file as its own response for image support. This is Gradle 9.4+
  only, but the events are there already.
- Add an env var for the project root, that supports ., .., ~, etc. Test with Junie to see if I can use `.`.
- Test with continuous builds