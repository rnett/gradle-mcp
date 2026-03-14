Steps are in order, from next to furthest.

- [May already be fixed by configuration reporting] mcpDependencyReport's progress is still nothing then all at once. It probably needs to report the progress for configuration. The repl tool should do the same
- [Probably fixed] Progress stays at 0 during the configuration phase. Should update for each progrect configured.
- Support for searching a single dependency's sources. Should support explicit G:A, or G:A and look up from project/config/source set, or maybe just group too?
- A "Gradle doctor" like skill with best practices embedded in it (via gradle task), or enhance the expert skill.
- Searching + indexing for the Gradle project's sources?
- More skills in general.
- Investigate how tests with `@Ignore` are reported, compare with IDE.
- Group test results by test suite/class in summaries for better readability.
- Support for test retry reporting and highlighting flaky tests.
- Test with continuous builds