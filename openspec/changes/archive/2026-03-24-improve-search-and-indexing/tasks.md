## 1. Progress Reporting Stability

- [x] 1.1 Modify `ParallelProgressTracker` to track a "focus" dependency from the active tasks.
- [x] 1.2 Update `ParallelProgressTracker.reportMessage` (or related methods) to only update the displayed dependency name if the current focus dependency finishes or if there is no focus dependency.
- [x] 1.3 Ensure progress frequency remains unthrottled and no distinction is forced between extraction and indexing phases in the tracker's message logic.

## 2. Search Result Prioritization

- [x] 2.1 Modify `FullTextSearch.kt` to use multi-field Lucene boosting (`contents` vs `contents_code`).
- [x] 2.2 Implement exact-length whitespace replacement for boilerplate lines (`import`, `package`) to preserve snippet offsets in the code field.
- [x] 2.3 Add a unit test to verify that code matches are ranked higher than import and package matches.

## 3. Snippet Refinement

- [x] 3.1 Audit `SearchResult.toSearchResults` to ensure snippet content remains clean of internal line numbers.
- [x] 3.2 Refine `DependencySourceTools.formatSearchResults` to provide a more consistent and readable output.
- [x] 3.3 Add a test case to verify the final formatted tool output for search results.