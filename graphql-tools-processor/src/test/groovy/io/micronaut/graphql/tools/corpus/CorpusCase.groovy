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
 * A corpus case: one directory with the schema, the resolver sources per language, {@code case.properties} and the
 * expected verdicts. Reading the directory never needs a harness change: a case is data.
 *
 * <pre>
 * &lt;case&gt;/
 *   case.properties            description, tags, environments, runtime.without, config.&lt;key&gt; (the runtime configuration)
 *   schema.graphqls            one or more *.graphqls files
 *   java/ groovy/ kotlin/      the sources of each language the case covers, as packages, or as module-&lt;name&gt;/ directories that
 *                              are compiled one after the other (Java and Groovy)
 *   expected/
 *     runtime.txt              required: {@code ok}, or the sorted findings of the startup
 *     compile.txt              optional: {@code ok}, or the sorted findings of the compile-time checks
 *     manifest.txt             optional: the binding manifest of a bound startup
 *     &lt;lang&gt;/...                overrides of the three files for one language
 * </pre>
 */
@CompileStatic
final class CorpusCase {

    static final String PROPERTIES = 'case.properties'
    static final String EXPECTED = 'expected'
    static final String JAR = 'jar'
    static final String MODULE_PREFIX = 'module-'
    static final List<String> EXPECTED_FILES = ['runtime.txt', 'compile.txt', 'compile.kapt.txt', 'manifest.txt']
    static final Set<String> PROPERTY_KEYS = ['description', 'tags', 'environments', 'runtime.without'] as Set<String>
    static final String CONFIG_PREFIX = 'config.'
    static final String COMPILE_PREFIX = 'compile.'
    /** {@code compile.processor=absent}: the case is compiled without the processor on the compiler path, as a build that does not register it */
    static final String PROCESSOR = 'processor'
    static final String PROCESSOR_ABSENT = 'absent'

    final String name
    final Path dir
    final String description
    final List<String> tags
    final List<String> environments
    /** {@code runtime.without}: file name prefixes of the class path entries that the startup of the case does not see, as an application that lacks that library */
    final List<String> runtimeWithout
    final Map<String, String> config
    /** the options of the checked compilation, see {@link CompileOptions}, keys without the {@code compile.} prefix */
    final Map<String, String> compile
    final List<String> problems = []

    private final Map<Language, Map<String, String>> expectedFiles = [:]
    private final Map<String, String> baseExpected = [:]

    private CorpusCase(String name, Path dir, Properties properties) {
        this.name = name
        this.dir = dir
        this.description = properties.getProperty('description', '')
        this.tags = split(properties.getProperty('tags', ''))
        this.environments = split(properties.getProperty('environments', ''))
        this.runtimeWithout = split(properties.getProperty('runtime.without', ''))
        Map<String, String> config = new TreeMap<>()
        Map<String, String> compile = new TreeMap<>()
        properties.stringPropertyNames().sort().each { String key ->
            if (key.startsWith(CONFIG_PREFIX)) {
                config.put(key.substring(CONFIG_PREFIX.length()), properties.getProperty(key))
            } else if (key.startsWith(COMPILE_PREFIX)) {
                String setting = key.substring(COMPILE_PREFIX.length())
                String value = properties.getProperty(key)
                boolean valid = setting == 'schema-dirs' ? CompileOptions.DIRS.contains(value)
                        : setting == PROCESSOR ? value in ['present', PROCESSOR_ABSENT] : setting.startsWith('option.')
                if (!valid) {
                    problems.add("${PROPERTIES}: unknown compile setting '${key}' (known: compile.schema-dirs=${CompileOptions.DIRS.join('|')}, compile.processor=present|${PROCESSOR_ABSENT}, compile.option.<key>)".toString())
                } else {
                    compile.put(setting, value)
                }
            } else if (!PROPERTY_KEYS.contains(key)) {
                problems.add("${PROPERTIES}: unknown key '${key}' (known: ${PROPERTY_KEYS.join(', ')}, config.<key>)".toString())
            }
        }
        this.config = Collections.unmodifiableMap(config)
        this.compile = Collections.unmodifiableMap(compile)
    }

    static CorpusCase load(Path root, Path dir) {
        Properties properties = new Properties()
        Files.newBufferedReader(dir.resolve(PROPERTIES), StandardCharsets.UTF_8).withCloseable { properties.load(it) }
        CorpusCase result = new CorpusCase(root.relativize(dir).toString().replace(File.separator, '/'), dir, properties)
        result.validateLayout()
        return result
    }

    private static List<String> split(String value) {
        return value.split(',').collect { it.trim() }.findAll { !it.empty }
    }

    List<Path> schemaFiles() {
        return list(dir).findAll { Files.isRegularFile(it) && it.fileName.toString().endsWith('.graphqls') }
    }

