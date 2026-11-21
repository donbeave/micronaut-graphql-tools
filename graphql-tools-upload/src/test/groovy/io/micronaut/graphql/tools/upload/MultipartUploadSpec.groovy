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
package io.micronaut.graphql.tools.upload

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.http.multipart.CompletedPart
import io.micronaut.json.JsonMapper
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import spock.lang.Shared
import spock.lang.Specification

/** A real server and a real multipart client: the specification's request shapes, the invalid ones, and where the resolver runs. */
class MultipartUploadSpec extends Specification {

    static final String SPEC_NAME = 'MultipartUploadSpec'

    @Language('GraphQL')
    static final String SCHEMA = '''
scalar Upload

schema { query: Query mutation: Mutation }

type Query { ping: String }

type Mutation {
  singleUpload(file: Upload!): String!
  optionalUpload(file: Upload): String!
  multipleUpload(files: [Upload!]!): [String!]!
  documentUpload(input: DocumentInput!): String!
  uploadThread(file: Upload!): String!
  rawPart(file: Upload!): String!
}

input DocumentInput {
  title: String!
  file: Upload!
  attachments: [Upload!]
}
'''

    @Shared ApplicationContext context
    @Shared EmbeddedServer server
    @Shared HttpClient client
    @Shared JsonMapper json

    void setupSpec() {
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry()
        registry.merge(new SchemaParser().parse(SCHEMA))
        context = ApplicationContext.builder(
                ['spec.name'                             : SPEC_NAME,
                 'micronaut.server.host'                 : '127.0.0.1',
                 'micronaut.server.port'                 : -1,
                 'graphql.graphql-ws.enabled'            : false,
                 'graphql.graphiql.enabled'              : false], 'test')
                .singletons(registry)
                .start()
        server = context.getBean(EmbeddedServer).start()
        client = context.createBean(HttpClient, server.URL)
        json = context.getBean(JsonMapper)
    }

    void cleanupSpec() {
        client?.close()
        context?.close()
    }

