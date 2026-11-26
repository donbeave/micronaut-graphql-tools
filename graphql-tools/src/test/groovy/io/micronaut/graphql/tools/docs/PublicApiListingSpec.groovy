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

import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * The reviewed list of the public API (src/main/docs/guide/publicApi.adoc) names the public top-level types of every published
 * module, and the types that carry @Experimental. The checks read the sources, so they hold on every JDK.
 */
class PublicApiListingSpec extends Specification {

    static final Pattern DECLARATION = Pattern.compile('(?m)^((?:public|protected|private|static|final|abstract|sealed|non-sealed|strictfp)\\s+)*(@interface|class|interface|enum|record)\\s+(\\w+)')

    @Shared Path root = Paths.get(System.getProperty('docs.root'))
    @Shared List<String> modules = readModules()
    @Shared Map<String, String> moduleOfCode = [:]
    @Shared Map<String, String> moduleOnPage = [:]
    @Shared List<String> duplicatePackages = []
    @Shared Map<String, Map<String, Boolean>> code = readCode()
    @Shared Map<String, Map<String, Boolean>> listed = readPage()

    def "the modules are every graphql-tools module that has Java sources"() {
        expect:
        modules.containsAll(['graphql-tools', 'graphql-tools-binding', 'graphql-tools-processor', 'graphql-tools-scalars', 'graphql-tools-micrometer', 'graphql-tools-opentelemetry', 'graphql-tools-upload', 'graphql-tools-security', 'graphql-tools-testing'])
        !modules.contains('test-suite-tck')
    }

    def "the sources have public types"() {
        expect:
        code.size() >= 15
        code['io.micronaut.graphql.tools.annotation'].containsKey('GraphQLField')
        code['io.micronaut.graphql.tools.execution']['GraphQLTransport']
    }

    def "the page lists exactly the public types of the code, package by package"() {
        given:
        def problems = []
        (code.keySet() + listed.keySet()).unique().sort().each { name ->
            def inCode = code[name]?.keySet() ?: [] as Set
            def onPage = listed[name]?.keySet() ?: [] as Set
            (inCode - onPage).each { problems << "${name}.${it} is public in the code and not on the page" }
            (onPage - inCode).each { problems << "${name}.${it} is on the page and is not a public type of the code" }
        }

        expect:
        assert problems.empty: "The Public API page and the code disagree:\n  " + problems.sort().join('\n  ') +
                "\nA new public type goes on src/main/docs/guide/publicApi.adoc in the same change, and a type that is not API gets @Internal."
    }

    def "a package is listed once, under the module that holds it"() {
        given:
        def wrong = moduleOnPage.findAll { name, module -> moduleOfCode[name] != null && moduleOfCode[name] != module }
                .collect { name, module -> "${name} is under ${module} on the page, and in ${moduleOfCode[name]} in the code".toString() }

        expect:
        assert duplicatePackages.empty: "Listed twice on the Public API page: ${duplicatePackages}"
        assert wrong.empty: "The module headings of the Public API page are wrong:\n  " + wrong.join('\n  ')
    }

    def "the experimental marks of the page are the @Experimental annotations of the code"() {
        given:
        def marked = { Map<String, Map<String, Boolean>> source ->
            source.collectMany { name, types -> types.findAll { it.value }.keySet().collect { "${name}.${it}".toString() } }.toSorted()
        }

        expect:
        marked(code) == marked(listed)
        marked(code).size() == 5
    }

    private Map<String, Map<String, Boolean>> readPage() {
        Map<String, Map<String, Boolean>> result = new TreeMap<>()
        String module = null
        Files.readAllLines(root.resolve('src/main/docs/guide/publicApi.adoc')).each { line ->
            def heading = line =~ /^=== (micronaut-)?(graphql-tools\S*)$/
            if (heading.find()) {
                module = heading.group(2)
                return
            }
            if (!line.startsWith('|`io.micronaut.')) {
                return
            }
            def cells = line.substring(1).split(/\s*\|\s*/, 2).toList()
            def name = cells[0].replace('`', '').trim()
            if (result.containsKey(name)) {
                duplicatePackages << name
            }
            moduleOnPage[name] = module
            def types = [:]
            cells[1].split(',').collect { it.trim() }.each { item ->
                def m = item =~ /^`(\w+)`( \(experimental\))?$/
                assert m.matches(): "Bad type cell in ${name}: ${item}"
                types[m.group(1)] = m.group(2) != null
            }
            result[name] = types
        }
        result
    }

    private List<String> readModules() {
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter { it.fileName.toString().startsWith('graphql-tools') && Files.isDirectory(it.resolve('src/main/java')) }
                    .map { it.fileName.toString() }.sorted().collect(Collectors.toList())
        }
    }

    private Map<String, Map<String, Boolean>> readCode() {
        Map<String, Map<String, Boolean>> result = new TreeMap<>()
        modules.each { module ->
            def sources = root.resolve(module).resolve('src/main/java')
            if (!Files.isDirectory(sources)) {
                return
            }
            try (Stream<Path> stream = Files.walk(sources)) {
                stream.filter { it.toString().endsWith('.java') && it.fileName.toString() != 'package-info.java' }.collect(Collectors.toList()).each { file ->
                    def name = sources.relativize(file.parent).iterator().collect { it.toString() }.join('.')
                    if ('internal' in name.split('\\.').toList()) {
                        return
                    }
                    def text = Files.readString(file)
                    def typeName = file.fileName.toString().replaceFirst(/\.java$/, '')
                    def declaration = DECLARATION.matcher(text)
                    boolean found = false
                    while (!found && declaration.find()) {
                        found = declaration.group(3) == typeName
                    }
                    assert found: "No declaration of the type ${typeName} in ${root.relativize(file)}"
                    def header = text.substring(0, declaration.start()).split('\\*/|(?m:^import [^\\n]*$)|(?m:^package [^\\n]*$)').last()
                    def modifiers = declaration.group(0)
                    def annotations = (header =~ /@(\w+)/).collect { it[1] }
                    if (modifiers.startsWith('public') && !('Internal' in annotations)) {
                        moduleOfCode[name] = module
                        result.computeIfAbsent(name) { new TreeMap<String, Boolean>() }[declaration.group(3)] = 'Experimental' in annotations
                    }
                }
            }
        }
        result
    }

}
