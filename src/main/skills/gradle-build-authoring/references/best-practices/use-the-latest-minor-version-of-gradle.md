# Use the Latest Minor Version of Gradle
Stay on the latest minor version of the major Gradle release you're using, and regularly update your plugins to the latest compatible versions.  

## Explanation
Gradle follows a fairly predictable, time-based release cadence. Only the latest minor version of the current and previous major release is actively supported.  
We recommend the following strategy:  
* Try upgrading directly to the latest minor version of your current major Gradle release.

* If that fails, upgrade one minor version at a time to isolate regressions or compatibility issues.

Each new minor version includes:  
* Performance and stability improvements.

* Deprecation warnings that help you prepare for the next major release.

* Fixes for known bugs and security vulnerabilities.

Use the `wrapper` task to update your project:  

```bash
./gradlew :wrapper --gradle-version <version>
```

You can also install the latest Gradle versions easily using tools like [SDKMAN!](https://sdkman.io/) or [Homebrew](https://brew.sh/), depending on your platform.  

## Plugin Compatibility
Always use the latest compatible version of each plugin:  
* Upgrade Gradle before plugins.

* Test plugin compatibility using [shadow jobs](https://slack.engineering/shadow-jobs/).

* Consult changelogs when updating.

Subscribe to the Gradle [newsletter](https://newsletter.gradle.org/) to stay informed about new Gradle releases, features, and plugins.  

## References
* [Upgrade Guide](https://docs.gradle.org/current/userguide/upgrading_version_9.html#upgrading_version_9) (Use `gradle_docs(path="userguide/upgrading_version_9.html#upgrading_version_9")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
