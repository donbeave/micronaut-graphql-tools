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

import spock.lang.Specification
import spock.lang.Unroll

class ConstantNameSpec extends Specification {

    @Unroll
    def "the identifier of the schema name #name is #identifier"() {
        expect:
        Lowering.constantName(name) == identifier

        where:
        name         | identifier
        'fooBar'     | 'FOO_BAR'
        'foo_bar'    | 'FOO_BAR'
        'HTTPStatus' | 'HTTP_STATUS'
        'v2Api'      | 'V2_API'
        'ID'         | 'ID'
        '_id'        | '_ID'
    }
}
