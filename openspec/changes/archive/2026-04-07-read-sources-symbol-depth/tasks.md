## 1. Filesystem Directory Listing

- [x] 1.1 In `walkDirectoryImpl`, detect when a directory child is at the depth limit (`depth + 1 >= maxDepth`) and append `  (N items)` to its output line using `child.listDirectoryEntries().size`
- [x] 1.2 Verify that directories within the depth limit (those whose children will be walked) do NOT receive a count suffix

## 2. Nested Package Listing

- [x] 2.1 Add a `listNestedPackageContents(sources, packageName, maxSubDepth: Int = 2)` method (or equivalent helper) to `SourceIndexService` that fetches `PackageContents` for the root package and then calls `listPackageContents` for each
  direct sub-package to populate a depth-2 structure
- [x] 2.2 Apply the 30-sub-package cap: if a package's direct sub-packages exceed 30, return only the flat list with a note and skip recursive expansion
- [x] 2.3 Update `DependencySourceTools.formatPackageContents` (or introduce `formatNestedPackageContents`) to render the 2-level structure: sub-packages with their direct symbols listed beneath, and at depth 2 show only the sub-package
  name with `(N symbols)` or `(N sub-packages)` annotation
- [x] 2.4 Update the call site in `readDependencySources` to use the new nested listing when a package path is resolved

## 3. Tests

- [x] 3.1 Unit-test the annotated directory listing: verify `(N items)` appears on directories at the depth limit and not on shallower ones
- [x] 3.2 Unit/integration-test nested package listing: verify 2-level expansion, symbol count annotations, and the 30-sub-package cap behaviour

## 4. Documentation Sync

- [x] 4.1 Run `./gradlew :updateToolsList` to regenerate auto-generated tool docs if any tool description text changed
