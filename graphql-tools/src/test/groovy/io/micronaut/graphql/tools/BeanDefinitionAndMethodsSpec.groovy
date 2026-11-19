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

import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification

class BeanDefinitionAndMethodsSpec extends Specification {

    private ExecutableMethod<Object, ?> method(String methodName, boolean abstractMethod, Class<?>... argumentTypes) {
        Stub(ExecutableMethod) {
            getMethodName() >> methodName
            isAbstract() >> abstractMethod
            getArgumentTypes() >> argumentTypes
        }
    }

    void "an abstract method alone is kept"() {
        given:
        def item = new BeanDefinitionAndMethods(Stub(BeanDefinition))
        def hello = method('hello', true)

        when:
        item.addExecutableMethod(hello)

        then:
        item.executableMethods == [hello] as Set
    }

    void "a concrete method replaces an abstract one with the same signature, in either order"() {
        given:
        def item = new BeanDefinitionAndMethods(Stub(BeanDefinition))
        def concrete = method('hello', false, String)

        when:
        order.each { item.addExecutableMethod(it == 'abstract' ? method('hello', true, String) : concrete) }

        then:
        item.executableMethods == [concrete] as Set

        where:
        order << [['abstract', 'concrete'], ['concrete', 'abstract']]
    }

    void "abstract and concrete methods with different signatures are both kept"() {
        given:
        def item = new BeanDefinitionAndMethods(Stub(BeanDefinition))

        when:
        item.addExecutableMethod(method('hello', true, String))
        item.addExecutableMethod(method('hello', false, Integer))

        then:
        item.executableMethods.size() == 2
    }

}
