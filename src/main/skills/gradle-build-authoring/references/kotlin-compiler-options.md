# Kotlin Compiler Options

Managing compiler options in Gradle requires matching the DSL to the specific target platform and Kotlin version.

## Global Compiler Options

The `compilerOptions` block is the modern way to configure the Kotlin compiler, replacing the older `kotlinOptions` block.

```kotlin
kotlin {
    compilerOptions {
        // target bytecode version
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        
        // Additional flags passed directly to kotlinc
        freeCompilerArgs.add("-Xopt-in=kotlin.RequiresOptIn")
        
        // Enable specific language features or warnings
        allWarningsAsErrors.set(true)
    }
}
```

## Per-Source-Set Configuration

In Multiplatform (KMP) or complex projects, you may need different options for different source sets.

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            languageSettings {
                // Common settings here
            }
        }
        
        val jvmMain by getting {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }
    }
}
```

## Common Configuration Patterns

### Handling Opt-ins

For experimental features, use the `freeCompilerArgs` to add `-Xopt-in` flags:

```kotlin
compilerOptions {
    freeCompilerArgs.add("-Xopt-in=kotlin.experimental.ExperimentalKmpApi")
}
```

### Configuring JVM Target Consistently

To avoid "class file has wrong version" errors, ensure the `jvmTarget` in the Kotlin block matches the `targetCompatibility` in the Java block:

```kotlin
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
```

## KMP Specifics

In KMP, some options are target-specific. For example, `jvmTarget` is only valid for JVM targets. For Native targets (iOS, macOS), you configure the `binaries` block or specific target compiler options.
