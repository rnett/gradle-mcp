# Use the Build Cache
Use the Build Cache to save time by reusing outputs produced by previous builds.  

## Explanation
The Build Cache avoids re-executing tasks when their inputs haven't changed by reusing outputs from previous builds.  
This prevents redundant work. If the inputs are the same, the outputs will be too, resulting in faster, more efficient builds.  

## Example
### Don't Do This
Build caching is disabled by default:  
gradle.properties  

```properties
# caching is off by default
# org.gradle.caching=false
```

### Do This Instead
To enable the Build Cache, add the following to your `gradle.properties` file:  
gradle.properties  

```properties
org.gradle.caching=true
```

When you build your project for the first time, Gradle populates the cache with the outputs of tasks like compilation.  
Even if you run `./gradlew clean` to delete the build directory, Gradle can reuse cached outputs in subsequent builds.  

```bash
$ ./gradlew clean
```

```text
:clean
BUILD SUCCESSFUL
```

On subsequent builds, instead of executing the `:compileJava` task again, the outputs of the task will be loaded from the Build Cache:  

```bash
$ ./gradlew compileJava
```

```text
> Task :compileJava FROM-CACHE

BUILD SUCCESSFUL in 0s
1 actionable task: 1 from cache
```

## References
* [Build Cache Overview (Use `gradle_docs(path="userguide/build_cache.md")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
