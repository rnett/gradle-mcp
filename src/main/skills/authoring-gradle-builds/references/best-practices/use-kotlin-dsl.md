<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: e635371cdf8bf88c93e5c852f7d6503b96ddbed9e23ecd44fb40d7eca7eb9da7
-->
# Use Kotlin DSL
Prefer the Kotlin DSL (`build.gradle.kts`) over the Groovy DSL (`build.gradle`) when authoring new builds or creating new subprojects in existing builds.  

## Explanation
The Kotlin DSL offers several advantages over the Groovy DSL:  
* **Strict typing**: IDEs provide better auto-completion and navigation with the Kotlin DSL.

* **Improved readability**: Code written in Kotlin is often easier to follow and understand.

* **Single-language stack**: Projects that already use Kotlin for production and test code don't need to introduce Groovy just for the build.

Since Gradle 8.0, [Kotlin DSL is the default](https://blog.gradle.org/kotlin-dsl-is-now-the-default-for-new-gradle-builds) for new builds created with `gradle init`. Android Studio also [defaults to Kotlin DSL](https://developer.android.com/build/migrate-to-kotlin-dsl#timeline).  

## References
* [Kotlin DSL Primer](https://docs.gradle.org/current/userguide/kotlin_dsl.html#kotdsl:kotlin_dsl) (Use `gradle_docs(path="userguide/kotlin_dsl.html#kotdsl:kotlin_dsl")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
