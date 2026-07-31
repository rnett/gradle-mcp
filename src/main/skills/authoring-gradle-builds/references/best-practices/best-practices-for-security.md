<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: af76df9859f061c86f10a8a7f9839b1014654521710435bc69becfbc5c522795
-->
# Best Practices for Security

## Validate the Gradle Distribution SHA-256 Checksum
Set `distributionSha256Sum` in `gradle-wrapper.properties` to verify the integrity of the downloaded Gradle distribution.  

### Explanation
Always set the `distributionSha256Sum` property in your `gradle-wrapper.properties` file to verify the integrity of the downloaded Gradle distribution. This ensures the `gradle-X.X-bin.zip` file matches the official SHA-256 checksum published by Gradle, protecting your build from corruption or tampering.  

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.6-bin.zip
distributionSha256Sum=2b3f4...sha256-here...f4511
```

This validation step enhances security by preventing the execution of compromised or incomplete Gradle distributions.  
The official SHA-256 checksums can be found on the [Gradle releases page](https://gradle.org/releases/).  

### References
* [Gradle Releases with Checksums](https://gradle.org/releases/)

* [Gradle Wrapper Reference](https://docs.gradle.org/current/userguide/gradle_wrapper.html#gradle_wrapper) (Use `gradle_docs(path="userguide/gradle_wrapper.html#gradle_wrapper")`.)

### Tags
[`#properties`](https://docs.gradle.org/current/userguide/tags_reference.html#tag:properties) (Use `gradle_docs(path="userguide/tags_reference.html#tag:properties")`.), [`#wrapper`](https://docs.gradle.org/current/userguide/tags_reference.html#tag:wrapper) (Use `gradle_docs(path="userguide/tags_reference.html#tag:wrapper")`.)  

## Validate the Gradle Wrapper on every Upgrade
The Gradle Wrapper runs before your build logic and can deeply affect your build. You should treat any change to the Wrapper as security-sensitive.  
Validate the Wrapper JAR and distribution settings every time you upgrade Gradle.  

### Explanation
When you update Gradle, two Wrapper files typically change:  
* `gradle/wrapper/gradle-wrapper.jar`

* `gradle/wrapper/gradle-wrapper.properties` (`distributionUrl` and optionally `distributionSha256Sum`)

You should verify that:  
* The **Wrapper JAR** is the official binary published by Gradle (not tampered with).

* The **Wrapper JAR checksum** matches one of the values published at [gradle.org/release-checksums](https://gradle.org/release-checksums).

* The **distribution URL** points to the expected Gradle release.

* The **distribution checksum** (if configured via `distributionSha256Sum`) matches the release you intend to use (also listed on [gradle.org/release-checksums](https://gradle.org/release-checksums)).

Running an unverified Wrapper risks executing untrusted code before any of your build's safeguards run.  

#### Do this
If you use the [`setup-gradle` action](https://github.com/marketplace/actions/build-with-gradle#the-setup-gradle-action) (version v4 or newer) for [GitHub Actions](https://github.com/features/actions), Wrapper validation will be performed automatically. The action validates the checksum of every `gradle-wrapper.jar` in your repository and fails the build if it finds any unknown Wrapper JAR.  
If you use a different GitHub Actions setup, you can use the dedicated [Gradle Wrapper validation action](https://github.com/marketplace/actions/build-with-gradle#the-wrapper-validation-action) instead.  

### References
* [Gradle Wrapper checksum verification](https://docs.gradle.org/current/gradle_wrapper.html#configuring_checksum_verification) (Use `gradle_docs(path="gradle_wrapper.html#configuring_checksum_verification")`.)

* [Gradle release checksums](https://gradle.org/release-checksums)

### Tags
[`#wrapper`](https://docs.gradle.org/current/userguide/tags_reference.html#tag:wrapper) (Use `gradle_docs(path="userguide/tags_reference.html#tag:wrapper")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
