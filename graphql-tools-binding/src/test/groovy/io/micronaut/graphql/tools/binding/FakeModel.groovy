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

import io.micronaut.graphql.tools.binding.internal.model.AnnotationView
import io.micronaut.graphql.tools.binding.internal.model.JClass
import io.micronaut.graphql.tools.binding.internal.model.JEnumConstant
import io.micronaut.graphql.tools.binding.internal.model.JParameter
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames
import io.micronaut.graphql.tools.binding.internal.model.JProperty
import io.micronaut.graphql.tools.binding.internal.model.TypeRef

/** A hand-written type model: the rules are tested without a compiler and without a bean context. */
class FakeModel {

    static AnnotationView annotated(String... names) {
        def set = names as Set
        [has: { String it -> set.contains(it) },
         stringValue: { String a, String m -> Optional.empty() },
         classValues: { String a, String m -> [] }] as AnnotationView
    }

    static JProperty property(String name, String... annotations) {
        [name: { name }, type: { TypeRef.of('java.lang.String') }, annotations: { annotated(annotations) },
         accessorText: { 'get' + name.capitalize() + '()' }] as JProperty
    }

    static JClass enumClass(String name, Map<String, List<String>> constants) {
        [name: { name }, enumConstants: {
            constants.collect { constant, annotations -> new JEnumConstant(constant, annotated(annotations as String[])) }
        }] as JClass
    }

    static JClass inputClass(String name, List<JProperty> properties) {
        [name: { name }, writableProperties: { properties }] as JClass
    }

    static JParameter parameter(String name, String type, String argument = null) {
        def view = [has: { String it -> argument != null && it == AnnotationNames.ARGUMENT },
                    stringValue: { String a, String m ->
                        argument != null && a == AnnotationNames.ARGUMENT && m == 'value' ? Optional.of(argument) : Optional.empty()
                    },
                    classValues: { String a, String m -> [] }] as AnnotationView
        [name: { name }, nameIsSynthetic: { false }, type: { TypeRef.of(type) }, annotations: { view }] as JParameter
    }
}
