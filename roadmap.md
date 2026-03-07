Steps are in order, from next to furthest.

- Make symbol indexing and archive extraction more performant, especially for the Gradle sources. Look at using lucene, or doing the full text search first and then searching for symbols.
- A tool to read/search the source of one particular library, with the version used in our project. Should hopefully be faster than everything.
- Some way to highlight which tests were running when a build was canceled. An in-progress status?
- Search symbols based on FQNs (new option or using existing option), and "read" packages like directories
- Ensure Gradle progress shows last started task when waiting, not the last finished. Maybe list of in-progress tasks? Or at least show the number in a kind of "and N other tasks"
- Test progress for test tasks?
- More skills in general.
- Test with continuous builds