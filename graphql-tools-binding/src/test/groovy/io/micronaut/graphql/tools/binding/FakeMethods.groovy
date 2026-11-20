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

import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames
import io.micronaut.graphql.tools.binding.internal.model.AnnotationView
import io.micronaut.graphql.tools.binding.internal.model.JClass
import io.micronaut.graphql.tools.binding.internal.model.JMethod
import io.micronaut.graphql.tools.binding.internal.model.JParameter
import io.micronaut.graphql.tools.binding.internal.model.Nullability
import io.micronaut.graphql.tools.binding.internal.model.TypeModel
import io.micronaut.graphql.tools.binding.internal.model.TypeRef

/** Methods, types and a type model written by hand, for the rules that read return types and method annotations. */
class FakeMethods {

    static final String STAGE = 'java.util.concurrent.CompletionStage'
    static final String PUBLISHER = 'org.reactivestreams.Publisher'
    static final String RESULT = 'graphql.execution.DataFetcherResult'
    static final String OPTIONAL = 'java.util.Optional'

    static TypeRef ref(String name, TypeRef... arguments) {
        new TypeRef(name, arguments as List, Nullability.UNKNOWN, false, false)
    }

    static TypeRef array(String name, boolean primitive = false) {
        new TypeRef(name, [], Nullability.UNKNOWN, primitive, true)
    }

    static TypeRef primitive(String name) {
        new TypeRef(name, [], Nullability.UNKNOWN, true, false)
    }

    /** @param supertypes the supertypes by class name; a class is assignable to itself and to these */
    static TypeModel model(Map<String, List<String>> supertypes = [:]) {
        [resolve: { TypeRef type ->
            [name: { type.rawName() },
             isAssignableTo: { String other -> type.rawName() == other || supertypes.get(type.rawName(), []).contains(other) }] as JClass
        }] as TypeModel
    }

    /** @param values the members of the annotations that the method carries, by annotation name */
    static AnnotationView view(Map<String, Map<String, String>> values) {
        [has: { String name -> values.containsKey(name) },
         stringValue: { String name, String member -> Optional.ofNullable(values.get(name)?.get(member)) },
         classValues: { String name, String member -> [] }] as AnnotationView
    }

    static JMethod method(String name, Map<String, Map<String, String>> annotations = [:], List<JParameter> parameters = [],
                          TypeRef returns = ref('java.lang.String'), String signature = null, boolean suspend = false) {
        String sig = signature ?: "a.R#${name}()".toString()
        [name: { name }, signature: { sig }, parameters: { parameters }, declaringType: { [name: { sig.substring(0, sig.indexOf('#')) }] as JClass },
         genericReturnType: { returns }, suspend: { suspend }, annotations: { view(annotations) }] as JMethod
    }

    static Map<String, Map<String, String>> coordinate(String coordinate) {
        [(AnnotationNames.SCHEMA_COORDINATE): [value: coordinate]]
    }

    static final Map<String, Map<String, String>> IGNORED = [(AnnotationNames.IGNORE): [:]]
}
