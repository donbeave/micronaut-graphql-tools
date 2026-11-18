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
package io.micronaut.graphql.tools.binding

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * The module must stay usable by both adapters of the type model: it reads no Micronaut inject API, and it uses no
 * reflection, so that the compile-time adapter can load it and a native image needs no configuration for it.
 */
class BindingArchitectureSpec extends Specification {

    static final Path SOURCES = Path.of(System.getProperty('binding.sources'))

    static final List<String> FORBIDDEN = [
            'io.micronaut.inject',
            'java.lang.reflect',
            'java.lang.invoke',
            'Class.forName',
            '.getDeclaredMethod',
            '.getDeclaredField',
            '.getMethod(',
            '.getMethods(',
            '.getField(',
            '.getFields(',
            '.getConstructor(',
            '.newInstance(',
            '.setAccessible(',
            'getEnumConstants',
    ]

    void "the sources are found"() {
        expect:
        files().size() >= 20
    }

    void "no class of the module reads #forbidden"() {
        expect:
        offenders(forbidden).isEmpty()

        where:
        forbidden << FORBIDDEN
    }

    void "the code registry is the only public type; every other class is in an internal package"() {
        expect:
        files().findAll { !it.toString().contains('/internal/') && it.fileName.toString() != 'package-info.java' }.collect { it.fileName.toString() } == ['BindingCode.java']
    }

    void "every class is internal API"() {
        expect:
        files().findAll { !it.toFile().text.contains('@Internal') && !(it.fileName.toString() in ['NoAnnotations.java', 'BindingCode.java', 'package-info.java']) }
                .collect { SOURCES.relativize(it).toString() }.isEmpty()
    }

    private static List<Path> files() {
        try (Stream<Path> stream = Files.walk(SOURCES)) {
            return stream.filter { it.toString().endsWith('.java') }.collect(Collectors.toList())
        }
    }

    private static List<String> offenders(String forbidden) {
        files().findAll { path ->
            path.toFile().readLines().any { line ->
                !line.trim().startsWith('*') && !line.trim().startsWith('//') && line.contains(forbidden)
            }
        }.collect { SOURCES.relativize(it).toString() }
    }
}