    private String post(MultipartBody body) {
        client.toBlocking().retrieve(HttpRequest.POST('/graphql', body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String)
    }

    private Map postOk(MultipartBody body) {
        json.readValue(post(body), Map)
    }

    private HttpClientResponseException postFailing(MultipartBody body) {
        try {
            post(body)
        } catch (HttpClientResponseException e) {
            return e
        }
        throw new AssertionError('the request was accepted')
    }

    static MultipartBody.Builder operations(String query, Map variables, Map map) {
        MultipartBody.builder()
                .addPart('operations', JsonOf.write([query: query, variables: variables]))
                .addPart('map', JsonOf.write(map))
    }

    static MultipartBody.Builder withFile(MultipartBody.Builder builder, String name, String filename, String content) {
        builder.addPart(name, filename, MediaType.TEXT_PLAIN_TYPE, content.getBytes('UTF-8'))
    }

    void "a mutation receives an uploaded file as a top level argument"() {
        when:
        def body = withFile(operations('mutation ($file: Upload!) { singleUpload(file: $file) }', [file: null], ['0': ['variables.file']]),
                '0', 'notes.txt', 'hello upload').build()
        def response = postOk(body)

        then:
        response.errors == null
        response.data.singleUpload == 'notes.txt|hello upload|12'
    }

    void "a mutation receives uploaded files inside an input object"() {
        when:
        def builder = operations('mutation ($input: DocumentInput!) { documentUpload(input: $input) }',
                [input: [title: 'Report', file: null, attachments: [null, null]]],
                ['0': ['variables.input.file'], '1': ['variables.input.attachments.0'], '2': ['variables.input.attachments.1']])
        withFile(builder, '0', 'main.txt', 'body')
        withFile(builder, '1', 'a.txt', 'first')
        withFile(builder, '2', 'b.txt', 'second')
        def response = postOk(builder.build())

        then:
        response.errors == null
        response.data.documentUpload == 'Report|main.txt=body|a.txt=first,b.txt=second'
    }

    void "a list of uploads at the top level, and the same file mapped to two paths"() {
        when:
        def builder = operations('mutation ($files: [Upload!]!) { multipleUpload(files: $files) }',
                [files: [null, null, null]],
                ['0': ['variables.files.0', 'variables.files.2'], '1': ['variables.files.1']])
        withFile(builder, '0', 'x.txt', 'xx')
        withFile(builder, '1', 'y.txt', 'y')
        def response = postOk(builder.build())

        then:
        response.data.multipleUpload == ['x.txt|2', 'y.txt|1', 'x.txt|2']
    }

    void "a nullable upload may be absent from the map"() {
        when:
        def response = postOk(operations('mutation ($file: Upload) { optionalUpload(file: $file) }', [file: null], [:]).build())

        then:
        response.data.optionalUpload == 'none'
    }

    void "the files may come before the operations and the map"() {
        when:
        def builder = MultipartBody.builder()
        withFile(builder, '0', 'early.txt', 'early')
        builder.addPart('map', JsonOf.write(['0': ['variables.file']]))
        builder.addPart('operations', JsonOf.write([query: 'mutation ($file: Upload!) { singleUpload(file: $file) }', variables: [file: null]]))
        def response = postOk(builder.build())

        then:
        response.data.singleUpload == 'early.txt|early|5'
    }

    void "a part that is not a file is bound as a CompletedPart"() {
        when:
        def builder = operations('mutation ($file: Upload!) { rawPart(file: $file) }', [file: null], ['field': ['variables.file']])
        builder.addPart('field', 'plain value')
        def response = postOk(builder.build())

        then:
        response.data.rawPart == 'field|plain value'
    }

    void "the resolver runs on the executor of the library, not on the event loop"() {
        when:
        def body = withFile(operations('mutation ($file: Upload!) { uploadThread(file: $file) }', [file: null], ['0': ['variables.file']]),
                '0', 't.txt', 'x').build()
        def thread = postOk(body).data.uploadThread

        then:
        !thread.contains('nioEventLoopGroup')
        !thread.contains('event-loop')
    }

    void "the operation name and the errors of the execution are returned as for a JSON request"() {
        when:
        def response = postOk(withFile(operations('mutation ($file: Upload!) { missing(file: $file) }', [file: null],
                ['0': ['variables.file']]), '0', 'f.txt', 'x').build())

        then:
        response.errors*.message.any { it.contains('missing') }
    }

    void "a variable that no file replaces is an error of the operation"() {
        when:
        def response = postOk(operations('mutation ($file: Upload!) { singleUpload(file: $file) }', [file: null], [:]).build())

        then:
        response.errors
        response.data == null
    }

    void "a literal upload in the query is an error of the operation"() {
        when:
        def response = postOk(withFile(operations('mutation { singleUpload(file: "x") }', [file: null], ['0': ['variables.file']]), '0', 'f.txt', 'x').build())

        then:
        response.errors
    }

    void "#why returns a 400 with an error that has the code"() {
        when:
        def e = postFailing(body)
        def parsed = json.readValue(e.response.getBody(String).get(), Map)

        then:
        e.status == HttpStatus.BAD_REQUEST
        parsed.errors[0].extensions.code == GraphQLUpload.INVALID_REQUEST_CODE
        parsed.errors[0].message.contains(fragment)

        where:
        why                          | fragment                   | body
        'no operations part'         | "no 'operations' part"     | MultipartBody.builder().addPart('map', '{}').build()
        'no map part'                | "no 'map' part"            | MultipartBody.builder().addPart('operations', '{"query":"{ping}"}').build()
        'operations that is no JSON' | 'not valid JSON'           | MultipartBody.builder().addPart('operations', '{').addPart('map', '{}').build()
        'a batch of operations'      | 'batch'                    | MultipartBody.builder().addPart('operations', '[{"query":"{ping}"}]').addPart('map', '{}').build()
        'a map that is no object'    | "'map' part must be"       | MultipartBody.builder().addPart('operations', '{"query":"{ping}"}').addPart('map', '[]').build()
        'a map that is no JSON'      | 'not valid JSON'           | MultipartBody.builder().addPart('operations', '{"query":"{ping}"}').addPart('map', 'x').build()
        'a part that is missing'     | "no such part"             | MultipartBody.builder().addPart('operations', '{"query":"{ping}"}').addPart('map', '{"0":["variables.file"]}').build()
        'paths that are no list'     | 'must be a list of paths'  | withFile(MultipartBody.builder().addPart('operations', '{"query":"{ping}","variables":{"file":null}}').addPart('map', '{"0":"variables.file"}'), '0', 'f', 'x').build()
        'an empty list of paths'     | 'must be a list of paths'  | withFile(MultipartBody.builder().addPart('operations', '{"query":"{ping}","variables":{"file":null}}').addPart('map', '{"0":[]}'), '0', 'f', 'x').build()
        'a path outside variables'   | "start with 'variables.'"  | withFile(MultipartBody.builder().addPart('operations', '{"query":"{ping}"}').addPart('map', '{"0":["query"]}'), '0', 'f', 'x').build()
        'a path into nothing'        | 'does not exist'           | withFile(MultipartBody.builder().addPart('operations', '{"query":"{ping}","variables":{}}').addPart('map', '{"0":["variables.a.b"]}'), '0', 'f', 'x').build()
        'a list index out of range'  | 'no index 3'               | withFile(MultipartBody.builder().addPart('operations', '{"query":"{ping}","variables":{"l":[null]}}').addPart('map', '{"0":["variables.l.3"]}'), '0', 'f', 'x').build()
        'a list index that is no number' | 'not a list index'     | withFile(MultipartBody.builder().addPart('operations', '{"query":"{ping}","variables":{"l":[null]}}').addPart('map', '{"0":["variables.l.x"]}'), '0', 'f', 'x').build()
        'variables that are no object' | "'variables'"            | MultipartBody.builder().addPart('operations', '{"query":"{ping}","variables":[]}').addPart('map', '{}').build()
        'a map path without a null placeholder' | 'no null placeholder' | withFile(MultipartBody.builder().addPart('operations', '{"query":"{ping}","variables":{}}').addPart('map', '{"0":["variables.file"]}'), '0', 'f', 'x').build()
        'a map path that holds a value' | 'not a null placeholder' | withFile(MultipartBody.builder().addPart('operations', '{"query":"{ping}","variables":{"file":"x"}}').addPart('map', '{"0":["variables.file"]}'), '0', 'f', 'x').build()
        'a list element that is no placeholder' | 'not a null placeholder' | withFile(MultipartBody.builder().addPart('operations', '{"query":"{ping}","variables":{"l":["x"]}}').addPart('map', '{"0":["variables.l.0"]}'), '0', 'f', 'x').build()
        'a path that two entries name' | 'not a null placeholder' | withFile(withFile(MultipartBody.builder().addPart('operations', '{"query":"{ping}","variables":{"file":null}}').addPart('map', '{"0":["variables.file"],"1":["variables.file"]}'), '0', 'f', 'x'), '1', 'g', 'y').build()
        'an operations part without a query' | "has no 'query'" | MultipartBody.builder().addPart('operations', '{"variables":{}}').addPart('map', '{}').build()
        'a blank query' | "has no 'query'" | MultipartBody.builder().addPart('operations', '{"query":"  "}').addPart('map', '{}').build()
        'a part sent twice'          | 'more than once'           | MultipartBody.builder().addPart('operations', '{"query":"{ping}"}').addPart('map', '{}').addPart('map', '{}').build()
    }

    void "a JSON request still reaches the transport route"() {
        when:
        def response = client.toBlocking().retrieve(HttpRequest.POST('/graphql', '{"query":"{ ping }"}').contentType(MediaType.APPLICATION_JSON_TYPE), String)

        then:
        json.readValue(response, Map).data.ping == 'pong'
    }

    @Singleton
    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Resolvers {

        String ping() { 'pong' }

        String singleUpload(CompletedFileUpload file) { describe(file) }

        String optionalUpload(CompletedFileUpload file) { file == null ? 'none' : describe(file) }

        List<String> multipleUpload(List<CompletedFileUpload> files) { files.collect { it.filename + '|' + it.size } }

        String documentUpload(Document input) {
            input.title + '|' + input.file.filename + '=' + new String(input.file.bytes, 'UTF-8') + '|' +
                    input.attachments.collect { it.filename + '=' + new String(it.bytes, 'UTF-8') }.join(',')
        }

        String uploadThread(CompletedFileUpload file) { Thread.currentThread().name }

        String rawPart(CompletedPart file) { file.name + '|' + new String(file.bytes, 'UTF-8') }

        private static String describe(CompletedFileUpload file) {
            file.filename + '|' + new String(file.bytes, 'UTF-8') + '|' + file.size
        }

    }

    @GraphQLInput
    static class Document {
        String title
        CompletedFileUpload file
        List<CompletedFileUpload> attachments
    }

    static class JsonOf {
        static String write(Object value) {
            groovy.json.JsonOutput.toJson(value)
        }
    }

}
