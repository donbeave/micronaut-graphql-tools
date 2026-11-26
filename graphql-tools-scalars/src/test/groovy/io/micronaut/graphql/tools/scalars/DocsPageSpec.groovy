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
package io.micronaut.graphql.tools.scalars

import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** The table of the guide page lists every scalar with the id, the name and the Java class of the code. */
class DocsPageSpec extends Specification {

    @Shared Path root = Paths.get(System.getProperty('docs.root'))
    @Shared String page = Files.readString(root.resolve('src/main/docs/guide/scalars.adoc'))

    void "the table has a row for #scalar.id with its name and Java class"() {
        expect:
        page.readLines().any {
            it.startsWith("| `${scalar.getId()}` | `${scalar.getDefaultName()}` | `${scalar.getJavaClass().name}` |")
        }

        where:
        scalar << StandardScalar.values().toList()
    }

    void "the table has no row for a scalar that does not exist"() {
        given:
        def ids = page.readLines().findAll { it ==~ /^\| `[a-z-]+` \| `\w+` \| `[\w.]+` \|.*/ }
                .collect { it.split(/ \| /)[0].replaceAll(/[|` ]/, '') }

        expect:
        ids.toSorted() == StandardScalar.values()*.getId().toSorted()
    }

    void "the configuration keys are documented"() {
        expect:
        ['micronaut.graphql.tools.scalars', '`enabled`', '`<id>.enabled`', '`<id>.name`', '`<id>.format`'].every { page.contains(it) }
    }

    void "the formats of the page are the ones the code accepts"() {
        expect:
        ['epoch-seconds', 'epoch-millis', '`seconds`', '`millis`'].every { page.contains(it) }
    }

}
