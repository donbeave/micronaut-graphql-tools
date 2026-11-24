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
 * Every Java package of the library, binding, processor, scalars, micrometer, opentelemetry, upload and TCK modules is documented by a package-info.java, is small
 * enough to have one responsibility, and is listed in the API map (CONTRIBUTING.md, section API map) with an audience that
 * agrees with the @Internal marker of the package. The checks read the sources, so they hold on every JDK.
 */
class PackageDocumentationSpec extends Specification {

    static final List<String> MODULES = ['graphql-tools', 'graphql-tools-binding', 'graphql-tools-processor', 'graphql-tools-scalars', 'graphql-tools-micrometer', 'graphql-tools-opentelemetry', 'graphql-tools-upload', 'graphql-tools-security', 'graphql-tools-testing', 'test-suite-tck']
    static final int MAX_TYPES = 15

    /** A package over the limit needs an entry here, with the reason and the issue that removes it. */
    static final Map<String, String> OVER_THE_LIMIT = [:]

    @Shared Path root = Paths.get(System.getProperty('docs.root'))
    @Shared Map<String, Map> packages = readPackages()

    def "the packages are found"() {
        expect:
        packages.size() >= 15
        packages.containsKey('io.micronaut.graphql.tools.annotation')
        packages.containsKey('io.micronaut.graphql.tools.binding.internal.rules')
        packages.containsKey('io.micronaut.graphql.tools.visitor')
        packages.containsKey('io.micronaut.graphql.tools.tck')
        packages.containsKey('io.micronaut.graphql.tools.scalars')
        packages.containsKey('io.micronaut.graphql.tools.micrometer')
        packages.containsKey('io.micronaut.graphql.tools.opentelemetry.internal')
        packages.containsKey('io.micronaut.graphql.tools.internal.observer')
        packages.containsKey('io.micronaut.graphql.tools.upload')
    }

    def "every package has a package-info.java with a package statement that matches and a description"() {
        given:
        def problems = []
        packages.each { name, info ->
            if (info.packageInfo == null) {
                problems << "${name}: no package-info.java in ${root.relativize(info.dir)}. Add one with a Javadoc that says what the package is for, who uses it and which types matter."
                return
            }
            def text = Files.readString(info.packageInfo)
            if (!(text =~ /(?m)^package\s+${Pattern.quote(name)}\s*;/)) {
                problems << "${name}: the package statement of ${root.relativize(info.packageInfo)} does not name the package"
            }
            def doc = (text =~ /(?s)\/\*\*(.*?)\*\//)
            def description = doc.find() ? doc.group(1).replaceAll(/(?m)^\s*\*/, '').replaceAll(/\{@\w+\s+([^}]*)}/, '$1').trim() : ''
            if (description.split(/\s+/).length < 8) {
                problems << "${name}: the Javadoc of ${root.relativize(info.packageInfo)} is missing or too short to say what the package is for"
            }
        }

        expect:
        assert problems.empty: "Package documentation gaps:\n  " + problems.join('\n  ')
    }

    def "a package over the type limit is listed with a reason, and the list has no stale entry"() {
        given:
        def over = packages.findAll { name, info -> info.types.size() > MAX_TYPES }.keySet()

        expect:
        assert over.findAll { !OVER_THE_LIMIT.containsKey(it) }.empty:
                "Packages with more than ${MAX_TYPES} types: ${over.findAll { !OVER_THE_LIMIT.containsKey(it) }.toSorted()}. " +
                "Split the package by responsibility, or add it to OVER_THE_LIMIT with a reason and an issue."
        assert OVER_THE_LIMIT.keySet().findAll { !over.contains(it) }.empty:
                "No longer over the limit, remove it from OVER_THE_LIMIT: ${OVER_THE_LIMIT.keySet().findAll { !over.contains(it) }.toSorted()}"
    }

    def "the current layout of the API map lists exactly the packages of the code"() {
        given:
        def listed = apiMap().keySet()
        def existing = packages.keySet()

        expect:
        assert listed.findAll { !existing.contains(it) }.empty: "Listed in the API map, but no such package: ${listed.findAll { !existing.contains(it) }.toSorted()}"
        assert existing.findAll { !listed.contains(it) }.empty: "Missing from the current layout of the API map: ${existing.findAll { !listed.contains(it) }.toSorted()}"
        listed.size() == existing.size()
    }

    def "the audience of the API map agrees with the @Internal on the package"() {
        given:
        def mismatched = apiMap().findAll { name, audience ->
            def info = packages[name]?.packageInfo
            def internal = info != null && (Files.readString(info) =~ /(?m)^@Internal\s*$/).find()
            def hasInternalTypes = packages[name]?.types?.any { (Files.readString(it) =~ /(?m)^@Internal\b/).find() }
            audience.startsWith('Internal') != internal || (!internal && audience.startsWith('Mixed') != hasInternalTypes)
        }.keySet()

        expect:
        assert mismatched.empty: "Audience and @Internal disagree for: ${mismatched.toSorted()}. " +
                "A package whose audience is Internal carries @Internal in package-info.java, and no other package does. " +
                "A package of another audience is Mixed exactly when one of its top-level types is annotated @Internal."
    }

    def "the module column of the API map names the module that holds each package"() {
        given:
        def wrong = apiRows().findAll { name, cells -> packages[name] != null && packages[name].module != cells[1].trim() }.keySet()

        expect:
        assert wrong.empty: "The module column of the API map is wrong for: ${wrong.toSorted()}"
    }

    private Map<String, String> apiMap() {
        apiRows().collectEntries { name, cells -> [(name): cells[2].trim()] }
    }

    private Map<String, List<String>> apiRows() {
        def page = Files.readString(root.resolve('CONTRIBUTING.md'))
        def section = page.split('(?m)^#{2,3} ').find { it.startsWith('Current layout') }
        assert section: 'The API map has no "Current layout" section'
        def rows = section.readLines().findAll { it.startsWith('| `io.micronaut.') }
        rows.collectEntries {
            def cells = it.replaceFirst(/^\|\s*/, '').replaceFirst(/\s*\|\s*$/, '').split(/\s*\|\s*/).toList()
            [(cells[0].replace('`', '').trim()): cells]
        }
    }

    private Map<String, Map> readPackages() {
        def result = new TreeMap<String, Map>()
        MODULES.each { module ->
            def sources = root.resolve(module).resolve('src/main/java')
            if (!Files.isDirectory(sources)) {
                return
            }
            try (Stream<Path> stream = Files.walk(sources)) {
                stream.filter { it.toString().endsWith('.java') }.collect(Collectors.toList()).groupBy { it.parent }.each { dir, files ->
                    def name = sources.relativize(dir).iterator().collect { it.toString() }.join('.')
                    def foreign = files.findAll { !(Files.readString(it) =~ /(?m)^package\s+${Pattern.quote(name)}\s*;/).find() }
                    assert foreign.empty: "The package statement of ${foreign.collect { root.relativize(it) }} does not match its directory ${name}"
                    result[name] = [
                            module     : module,
                            dir        : dir,
                            packageInfo: files.find { it.fileName.toString() == 'package-info.java' },
                            types      : files.findAll { it.fileName.toString() != 'package-info.java' },
                    ]
                }
            }
        }
        result
    }
}
