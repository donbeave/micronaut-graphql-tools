# The binding corpus

A directory of small cases. Each case says, as data, whether a schema binds to a set of resolver sources and, if it does
not, which findings (code, coordinate, schema location) the startup reports. Features and fixes are mostly new data: the
runner (`io.micronaut.graphql.tools.corpus`, in the test sources of this module) does not change when a case is added.
Issue #142.

## A case

```
<category>/<case>/
  case.properties            description=...  tags=lang:all,area:...  environments=  config.<key>=<value>
  schema.graphqls            one or more *.graphqls files (merged into one TypeDefinitionRegistry)
  java/example/*.java        sources per language; a language directory is a language the case covers
  groovy/example/*.groovy
  kotlin/example/*.kt
  expected/
    runtime.txt              required. `ok`, or the sorted findings of the startup
    compile.txt              optional. `ok`, or the sorted findings of the compile-time checks
    manifest.txt             optional. The binding manifest of a bound startup, without its `#` header lines
    <lang>/<file>            overrides one of the three files for one language
```

A finding line is `<code> <coordinate> <schema-file>:<line>:<col>` (`-` where there is no location). The code is a code of the
registry in `graphql-tools-binding` (`BindingCode`, #237): the check name for a strict-mode check (`unbound-resolver-method`,
`root-method-several-operations`, `unbound-enum-constant`, `unmapped-input-property`, `unused-dictionary-entry`,
`interface-read-through-implementation`, `unreachable-type`, `unreachable-value-type`, `unwired-directive`) and the code of the
exception for the other failures (`field-not-bound`, `argument-mismatch`, ...). The coordinate is the schema coordinate (`Query.book`, `Book.isbn`) where the finding has
one, and the Java member (`example.Query#stray(java.lang.String)`) or the schema type name where it has not. Parity is
defined at the default severities. The cases under `severity/` run each of the ten checks at `fail` (with
`strict=false`, so an explicit entry is seen to win), `warn` and `ignore` (`config.micronaut.graphql.tools.checks.<code>`); a `warn`
case lists the `!` line in `manifest.txt`, an `ignore` case has none. Their `compile.txt` is the one of the default severity: the
compile-time check does not read the severity settings, so a `warn` or `ignore` case of a compile-time code still has the
compile finding, and the soundness check is made on the `fail` case.

A language directory that holds `module-<name>/` directories instead of packages is a case whose sources are several compilations, as a
build with several modules has them (Java and Groovy; the cases are under `modules/`). The modules are compiled in name order, each
with the output of the modules before it on its classpath, and the schema directory of every compilation is the case's schema. Each
compilation is checked on its own and the findings of all of them are the verdict of `compile.txt`: a module with a finding does
not hide the findings of the next one, because the checked compilation of a module sees the unchecked output of the modules before
it. The startup runs with every module on the classpath. A language directory holds either packages or modules, never both.

Case names are the relative paths. `tags` are free `key:value` words; `lang:all` means the case has a Java, a Groovy and
a Kotlin directory. `config.<key>` is runtime configuration (`config.micronaut.graphql.tools.strict=false`); the checks-on compilation also gets
`config.micronaut.graphql.tools.resolver-accessor-prefixes` as the visitor option `micronaut.graphql.tools.processor.resolverAccessorPrefixes`.

## What the runner asserts

Per case and language, Java and Groovy are compiled in process (javax.tools; the Groovy compiler with Micronaut's AST
transformation) as part of `test` on the default graphql-java leg, on JDK 17, 21 and 25 (the floor and ceiling legs skip `BindingCorpusSpec`), with Micronaut's processing only. Every case is compiled **twice**, as a build would:

- **checks off** (`micronaut.graphql.tools.processor.checks=false`, the escape hatch): the sources must compile, whatever the
  case says, because a failing case is a case whose startup or compile-time checks fail, not one whose Java is broken. The
  result is started through the real application context (`RuntimeBinder`).
- **checks on** (the schema files of the case are the schema directory of the visitor): `VisitorCompileTimeBinder` reads the
  errors and the warnings of the compiler as findings. This compilation must fail exactly when `expected/compile.txt` lists an
  error, and report nothing else.

