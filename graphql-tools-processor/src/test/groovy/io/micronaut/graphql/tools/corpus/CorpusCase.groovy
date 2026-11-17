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
 *   case.properties            description, tags, environments, config.&lt;key&gt; (the runtime configuration)
 *   schema.graphqls            one or more *.graphqls files
 *   java/ groovy/ kotlin/      the sources of each language the case covers, as packages
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
    static final List<String> EXPECTED_FILES = ['runtime.txt', 'compile.txt', 'manifest.txt']
    static final Set<String> PROPERTY_KEYS = ['description', 'tags', 'environments'] as Set<String>
    static final String CONFIG_PREFIX = 'config.'

    final String name
    final Path dir
    final String description
    final List<String> tags
    final List<String> environments
    final Map<String, String> config
    final List<String> problems = []

    private final Map<Language, Map<String, String>> expectedFiles = [:]
    private final Map<String, String> baseExpected = [:]

    private CorpusCase(String name, Path dir, Properties properties) {
        this.name = name
        this.dir = dir
        this.description = properties.getProperty('description', '')
        this.tags = split(properties.getProperty('tags', ''))
        this.environments = split(properties.getProperty('environments', ''))
        Map<String, String> config = new TreeMap<>()
        properties.stringPropertyNames().sort().each { String key ->
            if (key.startsWith(CONFIG_PREFIX)) {
                config.put(key.substring(CONFIG_PREFIX.length()), properties.getProperty(key))
            } else if (!PROPERTY_KEYS.contains(key)) {
                problems.add("${PROPERTIES}: unknown key '${key}' (known: ${PROPERTY_KEYS.join(', ')}, config.<key>)".toString())
            }
        }
        this.config = Collections.unmodifiableMap(config)
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

    /** @return the languages of the case, in the order of {@link Language} */
    List<Language> languages() {
        return Language.values().findAll { Files.isDirectory(dir.resolve(it.dir)) }.toList()
    }

    List<Path> sources(Language language) {
        Path root = dir.resolve(language.dir)
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
                    Files.isRegularFile(entry) && file.endsWith('.graphqls')
            if (!known) {
                problems.add("orphan entry '${file}' (a case holds ${PROPERTIES}, *.graphqls, ${languageDirs.join('/')} and ${EXPECTED})".toString())
            }
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
