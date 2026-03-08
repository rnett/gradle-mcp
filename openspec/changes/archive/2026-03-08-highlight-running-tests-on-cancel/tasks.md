## 1. Core Tracking Implementation

- [x] 1.1 Add tracking collection for active tests tied to a specific build execution
- [x] 1.2 Update test start event handler to add tests to the tracking collection
- [x] 1.3 Update test finish event handler to remove tests from the tracking collection
- [x] 1.4 Handle `SKIPPED` results for tests that were already in progress (treat as cancelled)

## 2. Reporting Implementation

- [x] 2.1 Update build completion/cancellation handler to check for active tests
- [x] 2.2 Format the active test names, truncating the list to 10 items max
- [x] 2.3 Output the formatted in-progress test list when tests remain and build stops

## 3. Testing and Validation

- [ ] 3.1 Create test simulating build cancellation with active tests (In progress: Test created, debugging SKIPPED vs CANCELLED logic)
- [ ] 3.2 Create test simulating build cancellation with >10 active tests
- [ ] 3.3 Verify tracking collection is correctly cleared across independent builds