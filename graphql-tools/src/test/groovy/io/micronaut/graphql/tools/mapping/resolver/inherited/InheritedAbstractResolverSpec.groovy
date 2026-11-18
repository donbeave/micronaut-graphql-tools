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
package io.micronaut.graphql.tools.mapping.resolver.inherited

import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.BindingManifest

class InheritedAbstractResolverSpec extends AbstractTest {

    void "the implementation of an abstract resolver method binds the field, once"() {
        given:
        startContext('schema { query: Query }\ntype Query { hello: String }', 'InheritedAbstractResolverSpec')

        when:
        def result = executeQuery('{ hello }')

        then:
        result.errors.isEmpty()
        result.data == [hello: 'hello']
        applicationContext.getBean(BindingManifest).lines.contains('Query.hello: String -> root ; '
                + 'io.micronaut.graphql.tools.mapping.resolver.inherited.JavaRootResolverImplementation#hello() ; '
                + 'java.lang.String ; java.lang.String')
    }

}
