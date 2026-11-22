# micronaut-graphql-tools-processor

Gradle project `:micronaut-graphql-tools-processor`, folder `graphql-tools-processor/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-processor`.

## Purpose

The compile-time checks. The annotation processor reads the schema files while `javac` compiles the application, and
fails the build on drift that the startup would otherwise report, for example an object type that nothing reachable
returns. Each error names the schema coordinate and the schema file line. The guide page is `src/main/docs/guide/compileTime.adoc`.

The compile-time engine is a `TypeElementVisitor` (`GraphQLToolsVisitor`, package `io.micronaut.graphql.tools.visitor`), registered in
`META-INF/services/io.micronaut.inject.visitor.TypeElementVisitor` of the jar. The jar holds that service file and no other
(`RegistrationSpec`): it registers no `javax.annotation.processing.Processor` and no
`META-INF/gradle/incremental.annotation.processors`, so the visitor is the one compile-time engine of a build.

## Responsibility

The visitor (package `io.micronaut.graphql.tools.visitor`):

- `ResolverContractsWriter`: records what a resolver class inherits: the contracts of a type resolver, the root operations of the marker interfaces (`QueryResolver`, `MutationResolver`, `SubscriptionResolver`), the source type of `TypeResolver<T>` and the contract methods (`MarkerAnalysis` reads them from the class hierarchy, and the per-class rules read the same answer). It writes `@ResolverContracts` into the annotation metadata of each bean class, also with `checks=false`, and the startup reads it from the bean definition instead of reading class annotations or generic types by reflection (#34, #73).
- `GraphQLToolsVisitor`: an isolating `TypeElementVisitor` that checks the concrete resolver classes (and an `@Introduction`
  interface that is a resolver) with the per-class rules of `graphql-tools-binding` (`ResolverClassFindings`), and reports each
  finding as a compile error (one finding is a warning, see below). The findings are those of one stage, as at startup: invalid coordinates, then the binding
  failures (the arguments, the input classes and enums they reach, the return type), then the strict checks, which the
  startup runs only after a binding that failed nowhere. It keeps its state per `start()`, looks up no class by name, asks for no project directory (it reads the class output only to compare it with `module.dir`) and
  writes no file (`VisitorArchitectureSpec`).
- `ProcessorOptions`: the six options, `micronaut.graphql.tools.processor.schema.dirs` (directories joined with the path
  separator), `micronaut.graphql.tools.processor.module.dir` (the directory of the module that sets `schema.dirs`, see
  "Resolvers in several modules"), `micronaut.graphql.tools.processor.checks` (`true` or `false`),
  `micronaut.graphql.tools.processor.resolver.accessor.prefixes` (`true` or `false`, the runtime
  `resolver-accessor-prefixes`; camel case because `javac` rejects a hyphen in an option name),
  `micronaut.graphql.tools.processor.loose.nullability` (`fail`, `warn` or `ignore`, what a Kotlin type that is stricter than the schema type is) and
  `micronaut.graphql.tools.processor.type.class.convention` (the runtime `type-class-convention`). An unknown key under
  `micronaut.graphql.tools.processor.` is an error; a runtime key that is present as a system property is not.
- `CheckedSchema`: reads the schema once per compilation, on the first class that is checked.
- `ElementTypeModel`: the adapter of the type model over `ClassElement` and `MethodElement`; the properties of an input class
  are read with the query that Micronaut's introspection visitor uses, and a class is introspected when it carries
  `@Introspected`, or a `@GraphQLModels` of the same compilation lists it (a class that only another class lists in
  `@Introspected(classes)` or `(packages)`, or that a dependency introspects, is not seen as introspected). The visitor
  records the `@GraphQLModels` annotations as it visits and judges the classes they did not cover when the compilation
  finishes, so the declaration order does not matter.

`class-not-introspected` is the one finding that a check of one class cannot prove, so the visitor reports it as a compile-time
**warning** and the startup decides (the soundness rule: a compile error must also be a startup failure, and a class that another
class introspects passes at startup). The other findings are errors. The walk stops at that finding, as the startup does
at its failure, so the findings of the same class that come after it are the startup's. A build that turns compiler warnings into errors (`-Werror`) fails on it as well, so there the hatch is `@Introspected` on the class itself, or a `@GraphQLModels` that lists it. The walk down a chain of input types
runs from a work stack (a chain of 5,000 types is checked on the default stack), as the startup binds with one.

Not in this module: the runtime library and the Micronaut bean lookup. The module must not depend on the runtime:
the `checkNoRuntimeDependency` task fails `check` when a main classpath contains `graphql-tools`. The visitor writes no binding
manifest; the runtime renders it.

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

Each package has a `package-info.java`. See the [API map](../CONTRIBUTING.md#api-map).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.visitor` | Compiler plug-in | The compile-time `TypeElementVisitor`; loaded by the compiler, never imported |

## Who uses it

Application developers, who add it to the path that their compiler reads Micronaut's processors from (`annotationProcessor`,
`ksp` or `kapt` for Kotlin, `compileOnly` for Groovy). The three example suites do this in their builds. Maintainers use the binding corpus in its tests.

## Public and internal

Application code does not import anything from this module. Its classes are used by the compiler only, through the visitor's
service registration. `BindingRulesAccess` is the one exception: it is public only so that the `:benchmarks` module
can run the per-class rules over compiler elements without a class in this package. It is internal, not API, and may change without notice.

## Relation to other modules

- Depends on `graphql-tools-binding` (`implementation`): the schema reader (`SchemaSources`, `SchemaSourceException`).
- Depends on `graphql-java` (`implementation`).
- Tests depend on `graphql-tools` (`testImplementation`): the binding corpus runs each case through the runtime binder and
  the compile-time binder and compares their verdicts.
- Used by `test-suite-java` (`annotationProcessor`), `test-suite-kotlin` (`ksp`) and `test-suite-groovy` (`compileOnly`); each has
  a `src/driftFixture` source set whose compilation must fail (`driftFixtureTest`). `test-suite-build` applies the visitor in sample projects (the options, the schema
  directory as a task input, the build cache) and builds them with Gradle TestKit.
- `graphql-tools-bom` manages its version.

## Build and test

The module compiles with `--release 17`, like the other library modules, and `check` runs on JDK 17, 21 and 25 (the CI job `check library` runs it on JDK 17):

```
./gradlew :micronaut-graphql-tools-processor:check
```

`check` runs the unit tests (the visitor under javac in process: `GraphQLToolsVisitorSpec`, `ProcessorOptionsSpec`,
`ContractsGateSpec`), the binding corpus (`BindingCorpusSpec`, about 70 seconds on the default graphql-java
leg), `ParitySpec` (the soundness and completeness rules and the exception list), `ProcessorOptionsDocsSpec` (it fails when a compile-time option is not named in the options page) and
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
  (`-Pcorpus.kotlin.processor=kapt`, the `lang:all` and `kapt:yes` cases; kapt reads Kotlin nullability from the stub annotations, `ElementTypeModel`) every night. Its budget is 8 minutes on JDK 25 (`corpus.kotlin.budget-seconds`, 480 s by default).
  Under kapt every compilation gets a worker process of its own (a unique `-Dcorpus.kapt.worker` argument): Gradle reuses an idle worker for the next task with the same JVM arguments, and javac keeps the first value of a Micronaut option in the system properties of that process, so a shared worker made the verdicts depend on the order of the tasks.
  `-Pcorpus.update` with it rewrites the Kotlin expectations.
