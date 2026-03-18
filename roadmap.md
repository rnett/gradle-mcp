Steps are in order, from next to furthest.

- Better/working JVM version detection for running Gradle - currently always uses the current version, which doesn't always work. Dokka is an example of where it fails. Probably need a JDK version arg that uses toolchain provisioning.
- Single dependency download works for deps that aren't in your project
- Option to include project sources in source index/search
- Expose absolute paths of downloaded deps for things like rg or ast-grep
- A "Gradle doctor" like skill with best practices embedded in it (via gradle task), or enhance the expert skill.
- Dedicated "render this compose preview" tool that takes the file+function name or line number
- More skills in general.
- Investigate how tests with `@Ignore` are reported, compare with IDE.
- Group test results by test suite/class in summaries for better readability.
- Support for test retry reporting and highlighting flaky tests.
- Kotlin version independent REPL
- Test with continuous builds