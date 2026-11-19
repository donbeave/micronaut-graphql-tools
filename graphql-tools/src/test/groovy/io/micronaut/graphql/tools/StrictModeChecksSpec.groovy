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
package io.micronaut.graphql.tools

import spock.lang.Specification
import io.micronaut.graphql.tools.internal.StrictModeChecks

class StrictModeChecksSpec extends Specification {

    def "the fixed set is the public methods of Object on this JDK"() {
        expect:
        StrictModeChecks.OBJECT_METHOD_SIGNATURES == Object.class.getMethods()
                .collect { StrictModeChecks.signature(it.name, it.parameterTypes) } as Set
    }

    def "the fixed set answers the same as Object.class.getMethod for every name and parameter list"() {
        given:
        def names = (Object.class.getDeclaredMethods()*.name + Object.class.getMethods()*.name
                + ['missing', 'finalize', 'clone']) as Set
        def parameterLists = [[], [Object], [String], [int], [long], [long, int], [int, long], [Object, Object]]

        when:
        def mismatches = []
        names.each { String name ->
            parameterLists.each { List<Class<?>> parameters ->
                Class<?>[] types = parameters as Class[]
                boolean reflective
                try {
                    Object.class.getMethod(name, types)
                    reflective = true
                } catch (NoSuchMethodException ignored) {
                    reflective = false
                }
                if (StrictModeChecks.isObjectSignature(name, types) != reflective) {
                    mismatches << StrictModeChecks.signature(name, types)
                }
            }
        }

        then:
        mismatches.isEmpty()
    }

    def "a resolver method that Object does not declare publicly is no Object method"() {
        expect:
        !StrictModeChecks.isObjectSignature(name, types as Class[])

        where:
        name        | types
        'finalize'  | []
        'clone'     | []
        'equals'    | [String]
        'wait'      | [int]
        'wait'      | [long, long]
        'toString'  | [int]
        'hashCode'  | [Object]
        'getClass'  | [Object]
    }

    def "the public methods of Object are Object methods"() {
        expect:
        StrictModeChecks.isObjectSignature(name, types as Class[])

        where:
        name        | types
        'equals'    | [Object]
        'hashCode'  | []
        'toString'  | []
        'getClass'  | []
        'notify'    | []
        'notifyAll' | []
        'wait'      | []
        'wait'      | [long]
        'wait'      | [long, int]
    }
}
