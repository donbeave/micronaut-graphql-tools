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
package io.micronaut.graphql.tools.docs

import groovy.json.JsonSlurper
import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import spock.lang.Shared
import spock.lang.Specification

import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

class DocsCoverageSpec extends Specification {

    static final String KEY_PREFIX = 'micronaut.graphql.tools.'
    static final String GUIDE = 'src/main/docs/guide'
    static final Pattern ENTRY = Pattern.compile('^(exception|annotation|config-key):(\\S+)\\s+#\\s+(.+\\(#\\d+\\))$')
    static final Pattern SNIPPET = Pattern.compile('^snippet::(\\S+?)\\[(.*)]\\s*$')
    static final Map<String, List<String>> SUITES = [
            java  : ['test-suite-java', 'java', 'java'],
            kotlin: ['test-suite-kotlin', 'kotlin', 'kt'],
            groovy: ['test-suite-groovy', 'groovy', 'groovy']
    ]

    @Shared Path root = findRoot()
    @Shared String docText = docFiles().collect { Files.readString(it) }.join('\n')
    @Shared Map<String, Map<String, String>> allowlist = readAllowlist()

    def "every configuration key is documented"() {
        given:
        def keys = configurationKeys()

        expect:
        keys.size() >= 12
        failures('config-key', keys, 'README.md or the guide page src/main/docs/guide/configuration.adoc').empty
    }

    def "every public exception is documented"() {
        given:
        def names = publicClasses('exceptions', Throwable)

        expect:
        names == [SchemaBindingException.simpleName]
        failures('exception', names, 'the error reference: src/main/docs/guide/exceptions.adoc (or README.md)').empty
    }

    def "the error reference names the base class and the accessor of the code"() {
        given:
        def page = Files.readString(root.resolve(GUIDE + '/exceptions.adoc'))

        expect:
        SchemaBindingException.superclass == io.micronaut.context.exceptions.ConfigurationException
        SchemaBindingException.declaredMethods*.name.contains('getCode')
        !SchemaBindingException.declaredMethods*.name.contains('code')
        page.contains('ConfigurationException')
        page.contains('SchemaBindingException#getCode()')
        Files.readString(root.resolve(GUIDE + '/migration/fromSnapshot.adoc')).contains('BC-41')
        Files.readString(root.resolve(GUIDE + '/breaks.adoc')).contains('BC-41')
    }

    def "the configuration reference lists every check code"() {
        given:
        def page = Files.readString(root.resolve(GUIDE + '/configuration/checkSeverity.adoc'))

        expect:
        BindingCode.values().findAll { it.isCheck() }.every { page.contains('`' + it.getCode() + '`') }
    }

    def "every public annotation is documented"() {
        given:
        def names = publicClasses('annotation', java.lang.annotation.Annotation)

        expect:
        names.contains(GraphQLField.simpleName)
        failures('annotation', names, 'a guide page under src/main/docs/guide (or README.md)').empty
    }

    def "every snippet exists with its tags in the Java, Kotlin and Groovy suites"() {
        given:
        def problems = []
        int checked = 0
        guidePages().each { page ->
            Files.readAllLines(page).eachWithIndex { line, i ->
                def m = SNIPPET.matcher(line)
                if (!m.matches()) {
                    return
                }
                def attrs = parseAttributes(m.group(2))
                if (attrs.containsKey('project')) {
                    return
                }
                checked++
                def where = "${root.relativize(page)}:${i + 1}"
                def source = attrs.source ?: 'test'
                def tags = (attrs.tags ?: '').split(',').collect { it.trim() }.findAll { it }
                SUITES.each { language, suite ->
                    def base = attrs['project-base'] ?: 'test-suite'
                    def file = root.resolve("${base}-${language}/src/${source}/${suite[1]}/${m.group(1).replace('.', '/')}.${suite[2]}")
                    if (!Files.isRegularFile(file)) {
                        problems << "${where}: no ${language} file ${root.relativize(file)} for snippet ${m.group(1)}"
                        return
                    }
                    def content = Files.readString(file)
                    tags.each { tag ->
                        if (!content.contains("tag::${tag}[]") || !content.contains("end::${tag}[]")) {
                            problems << "${where}: ${root.relativize(file)} has no tag ${tag} (tag::${tag}[] and end::${tag}[])"
                        }
                    }
                }
            }
        }

        expect:
        checked > 0
        assert problems.empty : "Snippet gaps (fix the page or add the file or tag in all three suites):\n  " + problems.join('\n  ')
    }

