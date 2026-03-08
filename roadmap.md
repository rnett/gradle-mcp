Steps are in order, from next to furthest.

- Ensure Gradle progress shows last started task when waiting, not the last finished. Maybe list of in-progress tasks? Or at least show the number in a kind of "and N other tasks"
- Test progress for test tasks? At least in a paren after (`:proj:test (4/23 tests)`)
- A "Gradle doctor" like skill with best practices embedded in it (via gradle task), or enhance the expert skill.
- More skills in general.
- Investigate how tests with `@Ignore` are reported, compare with IDE.
- Group test results by test suite/class in summaries for better readability.
- Support for test retry reporting and highlighting flaky tests.
- Test with continuous builds