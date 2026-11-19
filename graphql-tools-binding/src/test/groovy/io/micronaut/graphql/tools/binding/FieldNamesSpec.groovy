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

import io.micronaut.graphql.tools.binding.internal.rules.FieldNames
import spock.lang.Specification

class FieldNamesSpec extends Specification {

    void "a member binds the declared name, else its own name"() {
        expect:
        FieldNames.ofMember('klass', 'class') == 'class'
        FieldNames.ofMember('title', '') == 'title'
        FieldNames.ofMember('title', null) == 'title'
    }

    void "a resolver method #method binds #names (accessor prefixes #prefixes)"() {
        expect:
        FieldNames.ofResolverMethod(method, prefixes) == names

        where:
        method        | prefixes | names
        'author'      | true     | ['author']
        'getAuthor'   | false    | ['getAuthor']
        'getAuthor'   | true     | ['getAuthor', 'author']
        'isActive'    | true     | ['isActive', 'active']
        'getURL'      | true     | ['getURL', 'URL']
        'get'         | true     | ['get']
        'getaway'     | true     | ['getaway']
        'issue'       | true     | ['issue']
        'is'          | true     | ['is']
    }

    void "the property an accessor reads or writes is #property"() {
        expect:
        FieldNames.accessedProperty(method, parameters, returnsVoid, returnsBoolean) == property

        where:
        method      | parameters | returnsVoid | returnsBoolean | property
        'getTitle'  | 0          | false       | false          | 'title'
        'isActive'  | 0          | false       | true           | 'active'
        'isName'    | 0          | false       | false          | null
        'getName'   | 0          | true        | false          | null
        'setTitle'  | 1          | true        | false          | 'title'
        'setTitle'  | 0          | true        | false          | null
        'getTitle'  | 1          | false       | false          | null
        'setup'     | 1          | true        | false          | null
        'title'     | 0          | false       | false          | null
    }
}
