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
package example.validated

import example.validated.generated.Generated
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import spock.lang.Specification

// The classes that @GraphQLModels lists keep their introspections when Micronaut Validation's processor runs in the same
// compilation (#528).
class ValidatedModelsSpec extends Specification {

    private static int introspections(Class<?> type) {
        BeanIntrospector.SHARED.findIntrospections { it.beanType == type }.size()
    }

    def "a listed class is introspected"() {
        expect:
        BeanIntrospection.getIntrospection(Plain).getProperty('name').isPresent()
        BeanIntrospection.getIntrospection(Listed).getProperty('label').isPresent()
    }

    def "the classes of a listed package are introspected"() {
        expect:
        BeanIntrospection.getIntrospection(Generated).getProperty('id').isPresent()
    }

    def "a direct Introspected keeps working"() {
        expect:
        introspections(Direct) == 1
    }

    def "a class listed twice is introspected once"() {
        expect:
        introspections(Twice) == 1
    }
}
