<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: 2c962fb1677ef5da9ebf5ecf13668188e53087b7e9271f9b0e841add746d30c4
-->
# Prefer the `-bin` Gradle Distribution
Gradle publishes two distribution variants for each release: `-bin` (binaries only) and `-all` (binaries, sources, and documentation). For most builds, you should prefer the smaller `-bin` distribution.  
Using `-bin` reduces download size and verification effort, speeds up CI and developer builds, and limits the number of artifacts you need to trust.  

## Explanation
Each Gradle release provides:  
* `gradle-<version>-bin.zip` -- binaries only

* `gradle-<version>-all.zip` -- binaries plus sources and offline documentation

In modern setups:  
* IDEs and build tools can download sources and documentation directly from repositories or online docs (even when using `-bin` releases).

* The `-all` distribution is rarely required outside of specific offline or air-gapped environments.

Preferring `-bin` helps because:  
* It **reduces download and cache size** for CI and local builds.

* There is **less to verify and fewer artifacts to trust** (one smaller archive instead of a larger "everything included" zip).

* It **shortens the feedback loop** when upgrading Gradle.

In special cases (for example, fully offline environments), you can still use `-all`, but it should be a conscious exception rather than the default.  

### Don't Do This
Make sure your Wrapper doesn't point to the `-all` distribution:  
`gradle/wrapper/gradle-wrapper.properties`:  

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-<version>-all.zip
```

### Do This Instead
Configure the Wrapper to use the `-bin` distribution:  
`gradle/wrapper/gradle-wrapper.properties`:  

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-<version>-bin.zip
```

## References
* [Gradle Wrapper checksum verification](https://docs.gradle.org/current/gradle_wrapper.html#configuring_checksum_verification) (Use `gradle_docs(path="gradle_wrapper.html#configuring_checksum_verification")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
