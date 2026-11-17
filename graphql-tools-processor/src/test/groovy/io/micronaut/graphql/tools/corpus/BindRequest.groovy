package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

import java.nio.file.Path

/**
 * What a {@link Binder} gets: the case, the language, the compiler of that language (a compile-time binder compiles
 * the sources again, with the checks on) and the classes the runner compiled without the checks, which the runtime
 * binder starts. {@code work} is an empty directory that belongs to this request.
 */
@CompileStatic
final class BindRequest {

    final CorpusCase corpusCase
    final Language language
    final CaseCompiler compiler
    final CompiledCase unchecked
    final Path work

    BindRequest(CorpusCase corpusCase, Language language, CaseCompiler compiler, CompiledCase unchecked, Path work) {
        this.corpusCase = corpusCase
        this.language = language
        this.compiler = compiler
        this.unchecked = unchecked
        this.work = work
    }

}
