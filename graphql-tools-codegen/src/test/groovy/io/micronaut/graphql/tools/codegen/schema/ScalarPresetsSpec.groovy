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
package io.micronaut.graphql.tools.codegen.schema

import io.micronaut.graphql.tools.binding.internal.schema.ScalarCatalog
import io.micronaut.graphql.tools.scalars.StandardScalar
import spock.lang.Specification

class ScalarPresetsSpec extends Specification {

    static final List<String> LIBRARY_EXTENSIONS = ['Long', 'Short', 'BigDecimal', 'BigInteger']

    def "every standard scalar has the preset of its class, and the presets hold nothing else"() {
        given:
        Map<String, String> expected = StandardScalar.values().collectEntries { [(it.defaultName): it.javaClass.name] }

        expect:
        expected.size() == StandardScalar.values().length
        ScalarPresets.presets().findAll { name, className -> expected.containsKey(name) } == expected
        ScalarPresets.presets().keySet() - expected.keySet() as Set == (LIBRARY_EXTENSIONS + 'Upload') as Set
    }

    def "the presets equal the standard scalars of the runtime catalog"() {
        expect:
        ScalarCatalog.standardScalars.every { name, type -> ScalarPresets.presets()[name] == type.name }
    }

    def "the presets of the library extensions are the boxed classes that the runtime catalog accepts"() {
        expect:
        LIBRARY_EXTENSIONS.every { name ->
            def accepted = ScalarCatalog.getSupportedClasses(new graphql.language.TypeName(name))
            def preset = Class.forName(ScalarPresets.presets()[name])
            accepted.contains(preset) && accepted.every { it.primitive || it == preset }
        }
    }

    def "the presets are sorted and cannot be changed"() {
        when:
        ScalarPresets.presets().put('X', 'a.B')

        then:
        thrown(UnsupportedOperationException)
        ScalarPresets.presets().keySet() as List == ScalarPresets.presets().keySet().sort(false)
    }
}
