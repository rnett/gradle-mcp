<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: 1a078a060521d87a472a57590d054d8107257f3f4e70da4025bcc3893ad678c4
-->
# Use the Configuration Cache
Use the Configuration Cache to significantly improve build performance by caching the result of the configuration phase and reusing it in subsequent builds.  

## Explanation
The Configuration Cache works by saving the result of the configuration phase. On the next build, if nothing relevant has changed, Gradle skips configuration entirely and loads the cached task graph from disk, jumping straight to task execution.  
This can dramatically reduce build time for large builds, but it's just as valuable for smaller builds where configuration overhead can dominate short iterations. Faster feedback helps developers stay focused, without waiting on redundant configuration work.  
It's important to understand how this differs from the Build Cache. The Build Cache stores the outputs of task execution, while the Configuration Cache stores the configured task graph before execution begins. These are independent mechanisms that solve different problems, but they are designed to work together for optimal performance.  

|---|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|   | The Configuration Cache is the preferred way to execute Gradle builds, but it is not enabled by default. Many existing builds and plugins are not yet fully compatible, and adopting it may involve refactoring of build logic. Enabling it by default could lead to unexpected build failures, so Gradle uses an opt-in adoption model to allow teams to verify compatibility and adopt configuration caching incrementally and safely. |

## Example
### Don't Do This
Configuration Caching is not enabled by default:  
gradle.properties  

```properties
# caching is off by default
# org.gradle.configuration-cache=false
```

### Do This Instead
To enable the Configuration Cache, add the following to your `gradle.properties` file:  
gradle.properties  

```properties
org.gradle.configuration-cache=true
```

When you build your project for the first time, Gradle stores the outcome of the configuration phase, including the task graph, in the Configuration Cache.  

```bash
$ ./gradlew compileJava
```

```text
Configuration cache entry stored.
> Task :processResources NO-SOURCE
> Task :processTestResources NO-SOURCE
> Task :compileJava
> Task :classes
> Task :compileTestJava NO-SOURCE
> Task :testClasses UP-TO-DATE
> Task :test NO-SOURCE
> Task :check UP-TO-DATE
> Task :jar
> Task :assemble
> Task :build

BUILD SUCCESSFUL in 0s
2 actionable tasks: 2 executed
```

On subsequent builds, instead of reconfiguring tasks like `:compileJava`, Gradle loads the task graph from the Configuration Cache and proceeds directly to execution.  

```bash
$ ./gradlew compileJava
```

```text
Configuration cache entry reused.
> Task :processResources NO-SOURCE
> Task :processTestResources NO-SOURCE
> Task :compileJava
> Task :classes
> Task :compileTestJava NO-SOURCE
> Task :testClasses UP-TO-DATE
> Task :test NO-SOURCE
> Task :check UP-TO-DATE
> Task :jar
> Task :assemble
> Task :build

BUILD SUCCESSFUL in 0s
2 actionable tasks: 2 executed
```

## References
* [Enabling The Configuration Cache (Use `gradle_docs(path="userguide/configuration_cache_enabling.md")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