Java and Groovy are compiled without `-parameters` (Java) and without `parameters` (Groovy), so a case shows that a method declared in the
sources binds by the name of its source (the cases under `argument/`, #15). The one thing that needs a flag, a method inherited from a class
file without parameter names, is `ParameterNamesSpec` (a class file cannot be a case).

1. The checks-off compilation succeeds.
2. The startup verdict equals `expected/runtime.txt`.
3. The binding manifest of a bound startup equals `expected/manifest.txt`, where there is one.
4. Where the compile-time backend exists (Java, Groovy and Kotlin under KSP2): the findings of the checks-on compilation equal `expected/compile.txt`.
5. Soundness: every compile **error** is a startup finding with the same code and coordinate. A false compile error is a
   bug. Only the documented exceptions below excuse one.
6. Completeness: a startup finding of a per-class code (the five parity codes and the per-class failures, see `Parity`)
   that the compile side does not report is a gap, and must be listed in `corpus-divergences.txt`. A whole-program finding
   (field not bound, reachability, ambiguity, the dictionary, customizers) is not a gap: the compile side cannot see it, and
   the case records `compile.txt` as `ok`.

A compile line that starts with `warning ` is a warning of the compiler, not an error: the build passes. A check that cannot
prove its fact from one class reports it so (`class-not-introspected`: another class can make a class introspected with
`@Introspected(classes)` or `(packages)`, and so can a dependency, which a visitor that looks at one class cannot see). A warning is
outside the soundness rule, and for the completeness rule it counts as the compile side having reported the finding.

Soundness and completeness are also asserted on the recorded `compile.txt` and `runtime.txt` of every case, so they hold for a
language whose backend does not run in the same task (Kotlin runs in `corpusKotlin`).
The codes that a case relaxes at startup (the strict checks for
`config.micronaut.graphql.tools.strict=false`, the code of a `config.micronaut.graphql.tools.checks.<code>` that is `warn` or `ignore`)
are outside the soundness rule: the compile error cannot be relaxed at startup. Every other finding of the case is compared.

Structure is checked too (`BindingCorpusSpec`, "the corpus is well formed"): an orphan or stale file, an override equal to
its base, an override that no line of `corpus-language-differences.txt` explains, an unsorted or malformed verdict, a missing `runtime.txt`, an unknown property, a directory without a case.

## The divergence list

`corpus-divergences.txt` holds `max-entries: N` and one line `<case> <java|groovy|kotlin|all> <code> <coordinate>` per
known completeness gap. The ceiling must equal the number of entries, so a removed entry forces it down and a new entry shows
as a raised ceiling in the review. An entry that no longer diverges (the finding is gone from `runtime.txt`, or `compile.txt`
reports it) is stale and fails. The list may only shrink.

## The soundness exceptions

`corpus-soundness-exceptions.txt` holds `max-entries: N` and one line `<case> <java|groovy|kotlin|all> <code> <coordinate> <reason>`
per compile error that the startup does not report with the same code and coordinate on purpose. They are the cases where the
premise of the soundness rule (the class is an enabled bean, the startup binds what the compile time judged) does not hold, and each has a
case under `exception/` that shows it:

| reason | what the startup does instead |
|---|---|
| `two-classes-one-root-field` | reports the ambiguity of the field and judges no argument of either class |
| `customizer-claimed-field` | binds no method to a field that a `RuntimeWiringCustomizer` claims (`unbound-resolver-method` with strict on, nothing with strict off) |
| `runtime-exception-first` | binding the field throws before the argument rule (a singleton resolver that cannot be created): `unexpected-binding-error` |
| `other-class-binding-failure` | stops at a binding failure of another class before the strict checks of this one |
| `kotlin-nullability` | reports nothing: the nullability of a Kotlin type is compile-time metadata (`kotlin-nullability-mismatch`) |
| `conflicting-class-mapping` | reports `mapping-conflict` when two classes bind one input type or enum to different Java classes, before the strict checks |
| `dictionary-entry-wins` | lets a `SchemaMappingDictionary` entry win over two classes that match one object type by name (`ambiguous-type-class`); the visitor cannot see the dictionary |

The ceiling must equal the number of entries, so a new exception shows as a raised ceiling in the review. An entry is stale and
fails when the compile side no longer reports the error or the startup reports it now, an unknown reason fails, and an exception
excuses one finding of one case and language, never another finding of the case (`ParitySpec`). Every reason has an entry, on the case `exception/<reason>`. A
false compile error that fits none of the reasons is a bug in the visitor.

The `@Introspected(classes)` or `(packages)` that another class declares is not on this list: it would be a compile error where
the startup passes, so the check is a warning instead (`input/introspected-elsewhere` shows the startup passing and the
compile time silent; `input/class-not-introspected` shows both sides for a class that nothing introspects).

## Adding a case

1. Create the directory with `case.properties`, the schema and the sources (copy a neighbour). `expected/` may start with
   an empty `runtime.txt`.
2. `./gradlew :micronaut-graphql-tools-processor:corpusUpdate` records `expected/runtime.txt` (and `manifest.txt` if the file
   exists, even empty) and, for Java and Groovy, `expected/compile.txt` from the checks-on compilation. A Kotlin case writes
   `expected/compile.txt` by hand from the rule set (only per-class checks and failures, `ok` where the finding is whole-program).
3. Read the diff: it is the review. Check each verdict against what the case is meant to show.
4. `./gradlew :micronaut-graphql-tools-processor:test --tests '*BindingCorpusSpec'` must pass (the whole corpus runs in one JVM). `-Pcorpus.only='<regex>'` runs
   only the cases whose name matches it (for example `-Pcorpus.only='input/.*'`), for writing a case; CI runs them all.

Updating after a behaviour change is the same task (`-Pcorpus.update` on `test` does the same). The diff of the expected
files is what the pull request is reviewed on. The task never raises the divergence ceiling or the exception ceiling: a compile error
that the startup does not report fails the corpus until it is fixed or carries a documented reason.

## Time

Each run writes the time per case and language to `build/corpus/timings.txt`. A Java case takes about two to three seconds
(javac with Micronaut's processors, plus an application context), a Groovy case about the same.

## Kotlin

Kotlin cases run as one batched Gradle TestKit build with the production toolchain (Kotlin and KSP2 of the root build), not in
process: `KotlinCorpusSpec`, the task `corpusKotlin`. Cases with a `kotlin/` directory take part, and they are the `lang:all` cases.
Each case is two subprojects, as it is two compilations for Java and Groovy:

- the unchecked one has the visitor on the `ksp` path with `checks=false`; its classes are started and judged against `runtime.txt`;
- the checked one runs `kspKotlin` only, with the schema files of the case as `micronaut.graphql.tools.processor.schema.dirs`; its
  `e: [ksp] [code] coordinate` errors and `w: [ksp]` warnings, with the schema location of the message, are compared with
  `compile.txt`, and then with `runtime.txt` by the same soundness and completeness rules.

The verdict files are shared by the three languages. A file under `expected/<lang>/` says that one language differs; it fails the
corpus unless `corpus-language-differences.txt` lists it (`<case> <lang> <file> <reason>`). That list is empty and may only shrink.
A completeness gap of one language goes in `corpus-divergences.txt` with the language in the second column.

Adding a Kotlin case is adding a `kotlin/` directory (and the tag `lang:all`, with a `java/` and a `groovy/` directory next to it);
`./gradlew :micronaut-graphql-tools-processor:corpusKotlin` runs it (network needed), and `-Pcorpus.update` with it records what
the Kotlin run says. `-Pcorpus.kotlin.processor=kapt` runs the cases tagged `lang:all` or `kapt:yes` under kapt; the canary workflow does it every night. A kapt stub holds less nullability than KSP reads, so a case tagged `kapt:yes` may record `expected/compile.kapt.txt`, the findings under kapt where they differ from `compile.txt`. In CI, the
job `build tools` runs `corpusKotlin` on every pull request, and it is a gated check.

Kotlin compilation is the cost of the corpus. The batch builds `2 x N` subprojects with `--parallel`; the measurements of 23 cases are
in the pull request that added the checked variant (the Gradle build took 40 s unchecked only and 45 s with the checked variant on a
10-core machine, 50 to 80 s under kapt). The budget is 8 minutes (`corpus.kotlin.budget-seconds`, 480 s by default); over it, a `kotlin:pr` tag would
select the subset for pull requests and the nightly run would keep all of them. Under kapt, Gradle runs the annotation processing
in a process of its own (`kapt.workers.isolation=process`), because javac copies a `micronaut.*` option into the system properties of
its JVM and a later compilation in the same JVM would read the first value.