    def "the allowlist has only live entries"() {
        given:
        def stale = []
        def configurationKeys = configurationKeys()
        def exceptions = publicClasses('exceptions', Throwable)
        def annotations = publicClasses('annotation', java.lang.annotation.Annotation)
        allowlist.each { key, entry ->
            def kind = entry.kind
            def name = entry.name
            def universe = [exception: exceptions, annotation: annotations, 'config-key': configurationKeys][kind]
            if (!universe.contains(name)) {
                stale << "${key}: no such public ${kind} any more; delete the entry"
            } else if (isDocumented(kind, name)) {
                stale << "${key}: is documented now; delete the entry"
            }
        }

        expect:
        assert stale.empty : "Stale entries in docs-coverage-allowlist.txt:\n  " + stale.join('\n  ')
    }

    private List<String> failures(String kind, Collection<String> names, String page) {
        def missing = names.findAll { !allowlist.containsKey("${kind}:${it}".toString()) && !isDocumented(kind, it) }
        assert missing.empty: "The ${kind} ${missing.size() == 1 ? 'is' : 'items are'} not documented: ${missing.sort().join(', ')}. " +
                "Mention ${missing.size() == 1 ? 'it' : 'each'} in ${page}. " +
                "Only a deliberate decision with a tracker issue allows a gap: add it to docs-coverage-allowlist.txt."
        missing
    }

    private boolean isDocumented(String kind, String name) {
        def quoted = Pattern.quote(name)
        def pattern = kind == 'config-key' ?
                Pattern.compile("(?<![\\w.-])${quoted}(?![\\w-]|\\.[\\w-])") :
                Pattern.compile("(?<!\\w)${quoted}(?!\\w)")
        pattern.matcher(docText).find()
    }

    private static List<String> configurationKeys() {
        def urls = Thread.currentThread().contextClassLoader.getResources('META-INF/spring-configuration-metadata.json').toList()
        def keys = urls.collectMany { url ->
            new JsonSlurper().parse(url).properties.collect { it.name }
        }.findAll { it.startsWith(KEY_PREFIX) }
        assert !keys.empty: 'No generated configuration metadata found for ' + KEY_PREFIX
        keys.unique().sort()
    }

    private static List<String> publicClasses(String subPackage, Class<?> type) {
        def location = Paths.get(GraphQLField.protectionDomain.codeSource.location.toURI())
        assert Files.isDirectory(location): "Expected the main classes in a directory, not ${location}"
        def packageName = "io.micronaut.graphql.tools.${subPackage}"
        def dir = location.resolve(packageName.replace('.', '/'))
        Files.list(dir).withCloseable { stream ->
            stream.map { it.fileName.toString() }
                    .filter { it.endsWith('.class') && !it.contains('$') && it != 'package-info.class' }
                    .map { it.substring(0, it.length() - '.class'.length()) }
                    .map { Class.forName("${packageName}.${it}") }
                    .filter { Modifier.isPublic(it.modifiers) && type.isAssignableFrom(it) }
                    .map { it.simpleName }
                    .toList()
        }.toSorted()
    }

    private List<Path> guidePages() {
        Files.walk(root.resolve(GUIDE)).withCloseable { stream ->
            stream.filter { it.toString().endsWith('.adoc') && Files.isRegularFile(it) }.sorted().toList()
        }
    }

    private List<Path> docFiles() {
        guidePages() + [root.resolve('README.md')]
    }

    private static Map<String, String> parseAttributes(String text) {
        def result = [:]
        def m = Pattern.compile('([\\w-]+)="([^"]*)"').matcher(text)
        while (m.find()) {
            result[m.group(1)] = m.group(2)
        }
        result
    }

    private Map<String, Map<String, String>> readAllowlist() {
        def lines = getClass().getResourceAsStream('/docs-coverage-allowlist.txt').getText('UTF-8').readLines()
        def result = [:]
        lines.findAll { it.trim() && !it.startsWith('#') }.each { line ->
            def m = ENTRY.matcher(line)
            assert m.matches(): "Bad allowlist line (want 'kind:name # reason (#issue)'): ${line}"
            result["${m.group(1)}:${m.group(2)}".toString()] = [kind: m.group(1), name: m.group(2)]
        }
        result
    }

    private static Path findRoot() {
        def dir = System.getProperty('docs.root')
        assert dir: 'The docs.root system property is not set; the build sets it for the test task'
        Paths.get(dir)
    }
}