    /** @return the schema files that the case puts in a dependency jar, under {@code graphql/}, read through {@code classpath*:} */
    List<Path> jarSchemaFiles() {
        Path jar = dir.resolve(JAR)
        return Files.isDirectory(jar) ? list(jar).findAll { Files.isRegularFile(it) && it.fileName.toString().endsWith('.graphqls') } : []
    }

    /** @return the languages of the case, in the order of {@link Language} */
    List<Language> languages() {
        return Language.values().findAll { Files.isDirectory(dir.resolve(it.dir)) }.toList()
    }

    /** @return every source of a language, whether the case splits them into modules or not */
    List<Path> sources(Language language) {
        return sourcesIn(dir.resolve(language.dir), language)
    }

    /**
     * @return the compilations of a language: the one unit of a case that keeps its packages directly under the language directory,
     * or one unit per {@code module-<name>} directory, in name order (each sees the output of the units before it)
     */
    List<SourceUnit> units(Language language) {
        Path root = dir.resolve(language.dir)
        List<Path> modules = moduleDirs(root)
        if (modules.empty) {
            return [new SourceUnit('', sources(language))]
        }
        return modules.collect { Path module -> new SourceUnit(module.fileName.toString(), sourcesIn(module, language)) }
    }

    private static List<Path> moduleDirs(Path root) {
        return list(root).findAll { Files.isDirectory(it) && it.fileName.toString().startsWith(MODULE_PREFIX) }
    }

