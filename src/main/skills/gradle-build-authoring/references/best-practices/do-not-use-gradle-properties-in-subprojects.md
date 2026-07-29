# Do not use `gradle.properties` in subprojects
Do not place a `gradle.properties` file inside subprojects to configure your build.  

## Explanation
Gradle allows `gradle.properties` files in both the root project and subprojects, but support for subproject properties is inconsistent. Gradle itself and many popular plugins (such as the Android Gradle Plugin and Kotlin Gradle Plugin) do not reliably handle this pattern.  
Using subproject `gradle.properties` files also makes it harder to understand and debug your build. Property values may be scattered across multiple locations, overridden in unexpected ways, or difficult to trace back to their source.  
If you need to set properties for a **single subproject** , define them directly in that subproject's `build.gradle(.kts)`. If you need to apply properties across **multiple subprojects** , extract the configuration into a [convention plugin](https://docs.gradle.org/current/userguide/plugins.html#sec:convention_plugins) (Use `gradle_docs(path="userguide/plugins.html#sec:convention_plugins")`.).  

## Example
### Don't Do This
```kotlin
├── app
│   ├── ⋮
│   ├── build.gradle.kts
│   └── gradle.properties
├── utilities
│   ├── ⋮
│   ├── build.gradle.kts
│   └── gradle.properties
└── settings.gradle.kts
```

```groovy
├── app
│   ├── ⋮
│   ├── build.gradle
│   └── gradle.properties
├── utilities
│   ├── ⋮
│   ├── build.gradle
│   └── gradle.properties
└── settings.gradle
```

gradle.properties  

```properties
# This file is located in /app
propertyA=fixedValue
propertyB=someValue
```

build.gradle.kts  

```kotlin
// This file is located in /app
tasks.register("printProperties") { (1)
    val propA = project.findProperty("propertyA") (2)
    val propB = project.findProperty("propertyB")

    doLast {
        println("propertyA in app: $propA")
        println("propertyB in app: $propB")
    }
}
```

build.gradle  

```groovy
// This file is located in /app
tasks.register("printProperties") { (1)
    def propA = project.findProperty("propertyA") (2)
    def propB = project.findProperty("propertyB")

    doLast {
        println "propertyA in app: $propA"
        println "propertyB in app: $propB"
    }
}
```

gradle.properties  

```properties
# This file is located in /util
propertyA=fixedValue
propertyB=otherValue
```

build.gradle.kts  

```kotlin
// This file is located in /util
tasks.register("printProperties") {
    val propA = project.findProperty("propertyA")
    val propB = project.findProperty("propertyB") (3)

    doLast {
        println("propertyA in util: $propA")
        println("propertyB in util: $propB")
    }
}
```

build.gradle  

```groovy
// This file is located in /util
tasks.register("printProperties") {
    def propA = project.findProperty("propertyA")
    def propB = project.findProperty("propertyB") (3)

    doLast {
        println "propertyA in util: $propA"
        println "propertyB in util: $propB"
    }
}
```

|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | Register a task that uses the value of properties in each subproject.                                                                               |
| **2** | The task reads properties, which are supplied by the project-local `app/gradle.properties` file. `propertyA` does **not** vary between subprojects. |
| **3** | 'util's print task reads the properties which are supplied by `util/gradle.properties`. `propertyB` **varies** between subprojects.                 |

This structure requires duplicating properties that are shared between subprojects and is not guaranteed to remain supported.  

### Do This Instead

```kotlin
├── buildSrc
│   └──  ⋮
├── app
│   ├── ⋮
│   └── build.gradle.kts
├── utilities
│   ├── ⋮
│   └── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

```groovy
├── buildSrc
│   └──  ⋮
├── app
│   ├── ⋮
│   └──  build.gradle
├── utilities
│   ├── ⋮
│   └── build.gradle
├── settings.gradle
└── gradle.properties
```

gradle.properties  

```properties
# This file is located in the root of the build
propertyA=fixedValue
propertyB=someValue
```

ProjectProperties.kt  

```kotlin
import org.gradle.api.provider.Property

interface ProjectProperties { (1)
    val propertyA: Property<String>
    val propertyB: Property<String>
}
```

ProjectProperties.groovy  

```groovy
import org.gradle.api.provider.Property

interface ProjectProperties { (1)
    Property<String> getPropertyA()
    Property<String> getPropertyB()
}
```

project-properties.gradle.kts  

```kotlin
extensions.create<ProjectProperties>("myProperties") (2)

tasks.register("printProperties") { (3)
    val myProperties = project.extensions.getByName("myProperties") as ProjectProperties
    val projectName = project.name

    doLast {
        println("propertyA in ${projectName}: ${myProperties.propertyA.get()}")
        println("propertyB in ${projectName}: ${myProperties.propertyB.get()}")
    }
}
```

project-properties.gradle  

```groovy
extensions.create("myProperties", ProjectProperties) (2)

tasks.register("printProperties") { (3)
    def myProperties = project.extensions.getByName("myProperties") as ProjectProperties
    def projectName = project.name

    doLast {
        println("propertyA in ${projectName}: ${myProperties.propertyA.get()}")
        println("propertyB in ${projectName}: ${myProperties.propertyB.get()}")
    }
}
```

build.gradle.kts  

```kotlin
// This file is located in /app
plugins { (4)
    id("project-properties")
}

myProperties { (5)
    propertyA = providers.gradleProperty("propertyA")
    propertyB = providers.gradleProperty("propertyB")
}
```

build.gradle  

```groovy
// This file is located in /app
plugins { (4)
    id "project-properties"
}

myProperties { (5)
    propertyA = providers.gradleProperty("propertyA")
    propertyB = providers.gradleProperty("propertyB")
}
```

build.gradle.kts  

```kotlin
// This file is located in /util
plugins {
    id("project-properties")
}

myProperties {
    propertyA = providers.gradleProperty("propertyA")
    propertyB = "otherValue" (6)
}
```

build.gradle  

```groovy
// This file is located in /util
plugins {
    id "project-properties"
}

myProperties {
    propertyA = providers.gradleProperty("propertyA")
    propertyB = "otherValue" (6)
}
```

|-------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | Define a simple extension type in `buildSrc` to hold property values.                                                                                                                                                         |
| **2** | Register that property in a convention plugin.                                                                                                                                                                                |
| **3** | Register tasks using property values in the convention plugin.                                                                                                                                                                |
| **4** | Apply the convention plugin in each subproject.                                                                                                                                                                               |
| **5** | Set the extension's property values in each subproject's build script. This uses the values defined in the root `gradle.properties` file. The task reads values from the extension, not directly from the project properties. |
| **6** | When values need to vary between subprojects, they can be set directly on the extension.                                                                                                                                      |

This structure uses an extension type to hold values, allowing properties to be strongly typed, and for property values and operations on properties to be defined in a single location. Overriding values per subproject remains straightforward.  

## References
* [Gradle properties](https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties) (Use `gradle_docs(path="userguide/build_environment.html#sec:gradle_configuration_properties")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
