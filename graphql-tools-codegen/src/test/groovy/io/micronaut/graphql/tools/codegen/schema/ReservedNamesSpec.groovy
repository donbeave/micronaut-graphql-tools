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

import io.micronaut.graphql.tools.codegen.Language
import spock.lang.Specification

class ReservedNamesSpec extends Specification {

    def "the Java keywords of the rules are the ones that the options use for a package or class name"() {
        expect:
        ReservedNames.JAVA == Class.forName('io.micronaut.graphql.tools.codegen.JavaNames').RESERVED
    }

    def "a Kotlin or Groovy keyword is reserved only for that language"() {
        expect:
        ReservedNames.languageOf(Language.JAVA, 'class') == 'Java'
        ReservedNames.languageOf(Language.KOTLIN, 'class') == 'Java'
        ReservedNames.languageOf(Language.KOTLIN, 'fun') == 'Kotlin'
        ReservedNames.languageOf(Language.GROOVY, 'def') == 'Groovy'
        ReservedNames.languageOf(Language.JAVA, 'fun') == null
        ReservedNames.languageOf(Language.JAVA, 'name') == null
    }
}
