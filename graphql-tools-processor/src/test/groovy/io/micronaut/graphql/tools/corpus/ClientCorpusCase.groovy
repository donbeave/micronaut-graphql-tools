/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * A case of the client corpus: the client interface of each language, the client directory and the verdicts. A case is data, so the
 * runner does not change when one is added.
 *
 * <pre>
 * &lt;case&gt;/
 *   case.properties            description, tags, compile.client-dirs, compile.schema-dirs, compile.option.&lt;full key&gt;
 *   client/                    the client directory: one subdirectory per client schema, with *.graphqls and *.graphql files
 *   schema/                    optional: the schema directory of the resolvers, the schema named server
 *   java/ groovy/ kotlin/      the client interfaces of each language the case covers, as packages
 *   expected/
 *     compile.txt              required: {@code ok}, or the sorted findings, one per line: {@code <code> <member> <position>}
 *     metadata.txt             required when the compilation passes: one line per operation method
 * </pre>
 */
@CompileStatic
final class ClientCorpusCase {

    static final String PROPERTIES = 'case.properties'
    static final Set<String> PROPERTY_KEYS = ['description', 'tags'] as Set<String>
    static final List<String> CLIENT_DIRS = ['case', 'none', 'missing']
    static final List<String> SCHEMA_DIRS = ['case', 'none']

    final String name
    final Path dir
    final String description
    final List<String> tags
    /** {@code compile.client-dirs}: the client directory of the case, none, or one that does not exist */
    final String clientDirs
    /** {@code compile.schema-dirs}: the schema directory of the case (when it has one), or none */
    final String schemaDirs
    /** {@code compile.option.<full key>}: further options of the compilation */
    final Map<String, String> options
    final List<String> problems = []

    private ClientCorpusCase(String name, Path dir, Properties properties) {
        this.name = name
        this.dir = dir
        this.description = properties.getProperty('description', '')
        this.tags = properties.getProperty('tags', '').split(',').collect { it.trim() }.findAll { !it.empty }
        String clientDirs = 'case'
        String schemaDirs = 'case'
        Map<String, String> options = new TreeMap<>()
        properties.stringPropertyNames().sort().each { String key ->
            String value = properties.getProperty(key)
            if (key == 'compile.client-dirs') {
                if (CLIENT_DIRS.contains(value)) {
                    clientDirs = value
                } else {
                    problems.add("${PROPERTIES}: compile.client-dirs must be one of ${CLIENT_DIRS}, not '${value}'".toString())
                }
            } else if (key == 'compile.schema-dirs') {
                if (SCHEMA_DIRS.contains(value)) {
                    schemaDirs = value
                } else {
                    problems.add("${PROPERTIES}: compile.schema-dirs must be one of ${SCHEMA_DIRS}, not '${value}'".toString())
                }
            } else if (key.startsWith('compile.option.')) {
                options.put(key.substring('compile.option.'.length()), value)
            } else if (!PROPERTY_KEYS.contains(key)) {
                problems.add("${PROPERTIES}: unknown key '${key}' (known: ${PROPERTY_KEYS.join(', ')}, compile.client-dirs, compile.schema-dirs, compile.option.<key>)".toString())
            }
        }
        this.clientDirs = clientDirs
        this.schemaDirs = schemaDirs
        this.options = Collections.unmodifiableMap(options)
    }

    static ClientCorpusCase load(Path root, Path dir) {
        Properties properties = new Properties()
        Files.newBufferedReader(dir.resolve(PROPERTIES), StandardCharsets.UTF_8).withCloseable { properties.load(it) }
        ClientCorpusCase result = new ClientCorpusCase(root.relativize(dir).toString().replace(File.separator, '/'), dir, properties)
        result.validateLayout()
        return result
    }

    Path clientDir() {
        return dir.resolve('client')
    }

    Path schemaDir() {
        return dir.resolve('schema')
    }

    /** @return the languages of the case, in the order of {@link Language} */
    List<Language> languages() {
        return Language.values().findAll { Files.isDirectory(dir.resolve(it.dir)) }.toList()
    }

    List<Path> sources(Language language) {
        Path root = dir.resolve(language.dir)
        return list(root).collectMany { Path entry -> walk(entry) }.findAll { it.fileName.toString().endsWith(language.extension) }
    }

    /** @return the binary names of the client interfaces: one per source file, as the files are named after the interface they hold */
    List<String> clients(Language language) {
        Path root = dir.resolve(language.dir)
        return sources(language).collect { root.relativize(it).toString().replace(File.separator, '.').replaceFirst(/\.[a-z]+$/, '') }.sort()
    }

    boolean hasTag(String tag) {
        return tags.contains(tag)
    }

    Path expectedFile(String name) {
        return dir.resolve('expected').resolve(name)
    }

    String expected(String name) {
        Path file = expectedFile(name)
        return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8).stripTrailing() : null
    }

    private void validateLayout() {
        if (languages().empty) {
            problems.add('no java/, groovy/ or kotlin/ directory'.toString())
        }
        if (!Files.isRegularFile(expectedFile('compile.txt'))) {
            problems.add('expected/compile.txt is missing'.toString())
        }
        String compile = expected('compile.txt')
        if (compile == 'ok' && !Files.isRegularFile(expectedFile('metadata.txt'))) {
            problems.add('expected/metadata.txt is missing: a case that compiles records what each operation was written as'.toString())
        }
        if (compile != null && compile != 'ok' && Files.isRegularFile(expectedFile('metadata.txt'))) {
            problems.add('expected/metadata.txt exists for a case that does not compile'.toString())
        }
        if (clientDirs == 'case' && !Files.isDirectory(clientDir()) && !options.containsKey('micronaut.graphql.tools.processor.client.dirs')) {
            problems.add('the case compiles with its client directory and has no client/ directory'.toString())
        }
        if (schemaDirs == 'case' && Files.isDirectory(schemaDir()) && list(schemaDir()).findAll { it.fileName.toString().endsWith('.graphqls') }.empty) {
            problems.add('schema/ holds no *.graphqls file'.toString())
        }
        list(dir).each { Path entry ->
            String entryName = entry.fileName.toString()
            if (!(entryName in [PROPERTIES, 'client', 'schema', 'expected'] + Language.values()*.dir)) {
                problems.add("unknown entry ${entryName}".toString())
            }
        }
        if (Files.isDirectory(dir.resolve('expected'))) {
            list(dir.resolve('expected')).each { Path file ->
                if (!(file.fileName.toString() in ['compile.txt', 'metadata.txt'])) {
                    problems.add("unknown file expected/${file.fileName}".toString())
                }
            }
        }
        Set<String> sets = languages().collect { clients(it).collect { String client -> client } as Set<String> }.collect { it.sort().toString() }.toSet()
        if (sets.size() > 1) {
            problems.add('the languages do not define the same client interfaces'.toString())
        }
    }

    private static List<Path> list(Path directory) {
        if (!Files.isDirectory(directory)) {
            return []
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.sorted().collect(Collectors.toList())
        }
    }

    private static List<Path> walk(Path entry) {
        if (!Files.isDirectory(entry)) {
            return [entry]
        }
        try (Stream<Path> entries = Files.walk(entry)) {
            return entries.filter { Files.isRegularFile(it) }.sorted().collect(Collectors.toList())
        }
    }

}
