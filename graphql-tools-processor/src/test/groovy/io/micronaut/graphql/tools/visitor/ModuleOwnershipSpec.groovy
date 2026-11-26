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
package io.micronaut.graphql.tools.visitor

import io.micronaut.inject.visitor.VisitorContext
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * The answer about the module of a compilation is asked of the compiler once, however many visitors ask: KSP creates a file of its
 * own to answer the question about the project directory and the class output, and a second request for it fails the build.
 */
class ModuleOwnershipSpec extends Specification {

    @TempDir
    Path work

    private Map<String, Integer> calls
    private Map<String, Object> attributes

    private VisitorContext context(Path project, Path output) {
        calls = [projectDir: 0, classesOutputPath: 0]
        attributes = [:]
        return [
                getProjectDir       : { -> calls.projectDir++; Optional.ofNullable(project) },
                getClassesOutputPath: { -> calls.classesOutputPath++; Optional.ofNullable(output) },
                get                 : { Object... arguments -> Optional.ofNullable(attributes.get(arguments[0].toString())) },
                put                 : { Object... arguments -> attributes.put(arguments[0].toString(), arguments[1]) },
        ] as VisitorContext
    }

    def "the compiler is asked once for the project directory and once for the class output, however many times the question is put"() {
        given:
        def context = context(work, Files.createDirectories(work.resolve('build/classes')))

        when:
        def first = ModuleOwnership.of(null, context)
        def second = ModuleOwnership.of(null, context)

        then:
        first.is(second)
        first.moduleDir() == work.toAbsolutePath().normalize()
        first.foreignOutput() == null
        calls == [projectDir: 1, classesOutputPath: 1]
    }

    def "an output outside the module directory is the foreign output, and the option is the module directory when it is set"() {
        given:
        def other = work.resolve('other')
        def context = context(null, work.resolve('build/classes'))

        when:
        def ownership = ModuleOwnership.of(other, context)

        then:
        ownership.moduleDir() == other
        ownership.foreignOutput() == work.resolve('build/classes').toAbsolutePath().normalize()
        calls.projectDir == 0
    }

    def "a context that keeps no attributes is asked each time, and the answer is the same"() {
        given:
        def calls = [classesOutputPath: 0]
        def classes = Files.createDirectories(work.resolve('classes'))
        def context = [
                getProjectDir       : { -> Optional.of(work) },
                getClassesOutputPath: { -> calls.classesOutputPath++; Optional.of(classes) },
        ] as VisitorContext

        expect:
        ModuleOwnership.of(null, context).foreignOutput() == null
        ModuleOwnership.of(null, context).foreignOutput() == null
        calls.classesOutputPath == 2
    }
}
