# micronaut-graphql-tools-processor

Gradle project `:micronaut-graphql-tools-processor`, folder `graphql-tools-processor/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-processor`.

## Purpose

The compile-time checks. The annotation processor reads the schema files while `javac` compiles the application, and
fails the build on drift that the startup would otherwise report, for example an object type that nothing reachable
returns. Each error names the schema coordinate and the schema file line. The guide page is `src/main/docs/guide/compileTime.adoc`.

Today the processor is a JSR-269 annotation processor (`GraphQLSchemaBindingProcessor`, registered in
`META-INF/services/javax.annotation.processing.Processor`). It is being replaced by a `TypeElementVisitor` in the package
`io.micronaut.graphql.tools.visitor` (#150, #291). The JSR-269 code is deleted by #144 after the visitor reaches parity.

The visitor (`GraphQLToolsVisitor`) is in the tree and is registered for the tests of this module only
(`src/test/resources/META-INF/services/io.micronaut.inject.visitor.TypeElementVisitor`). The switch (#291) registers it in
`main` and removes the JSR-269 registration; until then the published jar still carries the JSR-269 processor alone.

## Responsibility

The visitor (package `io.micronaut.graphql.tools.visitor`):

- `GraphQLToolsVisitor`: an isolating `TypeElementVisitor` that checks the concrete resolver classes (and an `@Introduction`
  interface that is a resolver) with the per-class rules of `graphql-tools-binding` (`ResolverClassFindings`), and reports each
  finding as a compile error (one finding is a warning, see below). The findings are those of one stage, as at startup: invalid coordinates, then the binding
  failures (the arguments, the input classes and enums they reach, the return type), then the strict checks, which the
  startup runs only after a binding that failed nowhere. It keeps its state per `start()`, looks up no class by name, asks for no project directory (it reads the class output only to compare it with `module.dir`) and
  writes no file (`VisitorArchitectureSpec`).
- `ProcessorOptions`: the four options, `micronaut.graphql.tools.processor.schema.dirs` (directories joined with the path
  separator), `micronaut.graphql.tools.processor.module.dir` (the directory of the module that sets `schema.dirs`, see
  "Resolvers in several modules"), `micronaut.graphql.tools.processor.checks` (`true` or `false`) and
  `micronaut.graphql.tools.processor.resolverAccessorPrefixes` (`true` or `false`, the runtime
  `resolver-accessor-prefixes`; camel case because `javac` rejects a hyphen in an option name). An unknown key under
  `micronaut.graphql.tools.processor.` is an error; a runtime key that is present as a system property is not.
- `CheckedSchema`: reads the schema once per compilation, on the first class that is checked.
- `ElementTypeModel`: the adapter of the type model over `ClassElement` and `MethodElement`; the properties of an input class
  are read with the query that Micronaut's introspection visitor uses, and a class is introspected when it carries
  `@Introspected` (a class that only another class lists in `@Introspected(classes)` or `(packages)`, or that a dependency
  introspects, is not seen as introspected).

`class-not-introspected` is the one finding that a check of one class cannot prove, so the visitor reports it as a compile-time
**warning** and the startup decides (the soundness rule: a compile error must also be a startup failure, and a class that another
class introspects passes at startup). The other findings are errors. The walk stops at that finding, as the startup does
at its failure, so the findings of the same class that come after it are the startup's. A build that turns compiler warnings into errors (`-Werror`) fails on it as well, so there the hatch is `@Introspected` on the class itself. The walk down a chain of input types
runs from a work stack (a chain of 5,000 types is checked on the default stack), as the startup binds with one.

The JSR-269 processor (`processing/`, until #144):

- Read the schema files and the resolver beans of one compilation (`processing/`: `SchemaFiles`, `ResolverBeans`,
  `SchemaChecks`, `BindingChecks`).
- Read the processor options that are passed as `-A` compiler arguments (`ProcessorOptions`).
- Report the findings as compiler errors and, when the checks pass, write the binding manifest to the class output.
- Not in this module: the runtime library and the Micronaut bean lookup. The processor must not depend on the runtime:
  the `checkNoRuntimeDependency` task fails `check` when a main classpath contains `graphql-tools`.

## Resolvers in several modules

A build that keeps resolver classes in feature modules applies the processor in each module that holds resolver classes, and
only there (a module without resolvers has nothing to check, and a schema edit then does not recompile it). A compilation sees
its own sources and its classpath, so the checks split in two:

- **What one class determines runs in every module**, whichever module defines the classes it reaches: a method that binds no
  field, a wrong argument name or type, a primitive at a nullable position, an input class property or an enum constant that
  the schema does not define, the shape of the return type. The input classes and enums of a dependency are read from its
  class files. A module that fails fails its own build, with the code, the coordinate and the schema location; a module that
  does not depend on it still compiles.
- **What needs the whole program never fails at compile time**: a field that no resolver binds (the resolver may be in another
  module), two modules that resolve one field, a type that nothing reaches, the dictionary, directives. The visitor never reports
  a field as unbound because no resolver is in the module. The startup of the application, which sees every module, reports
  these with the coordinate (`field-not-bound`, `multiple-methods-found`, ...).
- **The schema** is a directory per module: each module that applies the processor sets
  `micronaut.graphql.tools.processor.schema.dirs` to the directory that holds the schema, for example the source directory of the
  schema module. A schema that only exists inside a jar on the classpath (`classpath*:` at startup) cannot be read by a compiler
  plug-in, so a module with resolvers and no directory fails with `processor-schema-not-configured`; the explicit way out is
  `micronaut.graphql.tools.processor.checks=false`, and the startup still checks everything. The schema directory is an input of
  every module that sets the option, so a schema edit reruns exactly those modules.
- **A module that forgot the option.** Micronaut copies the options of a compilation into system properties, and a later
  compilation of the same JVM (the Gradle daemon and its compile workers, the Maven reactor) reads them when it sets none. A module
  that forgot `schema.dirs` would be checked against the schema directory of the module compiled before it. So each module also sets
  `micronaut.graphql.tools.processor.module.dir` to its own project directory, next to `schema.dirs` (both are in the documented
  snippet). The visitor compares it with the output directory of the compilation: a value whose directory does not contain
  the output belongs to another module, and the build fails with `processor-schema-not-configured`, naming that directory and the
  output of this compilation. The rule has these limits, and each keeps or fails safe:
  - A module that sets `schema.dirs` without `module.dir` is trusted when no earlier module of the JVM set `module.dir`. Micronaut
    cannot say which options a compilation passed itself, so once one module sets `module.dir`, a later module that sets only
    `schema.dirs` sees the earlier marker and fails with `processor-schema-not-configured`; set both options in every module.
  - A compiler that gives no output directory (kapt and KSP) cannot be compared and keeps the old behaviour.
  - `checks=false` is honoured as before, also when an earlier module left it behind: a module that forgot every option is then
    compiled without checks, and the startup still checks every class.
  - The marker is a directory that contains the output, so a module whose output lies inside the project directory of an earlier
    module (a root project that applies the processor, compiled before a subproject that forgot the options) is not told apart
    from it, which is one more reason to set the options in every module.
  - A build that moves the output of a module outside its project directory sets `module.dir` to a directory that contains it.

  `multi-module-gradle` and `multi-module-maven` in `test-suite-build` prove the failure: the module that forgot the snippet is built after one that set it.

The samples are `multi-module-gradle` and `multi-module-maven` in `test-suite-build`; the corpus cases that split their sources into compilation units are
under `modules/` (see the corpus README).

## Packages

Each package has a `package-info.java`. See the [API map](../src/main/docs/guide/apiMap.adoc).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.visitor` | Compiler plug-in | The compile-time `TypeElementVisitor`; loaded by the compiler, never imported |
| `io.micronaut.graphql.tools.processing` | Internal | The legacy JSR-269 processor, deleted by #144 |

## Who uses it

Application developers, who add it to the annotation processor path next to Micronaut's processor (`annotationProcessor`,
or `kapt` for Kotlin). `test-suite-java` does this in its build. Maintainers use the binding corpus in its tests.

## Public and internal

Application code does not import anything from this module. Its classes are used by `javac` only, through the processor's
service registration. The `processing` package is annotated `@Internal` in its `package-info.java`; it is implementation and is removed by #144.

## Relation to other modules

- Depends on `graphql-tools-binding` (`implementation`): the schema reader (`SchemaSources`, `SchemaSourceException`).
- Depends on `graphql-java` (`implementation`).
- Tests depend on `graphql-tools` (`testImplementation`): the binding corpus runs each case through the runtime binder and
  the compile-time binder and compares their verdicts.
- Used by `test-suite-java`, which puts the processor on its `annotationProcessor` path. `test-suite-kotlin` and
  `test-suite-groovy` do not use it. `test-suite-build` applies the visitor in sample projects (the options, the schema
  directory as a task input, the build cache) and builds them with Gradle TestKit.
- `graphql-tools-bom` manages its version.

## Build and test

The module compiles for Java 21 (`micronautBuild.javaVersion = 21`), so it does not run on JDK 17. Use JDK 21 or 25:

```
./gradlew :micronaut-graphql-tools-processor:check
```

`check` runs the unit tests (the visitor under javac in process: `GraphQLToolsVisitorSpec`, `ProcessorOptionsSpec`,
`ContractsGateSpec`), `compileVisitorRelease17` (the visitor package compiled alone at release 17), the binding corpus (`BindingCorpusSpec`, about 70 seconds on the default graphql-java
leg), `ParitySpec` (the soundness and completeness rules and the exception list), `ProcessorOptionsDocsSpec` (it fails when a processor option is not named in the options page) and
`checkNoRuntimeDependency`. The floor and ceiling legs skip the corpus.

- The corpus is data in `src/test/resources/corpus`; its README says how to add a case, and how a case splits its sources into
  modules that are compiled one after the other. For Java and Groovy the runner compiles
  each case twice: with `checks=false`, which must succeed for every case (the escape hatch), and with the visitor on
  (`VisitorCompileTimeBinder`), whose errors and warnings must equal `expected/compile.txt`. It asserts them against the startup:
  soundness (a compile error is a startup finding; the five documented exceptions to the soundness rule are in
  `corpus-soundness-exceptions.txt`, each with a reason and a case under `exception/`) and completeness (the divergence list,
  empty). A language that differs from the others in an expected file needs a line with a reason in
  `corpus-language-differences.txt` (empty, and it may only shrink). `-Pcorpus.only='<regex>'` runs the cases that match.
- `./gradlew :micronaut-graphql-tools-processor:corpusUpdate` rewrites the expected files of the corpus from what the
  binders say (Java and Groovy). Review the diff.
- `./gradlew :micronaut-graphql-tools-processor:corpusKotlin` runs the Kotlin cases against the real visitor under KSP2: one
  Gradle TestKit build with two subprojects per case (the unchecked one is started, the checked one runs `kspKotlin` with the schema
  of the case, and its `e: [ksp] [code] coordinate` lines are compared with `expected/compile.txt`). It needs the network and is not
  part of `check` or `build`; the CI job `build tools` runs it on every pull request, and the canary workflow runs it under kapt
  (`-Pcorpus.kotlin.processor=kapt`, the `lang:all` cases) every night. Its budget is 3 minutes on JDK 25.
  `-Pcorpus.update` with it rewrites the Kotlin expectations.
