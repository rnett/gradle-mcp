<!--
class: authored-local
skill: authoring-gradle-builds
-->
# Artifact Publishing

Publishing artifacts to repositories like Maven Central or an internal Nexus/Artifactory requires the `maven-publish` and `signing` plugins.

## Plugin Configuration

Apply both plugins in your `build.gradle.kts`:

```kotlin
plugins {
    `maven-publish`
    signing
}
```

## Defining Publications

A publication defines *what* is being published. You typically create a `MavenPublication` for your main JAR.

```kotlin
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            
            groupId = "com.example"
            artifactId = "my-library"
            version = "1.0.0"
            
            pom {
                name.set("My Library")
                description.set("A professional library for doing things.")
                url.set("https://github.com/example/my-library")
                
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
}
```

## Signing Artifacts

Maven Central requires all artifacts to be digitally signed. Use the `signing` plugin to sign the publications.

```kotlin
signing {
    // Only sign if the GPG key is available in the environment (e.g., local ~/.gnupg or CI secret)
    useGpgCmd() 
    sign(publishing.publications["mavenJava"])
}
```

Keys are typically provided via `gradle.properties` or environment variables:
- `signing.keyId`
- `signing.password`
- `signing.secretKeyRingFile`

## Publishing to Repositories

The `repositories` block defines *where* the artifacts are sent.

```kotlin
publishing {
    repositories {
        maven {
            name = "OSSRH"
            url = uri("https://s01.oss.sonatype.org/service/local-repository/")
            credentials {
                username = project.property("ossrhUsername").toString()
                password = project.property("ossrhPassword").toString()
            }
        }
    }
}
```

## Maven Central Workflow

To successfully publish to Maven Central:
1. **GPG Signing**: Every JAR and POM must be signed.
2. **POM Validation**: Ensure the POM contains a name, description, URL, and license.
3. **Staging**: Publish to a staging repository (like OSSRH) first.
4. **Closing and Releasing**: Use the Sonatype web interface to "Close" the staging repository and "Release" the artifacts to the central registry.
