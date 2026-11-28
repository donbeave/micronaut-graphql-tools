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

import io.micronaut.graphql.tools.codegen.Mode
import io.micronaut.graphql.tools.codegen.Options
import io.micronaut.graphql.tools.codegen.golden.GoldenFiles
import io.micronaut.graphql.tools.codegen.ir.IrSnapshot
import spock.lang.Specification

import java.nio.file.Path
import java.nio.file.Paths

/**
 * The IR text snapshot of the client fixture: operations with variables and presence, selection sets, fragments, abstract
 * selections with the injected type name, and the result models derived from them. Run the suite with -PupdateGolden to rewrite
 * the snapshot, and review the diff.
 */
class ClientIrGoldenSpec extends Specification {

    static final Path FIXTURE = Paths.get(ClientIrGoldenSpec.getResource('/schema/client').toURI())

    static Options.Builder client() {
        Options.builder().mode(Mode.CLIENT).packageName('com.example').typeMappings([DateTime: 'java.time.OffsetDateTime'])
    }

    def "the client IR of the fixture matches its snapshot"() {
        when:
        def result = IrBuilder.fromDirectory(FIXTURE, client().build())

        then:
        result.diagnostics().empty
        new GoldenFiles().assertText('ir-client-default', 'ir.txt', IrSnapshot.print(result.ir()))
    }
}
