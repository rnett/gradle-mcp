# Best Practices for Testing

## Test your custom Task and Plugins with TestKit
You should test any custom tasks or plugins you create using [`Gradle TestKit`](https://docs.gradle.org/current/userguide/test_kit.html#test_kit) (Use `gradle_docs(path="userguide/test_kit.html#test_kit")`.).  

### Explanation
Gradle's flexibility supports a natural evolution of custom types as they mature.  
Creating new tasks and plugins directly in a `build.gradle(.kts)` file is a great way to prototype new functionality. As that functionality stabilizes, you should extract these definitions into `buildSrc` or a standalone plugin project for better reusability and maintainability. Once types exist outside of a single build file, you can easily write functional tests for them using TestKit.  
A mature build should include functional tests for its custom types to ensure they behave as expected.  

### Example
#### Don't Do This
The following build defines a custom task and a custom plugin that applies it within the `build.gradle(.kts)` file. The plugin adds multiple custom tasks that print a greeting using properties defined in a custom extension.  
This is a common pattern for prototyping new functionality, but it lacks tests to verify the behavior of the custom types. The only way to verify that the task and plugin work as intended is to run the build manually and inspect the output.  
build.gradle.kts  

```kotlin
import java.time.Instant

interface MyExtension {
    val firstName: Property<String>
    val lastName: Property<String>
}

var greeter = "Hello"

@CacheableTask (1)
abstract class MyTask: DefaultTask() {
    @get:Input
    abstract val firstName: Property<String>
    @get:Input
    abstract val lastName: Property<String>
    @get:Input
    abstract val greeting: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private final val today = Instant.now() (2)

    @TaskAction
    fun run() {
        val output = outputFile.asFile.get()
        val result = "${greeting.get()}, ${firstName.get()} ${lastName.get()}, it's currently\n$today"
        println(result)
        output.writeText(result)
    }
}

abstract class MyPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("myExtension", MyExtension::class.java)

        project.tasks.register<MyTask>("task1") {
            outputFile.convention(project.layout.buildDirectory.file("output1.txt"))
        }

        project.tasks.register<MyTask>("task2") {
            outputFile.convention(project.layout.buildDirectory.file("output2.txt"))
        }

        project.tasks.withType<MyTask>().configureEach {
            group = "Custom Tasks"
            firstName.convention(extension.firstName)
            lastName.convention(extension.firstName) (3)
            greeting.convention("Hi")
        }
    }
}

apply<MyPlugin>()

configure<MyExtension> {
    firstName = "John"
    lastName = "Smith"
}

tasks.named<MyTask>("task2") {
    greeter = "Bonjour" (4)
}
```

build.gradle  

```groovy
import java.time.Instant

interface MyExtension {
    Property<String> getFirstName()
    Property<String> getLastName()
}

def greeter = "Hello"

@CacheableTask (1)
abstract class MyTask extends DefaultTask {
    @Input
    abstract Property<String> getFirstName()
    @Input
    abstract Property<String> getLastName()
    @Input
    abstract Property<String> getGreeting()

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    private final Instant today = Instant.now() (2)

    @TaskAction
    void run() {
        def output = outputFile.asFile.get()
        def result = "${greeting.get()}, ${firstName.get()} ${lastName.get()}, it's currently\n$today"
        println result
        output.text = result
    }
}

abstract class MyPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        def extension = project.extensions.create("myExtension", MyExtension)

        project.tasks.register("task1", MyTask) { task ->
            task.outputFile.convention(project.layout.buildDirectory.file("output1.txt"))
        }

        project.tasks.register("task2", MyTask) { task ->
            task.outputFile.convention(project.layout.buildDirectory.file("output2.txt"))
        }

        project.tasks.withType(MyTask).configureEach { task ->
            task.group = "Custom Tasks"
            task.firstName.convention(extension.firstName)
            task.lastName.convention(extension.firstName) (3)
            task.greeting.convention("Hi")
        }
    }
}

apply plugin: MyPlugin

myExtension {
    firstName = "John"
    lastName = "Smith"
}

tasks.named("task2", MyTask) {
    greeter = "Bonjour" (4)
}
```

In this case, there are several problems:  

|-------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **The Task is declared as cacheable**: However, the task's output differs depending on when it is run, so it should not be cached.                                                  |
| **2** | **The current time is used as an undeclared input**: Inputs to a task must be explicitly declared using the appropriate annotations, otherwise Gradle cannot track changes to them. |
| **3** | **Error in task property wiring** : The `lastName` property is linked to the `firstName` property on the extension, which is likely a mistake.                                      |
| **4** | **The wrong variable is assigned during task configuration** : The `greeter` variable from the buildscript is mistakenly assigned instead of the task's `greeting` property.        |

#### Do This Instead
In this updated version of the build, the custom types are defined in an included `build-logic` [composite build](https://docs.gradle.org/current/userguide/best_practices_structuring_builds.html#favor_composite_builds) (Use `gradle_docs(path="userguide/best_practices_structuring_builds.html#favor_composite_builds")`.), which now includes a basic set of functional tests using Gradle TestKit.  
While these custom types are written in Java for demonstration purposes, they could just as easily be implemented in Groovy or Kotlin. Because they reside in a separate, complete Gradle build, they can be thoroughly tested using TestKit.  

```kotlin
├── build-logic/
│   ├── src
│   │   ├── main
│   │   │   └── java
│   │   │       └── org
│   │   │           └── example
│   │   │               └── MyExtension.java
│   │   │               └── MyPlugin.java
│   │   │               └── MyTask.java
│   │   └── functionalTest
│   │       └── java
│   │           └── org
│   │               └── example
│   │                   └── MyPluginFunctionalTest.java
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── settings.gradle.kts
└── build.gradle.kts
```

```groovy
├── build-logic/
│   ├── src
│   │   ├── main
│   │   │   └── java
│   │   │       └── org
│   │   │           └── example
│   │   │               └── MyExtension.java
│   │   │               └── MyPlugin.java
│   │   │               └── MyTask.java
│   │   └── functionalTest
│   │       └── java
│   │           └── org
│   │               └── example
│   │                   └── MyPluginFunctionalTest.java
│   ├── build.gradle
│   └── settings.gradle
├── settings.gradle
└── build.gradle
```

We've corrected the issues with the plugin and task from the previous example:  
MyTask.java  

```java
package org.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.io.IOException;

@CacheableTask
public abstract class MyTask extends DefaultTask {
    @Input
    public abstract Property<String> getFirstName();
    @Input
    public abstract Property<String> getLastName();
    @Input
    public abstract Property<String> getGreeting();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input (1)
    public abstract Property<Instant> getToday();

    @TaskAction
    public void run() throws IOException {
        File output = getOutputFile().getAsFile().get();
        String result = String.format("%s, %s %s, it's currently\n%s", getGreeting().get(), getFirstName().get(), getLastName().get(), getToday().get());
        System.out.println(result);
        Files.writeString(output.toPath(), result);
    }
}
```

|-------|--------------------------------------------------------------------------------------------------------------------------------|
| **1** | **`Today` is a proper `@Input`** : This allows Gradle's `UP-TO-DATE` checking to properly consider it when rerunning the task. |

MyPlugin.java  

```java
package org.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import java.time.Instant;

public abstract class MyPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        MyExtension extension = project.getExtensions().create("myExtension", MyExtension.class);

        project.getTasks().register("task1", MyTask.class, task -> {
            task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("output1.txt"));
        });

        project.getTasks().register("task2", MyTask.class, task -> {
            task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("output2.txt"));
        });

        project.getTasks().withType(MyTask.class).configureEach(task -> {
            task.setGroup("Custom Tasks");
            task.getFirstName().convention(extension.getFirstName());
            task.getLastName().convention(extension.getLastName()); (1)
            task.getGreeting().convention("Hi");
            task.getToday().convention(Instant.now()); (2)
        });
    }
}
```

|-------|----------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Corrected typo in assignment**: The last name convention set to the value of the last name from the extension.                 |
| **2** | **`Today` is set to the same value on all tasks** : Only calling `Instant.now()` once, rather than every time a task is created. |

Writing and running the tests as described below helped identify the bugs in the plugin implementation.  
We defined a functional test suite within the `build-logic` project. Because TestKit tests tend to be slower and more complex than unit tests, they are typically kept separate. Locating these tests in a dedicated `functionalTest` suite also clarifies their purpose for other developers.  
build.gradle.kts  

```kotlin
val functionalTest = testing.suites.register("functionalTest", JvmTestSuite::class) { (1)
    useJUnitJupiter()
    dependencies {
        implementation("commons-io:commons-io:2.16.1")
        implementation(project())
        implementation(gradleTestKit()) (2)
    }
}

tasks.check {
    dependsOn(functionalTest)
}

gradlePlugin {
    plugins {
        register("org.example.myplugin") {
            implementationClass = "org.example.MyPlugin"
        }
    }
    testSourceSets(functionalTest.get().sources) (3)
}
```

build.gradle  

```groovy
def functionalTest = testing.suites.register("functionalTest", JvmTestSuite) { (1)
    useJUnitJupiter()
    dependencies {
        implementation("commons-io:commons-io:2.16.1")
        implementation(project())
        implementation(gradleTestKit()) (2)
    }
}

tasks.check {
    dependsOn(functionalTest)
}

gradlePlugin {
    plugins {
        register("org.example.myplugin") {
            implementationClass = "org.example.MyPlugin"
        }
    }

    testSourceSets functionalTest.get().sources (3)
}
```

|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Define the new test suite** : Creates a `functionalTest` [JVM Test Suite](https://docs.gradle.org/current/userguide/jvm_test_suite_plugin.html#jvm_test_suite_plugin) (Use `gradle_docs(path="userguide/jvm_test_suite_plugin.html#jvm_test_suite_plugin")`.).                                        |
| **2** | **Add TestKit dependency** : This contains the `GradleRunner` class we'll use to write tests.                                                                         |
| **3** | **Make the `java-gradle-plugin` aware of the new test suite**: Now the usable plugin source from the project's main production code will be available to these tests. |

With this setup in place, we can write functional tests for our plugin in the `build-logic/src/functionalTest/java` directory. You can run `./gradlew :build-logic:functionalTest` or `./gradlew :build-logic:check` from the root project directory to execute these tests.  

|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|   | By default, tests within the included `build-logic` build are not executed when you run tests in the root project. Because the root project only requires the build artifacts from `build-logic`, Gradle will build the project without running its internal tests. To run them, you must explicitly invoke the tasks: `./gradlew :build-logic:check`. |

In the functional test class, we use TestKit to initialize a temporary Gradle project, apply our plugin, and verify that it behaves as expected.  
MyPluginFunctionalTest.java  

```java
package org.example;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MyPluginFunctionalTest {
    @TempDir File testProjectDir;
    private File settingsFile;
    private File buildFile;
    private File cacheDir = new File("build-cache");

    @BeforeEach
    public void setup() throws IOException {
        settingsFile = new File(testProjectDir, "settings.gradle");
        Files.writeString(settingsFile.toPath(), "rootProject.name = 'test-project'");

        buildFile = new File(testProjectDir, "build.gradle");
    }

    @Test
    public void testTaskRegistration() throws IOException { (1)
        String buildFileContent = """
            plugins {
                id("org.example.myplugin")
            }
        """;
        Files.writeString(buildFile.toPath(), buildFileContent);

        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .forwardOutput()
            .withArguments("tasks", "--all")
            .build();

        assertContainsIgnoringEol("""
            Custom Tasks tasks
            task1
            task2
            """,
            result.getOutput()
        );
    }

    @Test
    public void testTaskExecution() throws IOException { (2)
        File outputFile = new File(testProjectDir, "build/output.txt");

        String buildFileContent = """
            plugins {
                id("org.example.myplugin")
            }

            myExtension {
                firstName = "John"
                lastName = "Smith"
            }

            tasks.task1 {
                outputFile = project.layout.buildDirectory.file("output.txt")
            }
        """;
        Files.writeString(buildFile.toPath(), buildFileContent);

        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("task1")
            .build();

        String actual = Files.readString(outputFile.toPath());
        assertTrue(actual.startsWith("Hi, John Smith, it's currently"));
    }

    @Test
    public void testTaskDeterminism() throws IOException { (3)
        File outputFile = new File(testProjectDir, "build/output.txt");

        String buildFileContent = """
            plugins {
                id("org.example.myplugin")
            }

            myExtension {
                firstName = "John"
                lastName = "Smith"
            }

            tasks.task1 {
                outputFile = project.layout.buildDirectory.file("output.txt")
                today = ZonedDateTime.parse("2026-01-12T16:00:00-05:00").toInstant()
            }
        """;
        Files.writeString(buildFile.toPath(), buildFileContent);

        GradleRunner runner = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath();

        runner.withArguments("task1").build();
        String output1 = Files.readString(outputFile.toPath());

        runner.withArguments("task1", "--rerun-tasks").build();
        String output2 = Files.readString(outputFile.toPath());

        assertEquals(output1, output2);
    }

    @Test
    public void testTaskCacheability() throws IOException { (4)
        String buildFileContent = """
            plugins {
                id("org.example.myplugin")
            }

            myExtension {
                firstName = "John"
                lastName = "Smith"
            }

            tasks.task1 {
                outputFile = project.layout.buildDirectory.file("output.txt")
                today = ZonedDateTime.parse("2026-01-12T16:00:00-05:00").toInstant()
            }
        """;
        Files.writeString(buildFile.toPath(), buildFileContent);

        GradleRunner runner = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .forwardOutput();

        BuildResult result = runner.withArguments("--build-cache", "task1").build();
        assertEquals(SUCCESS, result.task(":task1").getOutcome());

        FileUtils.deleteDirectory(new File(testProjectDir, "build"));

        result = runner.withArguments("--build-cache", "task1").build();
        assertEquals(FROM_CACHE, result.task(":task1").getOutcome());
    }

    private static void assertContainsIgnoringEol(String expected, String actual) {
        assertTrue(normalizeEol(actual).contains(normalizeEol(expected)));
    }

    private static String normalizeEol(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }
}
```

Looking at the example tests here, you can see various techniques used to verify the plugin's behavior against actual, ad-hoc Gradle builds defined within the tests:  

|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **testTaskRegistration** : This test runs the `tasks` report and verifies the output.                                                                                 |
| **2** | **testTaskExecution**: This test runs the custom task and verifies its output file.                                                                                   |
| **3** | **testTaskDeterminism**: This test runs the build twice - forcing tasks to rerun the second time - to ensure the output is identical, which is necessary for caching. |
| **4** | **testTaskCacheability**: This test checks that when the task is run twice in a row, the second run is loaded from cache.                                             |

These examples only scratch the surface of what you can achieve with TestKit. For instance, a comprehensive test for cacheability should verify that changes to inputs correctly trigger re-execution and include tests for [relocatability](https://docs.gradle.org/current/userguide/build_cache_debugging.html#caching_relocation_test) (Use `gradle_docs(path="userguide/build_cache_debugging.html#caching_relocation_test")`.).  
You can find more information in the [Gradle TestKit documentation](https://docs.gradle.org/current/userguide/test_kit.html#test_kit) (Use `gradle_docs(path="userguide/test_kit.html#test_kit")`.).  

### References
* [`Gradle TestKit`](https://docs.gradle.org/current/userguide/test_kit.html#test_kit) (Use `gradle_docs(path="userguide/test_kit.html#test_kit")`.)

* [`Testing Binary Plugins`](https://docs.gradle.org/current/userguide/testing_binary_plugin_advanced.html#test_binary_plugins_advanced) (Use `gradle_docs(path="userguide/testing_binary_plugin_advanced.html#test_binary_plugins_advanced")`.)

### Tags
[`#plugins`](https://docs.gradle.org/current/userguide/tags_reference.html#tag:plugins) (Use `gradle_docs(path="userguide/tags_reference.html#tag:plugins")`.), [`#testing`](https://docs.gradle.org/current/userguide/tags_reference.html#tag:testing) (Use `gradle_docs(path="userguide/tags_reference.html#tag:testing")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