    private static List<Path> sourcesIn(Path root, Language language) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(language.extension) }
                    .sorted().collect(Collectors.toList())
        }
    }

    /**
     * @return the text of an expected file for a language: the file under {@code expected/<lang>/} if there is one,
     * else the file under {@code expected/}, else {@code null}
     */
    String expected(Language language, String file) {
        String own = expectedFiles.get(language)?.get(file)
        return own != null ? own : baseExpected.get(file)
    }

    boolean hasOwnExpected(Language language, String file) {
        return expectedFiles.get(language)?.containsKey(file) ?: false
    }

    boolean hasBaseExpected(String file) {
        return baseExpected.containsKey(file)
    }

    /** @return the sorted findings of {@code runtime.txt} or {@code compile.txt}, or {@code null} if the file is not recorded */
    Verdict expectedVerdict(Language language, String file) {
        String text = expected(language, file)
        return text == null ? null : parseVerdict(text)
    }

    static Verdict parseVerdict(String text) {
        List<String> lines = text.readLines().collect { it.trim() }.findAll { !it.empty && !it.startsWith('#') }
        if (lines == ['ok']) {
            return Verdict.bound()
        }
        return Verdict.failed(lines.collect { Finding.parse(it) })
    }

    /** @return whether the checked compilation of the case has the checks switched off ({@code compile.option.<processor>.checks=false}) */
    boolean checksOff() {
        return compile.get('option.' + CompileOptions.CHECKS)?.trim()?.toLowerCase() in ['false', 'no', 'off']
    }

    /** @return whether the case is compiled without the processor: nothing is checked at compile time and the startup sees what a build without it produces */
    boolean processorAbsent() {
        return compile.get(PROCESSOR) == PROCESSOR_ABSENT
    }

    boolean hasTag(String tag) {
        return tags.contains(tag)
    }

    Path expectedPath(Language language, String file) {
        return language == null ? dir.resolve(EXPECTED).resolve(file) : dir.resolve(EXPECTED).resolve(language.dir).resolve(file)
    }

    private static List<Path> list(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.sorted().collect(Collectors.toList())
        }
    }

    /**
     * Reads the layout and records every orphan or malformed part in {@link #problems}: an unknown entry, a language
     * directory without sources, an expected file with nothing to check, a stale override, an unsorted verdict.
     */
    private void validateLayout() {
        Set<String> languageDirs = Language.values()*.dir as Set<String>
        list(dir).each { Path entry ->
            String file = entry.fileName.toString()
            boolean known = file == PROPERTIES || file == EXPECTED || languageDirs.contains(file) && Files.isDirectory(entry) ||
                    file == JAR && Files.isDirectory(entry) || Files.isRegularFile(entry) && (file.endsWith('.graphqls') || file.endsWith('.graphql'))
            if (!known) {
                problems.add("orphan entry '${file}' (a case holds ${PROPERTIES}, *.graphqls, *.graphql operation files, ${JAR}/, ${languageDirs.join('/')} and ${EXPECTED})".toString())
            }
        }
        if (Files.isDirectory(dir.resolve(JAR)) && jarSchemaFiles().empty) {
            problems.add("${JAR}/ has no *.graphqls schema file".toString())
        }
        if (schemaFiles().empty) {
            problems.add('no *.graphqls schema file')
        }
        List<Language> languages = languages()
        if (languages.empty) {
            problems.add('no source directory (java, groovy or kotlin)')
        }
        languages.each { Language language ->
            List<Path> sources = sources(language)
            if (sources.empty) {
                problems.add("${language.dir}/ has no ${language.extension} source".toString())
            }
            Path root = dir.resolve(language.dir)
            List<Path> modules = moduleDirs(root)
            if (!modules.empty) {
                list(root).findAll { !modules.contains(it) }.each {
                    problems.add("${language.dir}/${it.fileName} sits next to the ${MODULE_PREFIX}* directories: a language directory holds either packages or modules".toString())
                }
                modules.findAll { sourcesIn(it, language).empty }.each {
                    problems.add("${language.dir}/${it.fileName}/ has no ${language.extension} source".toString())
                }
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter { Files.isRegularFile(it) && !it.fileName.toString().endsWith(language.extension) }
                        .forEach { problems.add("orphan file ${dir.relativize(it)}: only ${language.extension} sources belong in ${language.dir}/".toString()) }
            }
        }
        if (hasTag('lang:all') && languages.size() != Language.values().length) {
            problems.add('tag lang:all needs a java, a groovy and a kotlin directory')
        }
        readExpected(languages)
        List<String> distinct = problems.unique(false)
        problems.clear()
        problems.addAll(distinct)
    }

    private void readExpected(List<Language> languages) {
        Path expectedDir = dir.resolve(EXPECTED)
        if (!Files.isDirectory(expectedDir)) {
            problems.add("no ${EXPECTED}/ directory".toString())
            return
        }
        list(expectedDir).each { Path entry ->
            String file = entry.fileName.toString()
            if (Files.isRegularFile(entry)) {
                if (EXPECTED_FILES.contains(file)) {
                    baseExpected.put(file, Files.readString(entry, StandardCharsets.UTF_8))
                } else {
                    problems.add("orphan expected file ${EXPECTED}/${file}".toString())
                }
            } else {
                Language language = Language.values().find { it.dir == file }
                if (language == null || !languages.contains(language)) {
                    problems.add("orphan expected directory ${EXPECTED}/${file}/: the case has no such language".toString())
                    return
                }
                Map<String, String> own = new LinkedHashMap<>()
                expectedFiles.put(language, own)
                list(entry).each { Path overridden ->
                    String name = overridden.fileName.toString()
                    if (Files.isRegularFile(overridden) && EXPECTED_FILES.contains(name)) {
                        own.put(name, Files.readString(overridden, StandardCharsets.UTF_8))
                    } else {
                        problems.add("orphan expected file ${EXPECTED}/${file}/${name}".toString())
                    }
                }
            }
        }
        languages.each { Language language ->
            if (expected(language, 'runtime.txt') == null) {
                problems.add("no expected runtime verdict for ${language.dir} (${EXPECTED}/runtime.txt)".toString())
            }
        }
        expectedFiles.each { Language language, Map<String, String> own ->
            own.each { String file, String text ->
                if (baseExpected.get(file) == text) {
                    problems.add("stale override ${EXPECTED}/${language.dir}/${file}: it equals ${EXPECTED}/${file}".toString())
                }
            }
        }
        if (processorAbsent() && (baseExpected.containsKey('compile.txt') || baseExpected.containsKey('compile.kapt.txt') ||
                expectedFiles.values().any { it.containsKey('compile.txt') || it.containsKey('compile.kapt.txt') })) {
            problems.add("${EXPECTED}/compile.txt: a case with compile.processor=absent has no compile-time verdict".toString())
        }
        if (baseExpected.containsKey('compile.kapt.txt')) {
            if (!hasTag('kapt:yes')) {
                problems.add("${EXPECTED}/compile.kapt.txt needs the tag kapt:yes".toString())
            }
            if (baseExpected.get('compile.kapt.txt') == baseExpected.get('compile.txt')) {
                problems.add("stale ${EXPECTED}/compile.kapt.txt: it equals ${EXPECTED}/compile.txt".toString())
            }
        }
        Set<String> recorded = new LinkedHashSet<>(baseExpected.keySet())
        expectedFiles.values().each { recorded.addAll(it.keySet()) }
        recorded.findAll { it != 'manifest.txt' }.each { String file ->
            languages.each { Language language ->
                String text = expected(language, file)
                if (text == null) {
                    return
                }
                try {
                    Verdict verdict = parseVerdict(text)
                    List<String> lines = text.readLines().collect { it.trim() }.findAll { !it.empty && !it.startsWith('#') }
                    if (!verdict.bound && lines != lines.toSorted()) {
                        problems.add("${EXPECTED}/${file} (${language.dir}) is not sorted".toString())
                    }
                } catch (IllegalArgumentException e) {
                    problems.add("${EXPECTED}/${file} (${language.dir}): ${e.message}".toString())
                }
            }
        }
    }

    @Override
    String toString() {
        return name
    }

}
