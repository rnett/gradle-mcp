<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: 782d502474cbf09521930e6b0ac2f5e5f8701bf967878f812fd227423b77386f
-->
# Use Content Filtering with multiple Repositories
When using multiple repositories in a build, use [repository content filtering](https://docs.gradle.org/current/userguide/filtering_repository_content.html#repository-content-filtering) (Use `gradle_docs(path="userguide/filtering_repository_content.html#repository-content-filtering")`.) to ensure that dependencies are resolved from an appropriate repository.  

## Explanation
If your build declares more than one repository, you should declare content filters on these repositories to ensure you search for and obtain dependencies from the correct place.  
Content filtering is necessary if you have a reason to restrict searching for a dependency to a particular repository, and can be a good idea even if acceptable dependency artifacts exist in multiple locations.  
When possible, you should use the [exclusiveContent](https://docs.gradle.org/current/userguide/filtering_repository_content.html#sec:declaring-content-repositories) (Use `gradle_docs(path="userguide/filtering_repository_content.html#sec:declaring-content-repositories")`.) feature to restrict dependencies to a particular known repository.  
Content filtering has three main benefits:  
1. **Performance**, since you only query repositories for dependencies that should actually exist within them

2. **Security**, by avoiding asking potentially every repository for every dependency (even ones they shouldn't contain), you improve resiliency to supply chain attacks by avoiding leaking information about your dependencies to other repositories, or even downloading potentially malicious artifacts

3. **Reliability**, by avoiding searching repositories that contain invalid or incorrect metadata for particular dependencies, which could result in obtaining incorrect transitive dependencies

Repositories will be searched for dependencies that pass their filters in the order they are declared. Often the last repository is declared without any filters in order to serve as a default *fallback repository* that is queried for any dependencies that don't pass the filters present on the other repositories.  

|---|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|   | Carefully consider using content filtering with a fallback repository. This can pose a security risk, so make sure you fully trust the fallback repository. This setup can result in inadvertently (and silently) resolving dependencies from the fallback repository that were intended to come from filtered repositories if the dependencies were not available in those repositories. |

## Example
### Don't Do This
Don't add multiple repositories without content filtering:  
settings.gradle.kts  

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

settings.gradle  

```groovy
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

### Do This Instead
Use content filtering to ensure that the proper repositories are searched first for the expected artifacts:  
settings.gradle.kts  

```kotlin
dependencyResolutionManagement {
    repositories {
        google {
            content {
                // Use this repository for androidx and GMS dependencies
                includeGroupByRegex("androidx.*")
                includeGroup("com.google.gms")
            }
        }
        // Specify the fallback repository last
        mavenCentral()
    }
}
```

settings.gradle  

```groovy
dependencyResolutionManagement {
    repositories {
        google {
            content {
                // Use this repository for androidx and GMS dependencies
                includeGroupByRegex("androidx.*")
                includeGroup("com.google.gms")
            }
        }
        // Specify the fallback repository last
        mavenCentral()
    }
}
```

In many cases, it is better to use exclusive content filtering, as it ensures that dependencies *can only be found in the expected repository*. If they are not present there, they will not be found at all.  
settings.gradle.kts  

```kotlin
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                google()
            }
            filter {
                // Only use this repository, and use this repository only, for androidx and GMS dependencies
                includeGroupByRegex("androidx.*")
                includeGroup("com.google.gms")
            }
        }
        // Specify the fallback repository last
        mavenCentral()
    }
}
```

settings.gradle  

```groovy
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                google()
            }
            filter {
                // Only use this repository, and use this repository only, for androidx and GMS dependencies
                includeGroupByRegex("androidx.*")
                includeGroup("com.google.gms")
            }
        }
        // Specify the fallback repository last
        mavenCentral()
    }
}
```

## References
* [Filtering Repository Content](https://docs.gradle.org/current/userguide/filtering_repository_content.html#repository-content-filtering) (Use `gradle_docs(path="userguide/filtering_repository_content.html#repository-content-filtering")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
