<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: d928885d2bffa1c9f678d663380a3ee7b22f2c9c6f4a7537405697243b0775d0
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
* [Kotlin DSL Primer (Use `gradle_docs(path="userguide/kotlin_dsl.md")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
