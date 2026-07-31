<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: a0f925778ee0a33fc34e9e390d2eea71ba1e1ff5ba1c72a1b752c4704092924c
-->
# Use UTF-8 File Encoding
Set `UTF-8` as the default file encoding to ensure consistent behavior across platforms.  

## Explanation
Use `UTF-8` as the default file encoding to ensure consistent behavior across environments and avoid caching issues caused by platform-dependent default encodings.  
This is especially important when working with build caching, since differences in file encoding between environments can cause unexpected cache misses.  
To enforce `UTF-8` encoding, add the following to your `gradle.properties` file:  

```properties
org.gradle.jvmargs=-Dfile.encoding=UTF-8
```

|---|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
|   | Do not rely on the default encoding of the underlying JVM or operating system, as this may differ between environments and lead to inconsistent behavior. |

## References
* [File encoding and Caching](https://docs.gradle.org/current/userguide/common_caching_problems.html#system_file_encoding) (Use `gradle_docs(path="userguide/common_caching_problems.html#system_file_encoding")`.).

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
