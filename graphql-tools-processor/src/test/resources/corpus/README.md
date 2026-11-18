# The binding corpus

A directory of small cases. Each case says, as data, whether a schema binds to a set of resolver sources and, if it does
not, which findings (code, coordinate, schema location) the startup reports. Features and fixes are mostly new data: the
runner (`io.micronaut.graphql.tools.corpus`, in the test sources of this module) does not change when a case is added.
Issue #142 (the design is D-39, DD-4 and DD-13).

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
registry in `graphql-tools-binding` (`BindingCode`, #237): the check name of D-30 for a strict-mode check (`unbound-resolver-method`,
`root-method-several-operations`, `unbound-enum-constant`, `unmapped-input-property`, `unused-dictionary-entry`,
`interface-read-through-implementation`, `unreachable-type`, `unreachable-value-type`, `unwired-directive`) and the code of the
exception for the other failures (`field-not-bound`, `argument-mismatch`, ...). The coordinate is the schema coordinate (`Query.book`, `Book.isbn`) where the finding has
one, and the Java member (`example.Query#stray(java.lang.String)`) or the schema type name where it has not. Parity is
defined at the default severities (D-30). The cases under `severity/` run each of the ten checks at `fail` (with
`strict=false`, so an explicit entry is seen to win), `warn` and `ignore` (`config.micronaut.graphql.tools.checks.<code>`); a `warn`
case lists the `!` line in `manifest.txt`, an `ignore` case has none. They record no `compile.txt`: the compile-time check
does not read the severity settings (D-39).

Case names are the relative paths. `tags` are free `key:value` words; `lang:all` means the case has a Java, a Groovy and
a Kotlin directory. `config.<key>` is runtime configuration (`config.micronaut.graphql.tools.strict=false`).

## What the runner asserts

Per case and language, Java and Groovy are compiled in process (javax.tools; the Groovy compiler with Micronaut's AST
transformation) as part of `test` on the default graphql-java leg, on JDK 21 and 25 (the floor and ceiling legs skip `BindingCorpusSpec`; the processor module never runs on JDK 17), with Micronaut's processing only: **the runner never runs the JSR-269
processor of this module** (DD-13). The result is started through the real application context (`RuntimeBinder`).

1. The startup verdict equals `expected/runtime.txt`.
2. The binding manifest of a bound startup equals `expected/manifest.txt`, where there is one.
3. Where the compile-time backend exists: its findings equal `expected/compile.txt`.
4. Soundness (DD-4): every compile finding is a startup finding with the same code and coordinate. It fails outright; no
   list excuses it. A false compile error is a bug.
5. Completeness: a startup finding of a per-class code (the five parity codes and the per-class failures, see `Parity`)
   that the compile side does not report is a gap, and must be listed in `corpus-divergences.txt`. A whole-program finding
   (field not bound, reachability, ambiguity, the dictionary, customizers) is not a gap: the compile side cannot see it, and
   the case records `compile.txt` as `ok`.

Soundness and completeness are also asserted on the recorded `compile.txt` and `runtime.txt` of every case, so they hold now,
before the compile-time backend exists. Until the visitor (#150) lands the `CompileTimeBinder` is a stub
(`NoCompileTimeBinder`): `compile.txt` holds the verdicts the compile-time design predicts, they are not yet produced by a
compilation, and #150 turns the assertion on by passing its binder to `CorpusRunner`.

Structure is checked too (`BindingCorpusSpec`, "the corpus is well formed"): an orphan or stale file, an override equal to
its base, an unsorted or malformed verdict, a missing `runtime.txt`, an unknown property, a directory without a case.

## The divergence list

`corpus-divergences.txt` holds `max-entries: N` and one line `<case> <java|groovy|kotlin|all> <code> <coordinate>` per
known completeness gap. The ceiling must equal the number of entries, so a removed entry forces it down and a new entry shows
as a raised ceiling in the review. An entry that no longer diverges (the finding is gone from `runtime.txt`, or `compile.txt`
reports it) is stale and fails. The list may only shrink.

## Adding a case

1. Create the directory with `case.properties`, the schema and the sources (copy a neighbour). `expected/` may start with
   an empty `runtime.txt`.
2. `./gradlew :micronaut-graphql-tools-processor:corpusUpdate` records `expected/runtime.txt` (and `manifest.txt` if the file
   exists, even empty). Write `expected/compile.txt` by hand from the design: only for per-class checks and failures, and
   `ok` where the finding is whole-program.
3. Read the diff: it is the review. Check each verdict against what the case is meant to show.
4. `./gradlew :micronaut-graphql-tools-processor:test --tests '*BindingCorpusSpec'` must pass.

Updating after a behaviour change is the same task (`-Pcorpus.update` on `test` does the same). The diff of the expected
files is what the pull request is reviewed on. The task never raises the divergence ceiling.

## Time

Each run writes the time per case and language to `build/corpus/timings.txt`. A Java case takes about two to three seconds
(javac with Micronaut's processors, plus an application context), a Groovy case about the same.

## Kotlin

Kotlin cases run as one batched Gradle TestKit build with the production toolchain (Kotlin 2.1.21, KSP2), not in
process: see `KotlinCorpusSpec` and the task `corpusKotlin`. Cases with a `kotlin/` directory take part; the verdict is
`expected/kotlin/runtime.txt` if there is one, else `expected/runtime.txt`.
