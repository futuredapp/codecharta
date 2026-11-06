# Swift name resolution

Swift dependency analysis uses the Tree-sitter syntax tree. It does not run the Swift compiler or expand macros.

## Declarations and modules

Classes, structs, enums, actors, protocols, type aliases, top-level functions and simple global bindings are
declarations. Nested types retain their enclosing type's path. A type declared inside a function is not a project
declaration. Each extension contributes dependencies under a file-derived extension name, including a reference
to the type it extends; multiple extensions of that type in one file combine their dependencies.

Under the nearest `Package.swift` within the analysis root, conventional `Sources/<target>/` and `Tests/<target>/`
folders define modules. Other layouts use one module named after the analysis root. The manifest is not executed
or parsed for custom source paths, target dependencies or access rules. Analyse a package root to retain target
boundaries. Xcode and Tuist target membership is not evaluated.

## Lookup order

For a written name such as `Model`, `Outer.Inner` or `Core.Model`:

1. Generic type parameters of the declaration and enclosing types bind their names without producing a project edge.
2. Search enclosing declaration paths, from the innermost scope out to the current module.
3. Search imports. `import Core` allows `Model` and `Core.Model`; `import struct Core.Model` allows that declaration
   and nested names through it. `@testable import` uses the same lookup as a normal import.
4. If imports yield several different paths, or no rule matches, draw no edge. Another language's declarations are
   never candidates. An unresolved library name draws no edge.

`private` and `fileprivate` declarations and their nested types are visible only in the declaring file. This is a
file-level approximation of Swift's access control; lexical `private` rules and cross-module `internal` versus
`public` access are not enforced.

## Extraction limits

Explicit type annotations, inheritance, generic constraints, constructor calls and static receiver paths can
contribute dependencies. Comments and string contents do not contribute names; string interpolation is analysed.
There is no project-specific exclusion list for names such as `Endpoint`.

The analyser does not perform overload resolution, infer receiver types or return types, resolve inherited members,
or model associated types and protocol dispatch. Value bindings that shadow a type name are not fully modelled.
Conditional compilation branches are analysed together. Imported modules need their source declarations in the
analysis input to become project edges; Objective-C bridging and external package discovery are not performed.

## Verification

`SwiftDependencyTest` covers structural extraction, including nested comments, extensions, generic bindings and
long qualified names. `SwiftAnalyzerTest` covers lexical lookup, nested-name collisions, module imports, selective
imports, ambiguity, file visibility and language isolation. `DependencyParserTest` verifies that Swift extension
dependencies reach the file edges in the emitted dependency lens.

Regression probes under `sample-projects/regression/swift/` cover extensions (`spec-extensions`), qualified nested
types (`spec-nested-scope`) and imports between SwiftPM targets (`spec-module-import`).
