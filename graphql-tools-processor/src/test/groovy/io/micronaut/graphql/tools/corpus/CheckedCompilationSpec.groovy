package io.micronaut.graphql.tools.corpus

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Path

/**
 * The double compilation of a failing case (#150): with the checks on the compilation fails with the findings of the visitor, and
 * with {@code micronaut.graphql.tools.processor.checks=false} the same sources compile clean, which is the escape hatch. A warning
 * does not fail the compilation. The corpus does this for every case; this is the criterion on its own, with the real compilers
 * of both languages.
 */
class CheckedCompilationSpec extends Specification {

    @Shared
    Corpus corpus = new Corpus(CorpusFixtures.root())

    @TempDir
    Path work

    @Unroll
    void "#caseName [#language.dir]: checks on fail with #finding, checks off compile clean"() {
        given:
        CheckedCaseCompiler compiler = compilers.get(language)
        CorpusCase corpusCase = corpus.byName(caseName)

        when:
        CompiledCase on = compiler.compileChecked(corpusCase, work.resolve('on'))
        CompiledCase off = compiler.compile(corpusCase, work.resolve('off'))

        then:
        !on.success
        on.errors.any { it.contains(finding) }
        off.success
        off.errors == []

        where:
        caseName                              | language        | finding
        'strict/unbound-resolver-method'      | Language.JAVA   | '[unbound-resolver-method] example.Query#stray(java.lang.String)'
        'failure/optional-primitive-non-null' | Language.JAVA   | '[unsupported-return-type] Query.count'
        'failure/optional-primitive-non-null' | Language.GROOVY | '[unsupported-return-type] Query.count'
    }

    @Unroll
    void "a case that binds compiles with the checks on and off [#language.dir]"() {
        given:
        CheckedCaseCompiler compiler = compilers.get(language)
        CorpusCase corpusCase = corpus.byName('bound/root-and-type-fields')

        expect:
        compiler.compileChecked(corpusCase, work.resolve('on')).success
        compiler.compile(corpusCase, work.resolve('off')).success

        where:
        language << [Language.JAVA, Language.GROOVY]
    }

    @Unroll
    void "a finding that the check cannot prove is a warning: the checks-on compilation succeeds [#language.dir]"() {
        given:
        CheckedCaseCompiler compiler = compilers.get(language)
        CorpusCase corpusCase = corpus.byName('input/introspected-elsewhere')

        when:
        CompiledCase on = compiler.compileChecked(corpusCase, work.resolve('on'))

        then:
        on.success
        on.errors == []
        on.warnings.any { it.contains('[class-not-introspected] Filter') && it.contains('@Introspected(classes)') }

        where:
        language << [Language.JAVA, Language.GROOVY]
    }

    static final Map<Language, CheckedCaseCompiler> compilers = [
            (Language.JAVA)  : new JavaCaseCompiler(),
            (Language.GROOVY): new GroovyCaseCompiler(),
    ]

}
