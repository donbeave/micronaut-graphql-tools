package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path

/**
 * The options of the checked compilation of a case, as a build passes them to the visitor: the schema directories and any
 * further option. By default the schema directory holds the schema files of the case. A case changes that in
 * {@code case.properties}, so that the option rows of the failure-mode table have corpus cases in every language:
 *
 * <pre>
 * compile.schema-dirs=case|none|missing|empty|split
 * compile.option.&lt;full key&gt;=&lt;value&gt;
 * </pre>
 *
 * <p>{@code none} leaves the option out, {@code missing} names a directory that does not exist, {@code empty} one without a schema file,
 * {@code split} puts the first schema file of the case (by name) in one directory and the others in a second one. An
 * {@code option} line adds an option under its full key, or replaces the default one ({@code ...processor.checks}).</p>
 */
@CompileStatic
final class CompileOptions {

    static final String PREFIX = 'micronaut.graphql.tools.processor.'
    static final String SCHEMA_DIRS = PREFIX + 'schema.dirs'
    static final String CHECKS = PREFIX + 'checks'
    static final List<String> DIRS = ['case', 'none', 'missing', 'empty', 'split']

    private CompileOptions() {
    }

    /** @return the options of the checked compilation, creating the schema directories under {@code work} */
    static Map<String, String> of(CorpusCase corpusCase, Path work) {
        Map<String, String> options = new LinkedHashMap<>()
        String mode = corpusCase.compile.getOrDefault('schema-dirs', 'case')
        List<Path> files = corpusCase.schemaFiles()
        switch (mode) {
            case 'case':
                options.put(SCHEMA_DIRS, copy(files, work.resolve('schema')).toString())
                break
            case 'missing':
                options.put(SCHEMA_DIRS, work.resolve('missing').toString())
                break
            case 'empty':
                Path empty = Files.createDirectories(work.resolve('empty'))
                Files.writeString(empty.resolve('readme.txt'), 'no schema here\n')
                options.put(SCHEMA_DIRS, empty.toString())
                break
            case 'split':
                options.put(SCHEMA_DIRS, [copy(files.take(1), work.resolve('schema-1')), copy(files.drop(1), work.resolve('schema-2'))]
                        .join(File.pathSeparator))
                break
            default:
                break
        }
        corpusCase.compile.each { String key, String value ->
            if (key.startsWith('option.')) {
                options.put(key.substring('option.'.length()), value)
            }
        }
        return options
    }

    private static Path copy(List<Path> files, Path target) {
        Files.createDirectories(target)
        files.each { Files.copy(it, target.resolve(it.fileName.toString())) }
        return target
    }

}
