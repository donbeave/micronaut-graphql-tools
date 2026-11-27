# The client corpus

A directory of small cases for the client visitor (`GraphQLClientVisitor`, issue #666). Each case is a client interface in Java,
Groovy and Kotlin, a client directory with the schema and the operation files, and the verdicts that the visitor must give:
the findings (code, member, position) or, when the case compiles, what each operation was written as. A case is data: the runner
(`io.micronaut.graphql.tools.corpus`, `ClientCorpusSpec` and `KotlinClientCorpusSpec` in the test sources of this module) does not
change when one is added.

## A case

```
<category>/<case>/
  case.properties            description=...  tags=lang:all  compile.client-dirs=case|none|missing|case+missing  compile.schema-dirs=case|none  compile.option.<full key>=<value>
  client/<schema>/...        the client directory: one subdirectory per client schema, with *.graphqls and *.graphql files
  schema/*.graphqls          optional: the schema directory of the resolvers, which is the client schema named server
  java/example/*.java        the client interfaces, one per file, named after the interface; a language directory is a language the case covers
  groovy/example/*.groovy
  kotlin/example/*.kt
  expected/
    compile.txt              required. `ok`, or the sorted findings, one per line: <code> <member> <position>
    metadata.txt             required when compile.txt is `ok`: one line per operation method
```

- A finding line is the code and the member of the first line of the message, then the position of its `document` or `schema` line,
  relative to the client directory (`<client>/books/ops/books.graphql:3:5`) or the schema directory (`<schema>/schema.graphqls:1:1`), or
  `inline:3:5` for a document written in the annotation, or `-`. The member is `binary.Name#method(parameter types)`, or `-` for an option error.
  A finding that the visitor reports twice is listed twice, so a case about a cause that is reported once fails when it is reported again.
- A metadata line is `<Client>#<method> type=.. name=.. payload=.. variables=.. document=..`: what the visitor wrote into the annotation
  metadata of the method, read back from the bean definition of the compiled interface. The document has its whitespace removed,
  because the printer of graphql-java spaces a compact document a little differently from one release to the next.
- `compile.client-dirs` says which client directory the compilation gets: the one of the case (default), none (no option), or a
  directory that does not exist, or the directory of the case followed by one that does not exist (`case+missing`, two roots). `compile.schema-dirs=none` leaves `schema.dirs` out. `compile.option.<key>` adds or replaces an option under its
  full key (`compile.option.micronaut.graphql.tools.processor.client.checks=false`).
- The three languages of a case define the same interfaces, and every language gives the same verdict: a language that differs is a
  bug of the visitor or of the harness, not a second expected file. A case with one language directory (`kotlin/suspend-function`) is for what only that
  language has.

## What the runner asserts

Each case is compiled once per language with the visitor on the compiler path and the options of the case, as a build passes them:
`-A` options for javac, system properties for the Groovy compiler (in process), `ksp { arg }` for Kotlin (one Gradle TestKit build for
all the cases, the Gradle task `corpusKotlin`; `-Pcorpus.kotlin.processor=kapt` applies kapt for the `lang:all` cases, the nightly run).
The findings must equal `expected/compile.txt`, and, when the compilation passes, the metadata of every interface must equal
`expected/metadata.txt`. Structure is checked too: an orphan or unknown file, a missing verdict, a language that defines other interfaces
than the others, and a client code with no case. The corpus runs on the default graphql-java leg only (the positions of a syntax
error come from the parser of graphql-java).

## Adding a case

1. Create the directory with `case.properties`, the client directory and the interfaces of each language, and an `expected/compile.txt` that says `ok`.
2. `./gradlew :micronaut-graphql-tools-processor:clientCorpusUpdate` records `expected/compile.txt` and `expected/metadata.txt` from the Java run.
   It runs `ClientCorpusSpec` only, so it records the cases that have a Java directory. A case of Kotlin only is recorded by
   `./gradlew :micronaut-graphql-tools-processor:corpusKotlin --tests '*KotlinClientCorpusSpec' -Pclient.corpus.update -Pcorpus.only='<regex>'` (it needs the network).
   Read the diff: it is the review. Then run the case in the other languages (`./gradlew :micronaut-graphql-tools-processor:test --tests '*ClientCorpusSpec'` for
   Groovy, the `corpusKotlin` command above without `-Pclient.corpus.update` for Kotlin).
3. `-Pcorpus.only='<regex>'` runs only the cases whose name matches.

The case `document/too-large` is the document of 70 KB that does not fit in the annotation metadata (`client-document-too-large`): a document of
a file of the client directory, because the compiler limits a string written in an annotation as well.
