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
package example.fileupload

import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.upload.GraphQLUpload
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.json.JsonMapper
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

// The Groovy suite runs on the JVM: a real multipart request, from the client to the resolver.
class UploadTest extends Specification {

    static final Map<String, Object> SERVER = [
            'micronaut.server.host'   : '127.0.0.1',
            'micronaut.server.port'   : -1,
            'graphql.graphiql.enabled': false] as Map<String, Object>

    private static byte[] bytes(String text) {
        text.getBytes('UTF-8')
    }

    private static Map post(EmbeddedServer server, MultipartBody body) {
        ApplicationContext context = server.applicationContext
        HttpClient client = context.createBean(HttpClient, server.URL)
        try {
            String response = client.toBlocking()
                    .retrieve(HttpRequest.POST('/graphql', body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String)
            return context.getBean(JsonMapper).readValue(response, Map)
        } finally {
            client.close()
        }
    }

    // tag::top-level[]
    void 'a mutation receives the uploaded file'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, SERVER, UploadFixtures.UPLOAD)
        def body = MultipartBody.builder()
                .addPart('operations', '{"query":"mutation ($file: Upload!) { uploadedFile(file: $file) }","variables":{"file":null}}')
                .addPart('map', '{"0":["variables.file"]}')
                .addPart('0', 'notes.txt', MediaType.TEXT_PLAIN_TYPE, bytes('hello'))
                .build()

        when:
        def response = post(server, body)

        then:
        response.errors == null
        response.data == [uploadedFile: 'notes.txt:hello']

        cleanup:
        server?.close()
    }
    // end::top-level[]

    // tag::input-object[]
    void 'a mutation receives files inside an input object'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, SERVER, UploadFixtures.UPLOAD)
        def body = MultipartBody.builder()
                .addPart('operations', '{"query":"mutation ($input: BookUpload!) { uploadedBook(input: $input) }",' +
                        '"variables":{"input":{"title":"Dune","cover":null,"pages":[null,null]}}}')
                .addPart('map', '{"0":["variables.input.cover"],"1":["variables.input.pages.0"],"2":["variables.input.pages.1"]}')
                .addPart('0', 'cover.png', MediaType.IMAGE_PNG_TYPE, bytes('cover'))
                .addPart('1', '1.txt', MediaType.TEXT_PLAIN_TYPE, bytes('one'))
                .addPart('2', '2.txt', MediaType.TEXT_PLAIN_TYPE, bytes('two'))
                .build()

        when:
        def response = post(server, body)

        then:
        response.errors == null
        response.data == [uploadedBook: 'Dune cover=cover page=one page=two']

        cleanup:
        server?.close()
    }
    // end::input-object[]

    // tag::executor[]
    void 'the resolver runs off the event loop'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, SERVER, UploadFixtures.UPLOAD)
        def body = MultipartBody.builder()
                .addPart('operations', '{"query":"mutation ($file: Upload!) { uploadedOn(file: $file) }","variables":{"file":null}}')
                .addPart('map', '{"0":["variables.file"]}')
                .addPart('0', 'a.txt', MediaType.TEXT_PLAIN_TYPE, bytes('x'))
                .build()

        when:
        String thread = post(server, body).data.uploadedOn

        then:
        !thread.contains('event-loop')
        !thread.contains('nioEventLoopGroup')

        cleanup:
        server?.close()
    }
    // end::executor[]

    // tag::invalid[]
    void 'a map that names a missing part is an error with a code'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, SERVER, UploadFixtures.UPLOAD)
        def body = MultipartBody.builder()
                .addPart('operations', '{"query":"mutation ($file: Upload!) { uploadedFile(file: $file) }","variables":{"file":null}}')
                .addPart('map', '{"0":["variables.file"]}')
                .build()

        when:
        post(server, body)

        then:
        HttpClientResponseException failure = thrown()
        failure.status == HttpStatus.BAD_REQUEST
        def error = server.applicationContext.getBean(JsonMapper)
                .readValue(failure.response.getBody(String).get(), Map).errors[0]
        error.extensions.code == GraphQLUpload.INVALID_REQUEST_CODE
        error.message.contains('no such part')

        cleanup:
        server?.close()
    }
    // end::invalid[]

    // tag::precedence[]
    void 'a JSON request on the same path still reaches the transport route'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, SERVER, UploadFixtures.UPLOAD)
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

        when:
        String response = client.toBlocking().retrieve(HttpRequest.POST('/graphql', '{"query":"{ __typename }"}')
                .contentType(MediaType.APPLICATION_JSON_TYPE), String)

        then:
        server.applicationContext.getBean(JsonMapper).readValue(response, Map) == [data: [__typename: 'Query']]

        cleanup:
        client?.close()
        server?.close()
    }
    // end::precedence[]

}
