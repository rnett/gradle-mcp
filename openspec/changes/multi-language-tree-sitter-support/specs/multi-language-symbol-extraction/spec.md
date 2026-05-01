## ADDED Requirements

### Requirement: C FFI Symbol Extraction

The system SHALL extract symbols (classes, methods, functions, etc.) from source files using the `ts_pack_process()` C FFI function. This function handles parsing, language detection, and extraction internally, eliminating the need for
manual query compilation and cursor traversal.

#### Scenario: Extracting symbols from a Java file

- **WHEN** the system processes a `.java` file
- **THEN** it SHALL call `ts_pack_process()` with a `ProcessConfig` specifying the language and extraction flags
- **AND** deserialize the `structure` and `symbols` JSON arrays from the `ProcessResult`
- **AND** return a `List<ExtractedSymbol>` with name, FQN, package, line, and offset

### Requirement: FQN Construction from Nested Structure

The system SHALL construct Fully Qualified Names (FQNs) by recursively traversing the nested `StructureItem` tree returned by `ts_pack_process()`. Each `StructureItem` contains a `children: List<StructureItem>` field, representing the
containment hierarchy.

#### Scenario: Constructing Java FQN with nested classes

- **WHEN** extracting symbols from a Java file with `class Outer { class Inner { void method() {} } }`
- **THEN** the system SHALL recursively traverse the structure
- **AND** construct the FQN `Outer.Inner.method` by carrying the parent context down the tree
- **AND** use `.` as the separator for Java

#### Scenario: Constructing C++ FQN with namespaces

- **WHEN** extracting symbols from a C++ file with `namespace a { namespace b { class C {} } }`
- **THEN** the system SHALL recursively traverse the structure
- **AND** construct the FQN `a::b::C` by carrying the parent context down the tree
- **AND** use `::` as the separator for C++

### Requirement: Package/Module Detection from Structure

The system SHALL identify the package, module, or namespace of a file from the `StructureItem` tree returned by `ts_pack_process()`. Items with appropriate `kind` values (e.g., `module`, `namespace`) at the top level represent the file's
package.

#### Scenario: Detecting Java package

- **WHEN** processing a Java file with `package com.example;`
- **THEN** the `ts_pack_process()` result SHALL include a structure item with kind `package` or similar
- **AND** the system SHALL use this as the package name for all symbols in the file
