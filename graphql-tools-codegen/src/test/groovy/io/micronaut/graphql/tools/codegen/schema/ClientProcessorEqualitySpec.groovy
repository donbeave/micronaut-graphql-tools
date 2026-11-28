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

import io.micronaut.graphql.tools.binding.internal.client.ClientDirectories
import io.micronaut.graphql.tools.binding.internal.client.ClientOperations
import io.micronaut.graphql.tools.binding.internal.client.ClientRequest
import io.micronaut.graphql.tools.binding.internal.client.ClientSchemaBuilder
import io.micronaut.graphql.tools.codegen.Mode
import io.micronaut.graphql.tools.codegen.Options
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

/**
 * The generator sees what the compile-time checks of the processor validate: the document of every operation of the client fixture,
 * and its hash, equal the assembled document of the checks, which read the same files as a client directory.
 */
class ClientProcessorEqualitySpec extends Specification {

    static final Path FIXTURE = Paths.get(ClientProcessorEqualitySpec.getResource('/schema/client').toURI())

    @TempDir
    @Shared
    Path clients

    @Shared
    def ir

    @Shared
    ClientOperations checks

    def setupSpec() {
        // the processor reads a client directory with one subdirectory for each schema
        def shop = clients.resolve('shop')
        FIXTURE.toFile().eachFileRecurse { file ->
            if (file.isFile()) {
                def target = shop.resolve(FIXTURE.relativize(file.toPath()).toString())
                Files.createDirectories(target.parent)
                Files.copy(file.toPath(), target)
            }
        }
        def result = IrBuilder.fromDirectory(FIXTURE, Options.builder().mode(Mode.CLIENT).packageName('com.example')
                .typeMappings([DateTime: 'java.time.OffsetDateTime']).build())
        assert result.diagnostics().empty
        ir = result.ir()
        checks = new ClientOperations(new ClientDirectories([clients], []), true, ClientSchemaBuilder.STACK_BYTES)
    }

    @Unroll
    def "the document and the hash of #name equal the assembled document of the checks"() {
        given:
        def outcome = checks.resolve(new ClientRequest("Test#${name}", name, '', '', ''))
        def operation = ir.operations().find { it.names().sdlName() == name }

        expect:
        outcome.findings().empty
        operation.documentText() == outcome.operation().document()
        operation.documentHash() == sha256(outcome.operation().document())
        operation.variables()*.name() == outcome.operation().variables()
        operation.payload().name() == outcome.operation().payload().name()
        operation.kind().name().toLowerCase() == outcome.operation().operationType()
        operation.fragments() == outcome.operation().fragments().keySet().toList()

        where:
        name << ['GetUser', 'Search', 'FindNode', 'CreatePost', 'PostAdded', 'Viewer']
    }

    def "an inline document of the checks gives the same text as the file of the operation"() {
        given:
        def text = Files.readString(FIXTURE.resolve('ops/user.graphql'), StandardCharsets.UTF_8)
        def outcome = checks.resolve(new ClientRequest('Test#inline', 'inline', 'shop', text, 'GetUser'))

        expect:
        outcome.findings().empty
        outcome.operation().document() == ir.operations().find { it.names().sdlName() == 'GetUser' }.documentText()
    }

    def "the fixture has an operation for every case: an abstract selection, fragments, skip and include, and both payloads"() {
        expect:
        ir.operations()*.names()*.sdlName().toSet() == ['GetUser', 'Search', 'FindNode', 'CreatePost', 'PostAdded', 'Viewer'] as Set
        ir.operations()*.payload()*.name().toSet() == ['FIELD', 'DATA'] as Set
    }

    private static String sha256(String text) {
        MessageDigest.getInstance('SHA-256').digest(text.getBytes(StandardCharsets.UTF_8)).collect { String.format('%02x', it) }.join()
    }
}
