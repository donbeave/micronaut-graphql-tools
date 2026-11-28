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
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// Starts the native executable (args[0]), posts the queries of GraphQLSmokeSpec and of the Java and Kotlin smoke tests to
// /graphql, asserts the response bodies, runs the native executable of the client smoke test (args[1]) against it and stops the
// process. Spock cannot run natively, so this runs on the JVM.
File executable = new File(args[0])
File clientExecutable = new File(args[1])
int port
new ServerSocket(0).withCloseable { port = it.localPort }

Process process = new ProcessBuilder(executable.absolutePath)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .tap { environment().put('MICRONAUT_SERVER_PORT', port as String) }
        .start()

HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

Map<String, Object> post(HttpClient http, int port, String query, Map<String, Object> variables = [:], String user = null) {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:$port/graphql"))
            .timeout(Duration.ofSeconds(30))
            .header('Content-Type', 'application/json')
            .POST(HttpRequest.BodyPublishers.ofString(JsonOutput.toJson([query: query, variables: variables])))
    if (user != null) {
        request.header('X-User', user)
    }
    HttpResponse<String> response = http.send(
            request.build(),
            HttpResponse.BodyHandlers.ofString())
    assert response.statusCode() == 200: "HTTP ${response.statusCode()}: ${response.body()}"
    Map body = new JsonSlurper().parseText(response.body()) as Map
    assert body.errors == null: "GraphQL errors: ${body.errors}"
    (Map<String, Object>) body.data
}

try {
    long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos()
    while (true) {
        assert process.alive: "The native executable exited with ${process.exitValue()} before it listened on port $port"
        try {
            new Socket('localhost', port).close()
            break
        } catch (IOException ignored) {
            assert System.nanoTime() < deadline: "The native executable did not listen on port $port within 60 seconds"
            Thread.sleep(100)
        }
    }

    Closure<Map<String, Object>> execute = { String query, Map<String, Object> variables = [:], String user = null -> post(http, port, query, variables, user) }

    Map payload = execute('mutation($id: ID!) { addBook(id: $id) { error { __typename ... on SecurityError { securityCode: code message } } } }',
            [id: 'ab']).addBook
    assert payload.error.__typename == 'SecurityError'
    assert payload.error.securityCode == 'NOT_AUTHENTICATED'
    assert payload.error.message == 'Not logged in.'

    List<Map> books = execute('{ allBooks { id title year label sellers { name price } } }').allBooks
    assert books.size() == 5
    assert books.first().id == 'ab'
    assert books.first().title == 'The Fractured Sage'
    assert books.first().year == 1982
    assert books.first().label == 'The Fractured Sage (1982)'
    assert books.first().sellers*.name == ['Amaz', 'Yah']
    assert books.first().sellers*.price*.doubleValue() == [20.0d, 21.0d]

    List<Map> profiles = execute('query($contains: String!, $limit: Int) { searchProfile(contains: $contains, limit: $limit) { username fullName } }',
            [contains: 'T', limit: 1]).searchProfile
    assert profiles.size() == 1
    assert profiles.first().username == 'test'
    assert profiles.first().fullName == 'Test Test'

    String signInQuery = 'mutation($u: String!, $p: String!) { signIn(username: $u, password: $p) { data { username } error { __typename ... on ValidationError { validationCode: code message } ... on SecurityError { securityCode: code message } } } }'
    Map unknown = execute(signInQuery, [u: 'unknown-user', p: 'test']).signIn
    Map wrong = execute(signInQuery, [u: 'test', p: 'xyz']).signIn
    assert unknown.data == null
    assert unknown.error.validationCode == 'NOT_FOUND'
    assert unknown.error.message == 'User unknown-user not found.'
    assert wrong.data == null
    assert wrong.error.__typename == 'SecurityError'
    assert wrong.error.securityCode == 'INCORRECT_CREDENTIALS'
    assert wrong.error.message == 'Password is incorrect.'

    String signUpQuery = 'mutation($input: SignUpInput!) { signUp(input: $input) { data { username firstName lastName books { id } } error { __typename ... on ValidationError { validationCode: code message } } } }'
    Map input = [username: 'john', password: 'test', firstName: 'John', lastName: 'Johnathan']
    Map created = execute(signUpQuery, [input: input]).signUp
    Map duplicate = execute(signUpQuery, [input: input]).signUp
    assert created.error == null
    assert created.data.username == 'john'
    assert created.data.firstName == 'John'
    assert created.data.lastName == 'Johnathan'
    assert created.data.books == []
    assert duplicate.data == null
    assert duplicate.error.validationCode == 'DUPLICATE'
    assert duplicate.error.message == 'User with username john already exists.'

    Map signIn = execute('mutation { signIn(username: "bob", password: "bodi") { data { username } } }').signIn
    Map addBook = execute('mutation { addBook(id: "ax") { error { __typename } } }', [:], 'bob').addBook
    Map current = execute('{ currentUser { username books { id label } } }', [:], 'bob').currentUser
    assert signIn.data.username == 'bob'
    assert addBook.error == null
    assert current.username == 'bob'
    assert current.books*.id == ['ax']
    assert current.books.first().label != null
    assert execute('{ introduced }').introduced == 'introduced introduced'

    Map scalars = execute('query($at: Instant!, $json: JSON) { echoInstant(at: $at) echoJson(data: $json) echoLocale(tag: "en-US") }',
            [at: '2026-12-24T10:15:30Z', json: [kind: 'x', list: [1, 2]]])
    assert scalars.echoInstant == '2026-12-24T10:16:30Z'
    assert scalars.echoJson == [kind: 'x', list: [1, 2]]
    assert scalars.echoLocale == 'en-US'

    println "Native smoke test passed: 7 scenarios against ${executable.name} on port $port"

    File clientLog = File.createTempFile('client-smoke', '.log')
    Process client = new ProcessBuilder(clientExecutable.absolutePath, "http://localhost:$port")
            .redirectErrorStream(true)
            .redirectOutput(clientLog)
            .start()
    boolean finished = client.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
    if (!finished) {
        client.destroyForcibly().waitFor()
    }
    String clientOutput = clientLog.getText('UTF-8')
    clientLog.delete()
    print clientOutput
    assert finished: "The native client smoke test did not finish within 120 seconds"
    assert client.exitValue() == 0: "The native client smoke test exited with ${client.exitValue()}"
    assert clientOutput.contains('Client smoke test passed: 10 scenarios'): "The native client smoke test did not report its scenarios"
} finally {
    process.destroy()
    if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
        process.destroyForcibly()
    }
}
