<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: 08811a6512acdc14aeff9a63b7be888b71a21e104650ba9f09f0bd083df865c1
-->
# Do Not Use Internal APIs
Do not use APIs from a package where any segment of the package is `internal`, or types that have `Internal` or `Impl` as a suffix in the name.  

## Explanation
Using internal APIs is inherently risky and can cause significant problems during upgrades. Gradle and many plugins (such as Android Gradle Plugin and Kotlin Gradle Plugin) treat these internal APIs as subject to unannounced breaking changes during any new Gradle release, even during minor releases. There have been numerous cases where even highly experienced plugin developers have been bitten by their usage of such APIs leading to unexpected breakages for their users.  
If you require specific functionality that is missing, it's best to submit a feature request. As a temporary workaround consider copying the necessary code into your own codebase and extending a Gradle public type with your own custom implementation using the copied code.  

## Example
### Don't Do This
build.gradle.kts  

```kotlin
import org.gradle.api.internal.attributes.AttributeContainerInternal

configurations.create("bad") {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named<Category>(Category.LIBRARY))
    }
    val badMap = (attributes as AttributeContainerInternal).asMap() (1)
    logger.warn("Bad map")
    badMap.forEach { (key, value) ->
        logger.warn("$key -> $value")
    }
}
```

build.gradle  

```groovy
import org.gradle.api.internal.attributes.AttributeContainerInternal

configurations.create("bad") {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
    }
    def badMap = (attributes as AttributeContainerInternal).asMap() (1)
    logger.warn("Bad map")
    badMap.each {
        logger.warn("${it.key} -> ${it.value}")
    }
}
```

|-------|----------------------------------------------------------------------------------------------------------------|
| **1** | Casting to `AttributeContainerInternal` and using `toMap()` should be avoided as it relies on an internal API. |

### Do This Instead
build.gradle.kts  

```kotlin
configurations.create("good") {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named<Category>(Category.LIBRARY))
    }
    val goodMap = attributes.keySet().associate { (1)
        Attribute.of(it.name, it.type) to attributes.getAttribute(it)
    }
    logger.warn("Good map")
    goodMap.forEach { (key, value) ->
        logger.warn("$key -> $value")
    }
}
```

build.gradle  

```groovy
configurations.create("good") {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
    }
    def goodMap = attributes.keySet().collectEntries {
        [Attribute.of(it.name, it.type), attributes.getAttribute(it as Attribute<Object>)]
    }
    logger.warn("Good map")
    goodMap.each {
        logger.warn("$it.key -> $it.value")
    }
}
```

|-------|---------------------------------------------------------------------------------------------|
| **1** | Implementing your own version of `toMap()` that only uses public APIs is a lot more robust. |

## References
* [Gradle API

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
