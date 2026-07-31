<!--
class: authored-local
skill: authoring-gradle-builds
-->
# JDK Toolchains

Gradle toolchains decouple the JDK used to run Gradle from the JDK used to compile and test the project code. This ensures build reproducibility across different environments.

## Configuration

Define the required Java version in the `java` block of your build script.

```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        // Optional: specify a vendor, e.g., Azula, AdoptOpenJDK, Amazon.com, etc.
        // vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}
```

## Auto-Provisioning

If Gradle cannot find a local JDK that matches the toolchain requirement, it can automatically download and install one.

### Enabling Auto-Provisioning

Auto-provisioning is typically enabled by default, but can be configured in `gradle.properties`:

```properties
# Enable automatic downloading of missing toolchains
org.gradle.java.installations.auto-download=true

# Optional: Specify the directory where toolchains are stored
org.gradle.java.installations.paths=C:/jdk-storage
```

## Foojay Toolchains Resolver

The Foojay Toolchains plugin provides a standardized way to resolve and download JDKs from multiple vendors via the Foojay Disco API.

### Setup

Add the plugin to your `settings.gradle.kts`:

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
```

With this plugin, Gradle will use the Foojay registry to find the best JDK match for the specified `languageVersion` and `vendor`.

## Vendor-Specific Toolchains

You can narrow the search for a JDK by specifying a vendor.

```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.AZUL)
    }
}
```

Common `JvmVendorSpec` values include `ADOPTIUM`, `AMAZON`, `AZUL`, `ORACLE`, and `ALIBABA`.

## Kotlin Multiplatform (KMP) Toolchains

In KMP projects, toolchains are managed per target. For the JVM target, the toolchain configuration follows the standard Java plugin pattern.

```kotlin
kotlin {
    jvm {
        withJava()
    }
    
    sourceSets {
        val jvmMain by getting {
            // Toolchain is typically inherited from the java plugin
        }
    }
}
```

Ensure that the toolchain version is compatible with the Kotlin compiler version and the target bytecode level.
