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
package io.micronaut.graphql.tools.security

import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** The guide page names the configuration key, the context key, the error codes and the types of the module. */
class DocsPageSpec extends Specification {

    @Shared Path root = Paths.get(System.getProperty('docs.root'))
    @Shared String page = Files.readString(root.resolve('src/main/docs/guide/security.adoc'))

    void "the page names #name"() {
        expect:
        page.contains(name)

        where:
        name << ["${GraphQLSecurity.PREFIX}.enabled".toString(), 'GraphQLSecurity.AUTHENTICATION_KEY', GraphQLSecurity.UNAUTHENTICATED_CODE,
                 GraphQLSecurity.FORBIDDEN_CODE, 'RootFieldsHook', 'AbortExecutionException', '@Secured', 'SecurityService', 'Authentication']
    }

}
