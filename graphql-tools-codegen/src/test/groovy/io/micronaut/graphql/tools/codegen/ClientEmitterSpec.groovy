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
package io.micronaut.graphql.tools.codegen

import io.micronaut.core.type.Argument
import io.micronaut.graphql.tools.client.OperationType
import io.micronaut.graphql.tools.codegen.golden.Determinism
import io.micronaut.graphql.tools.codegen.golden.GoldenFiles
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

/**
 * The Java emitter of the client models (#671): golden files for the schema and operations of {@code schema/client-models}, the
 * compilation of the output against the documented class path of the generated sources, the compile-time checks of the client over
 * generated result types, callers in Java and Kotlin, and the decoding of responses with Micronaut Serialization.
 */
class ClientEmitterSpec extends Specification {

    static final Path FIXTURE = Paths.get(ClientEmitterSpec.getResource('/schema/client-models').toURI())

    @TempDir Path dir

    @Shared List<GeneratedFile> files
    @Shared ClientCompilation.Result checked
    @Shared Path checkedDir

    def setupSpec() {
        files = generate()
    }

    def "the client models of the fixture match the golden snapshot"() {
        expect:
        new GoldenFiles().assertMatches('client-java', files)
    }

    def "the output is identical for every order of the files and has no timestamp or dated @Generated"() {
        given:
        def all = []
        FIXTURE.toFile().eachFileRecurse { if (it.isFile()) { all << it.toPath() } }
        all.sort()

        expect:
        Determinism.assertDeterministic(all) { order ->
            generate(options(), order.findAll { it.toString().endsWith('.graphqls') }, order.findAll { it.toString().endsWith('.graphql') })
        }
    }

    def "the output compiles against the client module and the Serde JSON module only, and nothing else is on the class path"() {
        when:
        def result = ClientCompilation.plain(dir, files)

        then:
        result.success
        result.errors.empty
    }

    def "every generated file passes the compile-time checks of the client when a declarative client uses its types"() {
        when:
        def result = checkedCompilation()

        then:
        result.success
        result.errors.empty
    }

    def "a request class holds the document, its hash, the name, the kind and the types of the result, and sends nothing"() {
        given:
        def loader = checkedCompilation().loader()
        def getUser = loader.loadClass('com.example.model.GetUser')
        def variables = loader.loadClass('com.example.model.GetUserVariables')
        def document = getUser.DOCUMENT as String
        def request = getUser.getConstructor(variables).newInstance(variables.getConstructors()[0].newInstance('7', true, null))

        expect:
        getUser.NAME == 'GetUser'
        document.startsWith('query GetUser(')
        getUser.HASH == MessageDigest.getInstance('SHA-256').digest(document.getBytes(StandardCharsets.UTF_8)).encodeHex().toString()
        getUser.TYPE == OperationType.QUERY
        getUser.FIELD == 'user'
        (getUser.RESULT as Argument).type.simpleName == 'GetUserUser'
        (getUser.DATA as Argument).type == Map
        (getUser.DATA as Argument).typeParameters*.type*.simpleName == ['String', 'GetUserUser']
        request.name() == 'GetUser'
        request.document() == document
        request.hash() == getUser.HASH
        request.kind() == OperationType.QUERY
        request.variables().id() == '7'
        request.toString() == 'GetUser' + request.variables().toString()
        request == getUser.getConstructor(variables).newInstance(request.variables())
        request.hashCode() == request.variables().hashCode()
    }

    def "an operation without variables has an empty variables record and a constructor without arguments, and one that selects several root fields is read as the data object"() {
        given:
        def loader = checkedCompilation().loader()
        def viewer = loader.loadClass('com.example.model.Viewer')

        expect:
        viewer.getConstructor().newInstance().variables().class.recordComponents.length == 0
        !viewer.declaredFields*.name.contains('FIELD')
        (viewer.RESULT as Argument).type.simpleName == 'ViewerData'
        viewer.DATA.is(viewer.RESULT)
    }

    def "a subscription is a typed request class and result record, with no transport code"() {
        given:
        def source = files.find { it.path() == 'com/example/model/PostAdded.java' }.content()
        def loader = checkedCompilation().loader()
        def request = loader.loadClass('com.example.model.PostAdded')

        expect:
        request.TYPE == OperationType.SUBSCRIPTION
        request.FIELD == 'postAdded'
        (request.RESULT as Argument).type.simpleName == 'PostAddedPostAdded'
        loader.loadClass('com.example.model.PostAddedPostAdded').recordComponents*.name == ['id', 'likes']
        source.contains('SUBSCRIPTION')
        source.readLines().findAll { it.startsWith('import ') } as Set == [
                'import io.micronaut.core.type.Argument;', 'import io.micronaut.graphql.tools.client.OperationType;',
                'import java.util.Map;', 'import java.util.Objects;', 'import javax.annotation.processing.Generated;'] as Set
        !source.contains('Publisher') && !source.contains('WebSocket') && !source.contains('GraphQLHttpClient')
    }

    def "the Java caller compiles and works with the generated types"() {
        given:
        def loader = checkedCompilation().loader()
        def caller = loader.loadClass('com.example.caller.JavaCaller')
        def request = caller.request('1')

        expect:
        request.variables().withPosts() == true
        request.variables().first() == null
        caller.lowLevel(request).document == request.document()
        caller.lowLevel(request).operationName == 'GetUser'
        caller.lowLevel(request).operationType == OperationType.QUERY
        caller.lowLevel(request).dataType.is(loader.loadClass('com.example.model.GetUser').DATA)
        caller.subscription() == OperationType.SUBSCRIPTION
    }

    def "a Kotlin caller compiles against the generated Java, sees its nullability and matches the sealed interfaces exhaustively"() {
        given:
        def java = checkedCompilation()
        def kotlin = KotlinCompilation.compile(dir, ['com/example/caller/KotlinCaller.kt': resource('KotlinCaller.kt')], java.classes)

        expect:
        kotlin.success
        kotlin.output.trim().empty || !kotlin.output.contains('error:')
    }

    def "a Kotlin caller that uses a nullable component as non-null does not compile"() {
        given:
        def java = checkedCompilation()
        def kotlin = KotlinCompilation.compile(dir, ['Bad.kt': """
            import com.example.model.GetBookBook
            fun bad(book: GetBookBook): String = book.subtitle()
        """.stripIndent()], java.classes)

        expect:
        !kotlin.success
        kotlin.output.contains('Bad.kt')
        kotlin.output.contains('String?')
    }

    def "Micronaut Serialization reads the responses of the operations, including an unknown type and an unknown enum value"() {
        given:
        def loader = checkedCompilation().isolated()
        def contextLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = loader
        def context = loader.loadClass('io.micronaut.context.ApplicationContext').builder().classLoader(loader).start()
        def mapper = context.getBean(loader.loadClass('io.micronaut.serde.ObjectMapper'))

        when:
        def search = mapper.readValue('''{"search": [
            {"__typename": "Folder", "id": "1", "name": "docs", "createdAt": "2020-01-02T03:04:05Z"},
            {"__typename": "File", "id": "2", "bytes": 12},
            {"__typename": "User", "id": "3"},
            {"__typename": "Mystery", "id": "4"}]}''', loader.loadClass('com.example.model.Search').DATA)
        def results = search.search

        then:
        results*.class*.simpleName == ['SearchSearchFolder', 'SearchSearchFile', 'SearchSearchUser', 'SearchSearchOther']
        results*.__typename() == ['Folder', 'File', 'User', 'Mystery']
        results[0].name() == 'docs'
        results[0].createdAt() == OffsetDateTime.parse('2020-01-02T03:04:05Z')
        results[1].bytes() == 12

        when:
        def node = mapper.readValue('{"node": {"__typename": "Folder", "id": "1", "name": "n", "size": 3, "children": [{"__typename": "File", "id": "2"}]}}',
                loader.loadClass('com.example.model.FindNode').DATA).node

        then: 'a nested abstract type that names no member is read as its other record'
        node.class.simpleName == 'FindNodeNodeFolder'
        node.children()*.class*.simpleName == ['FolderBitsChildrenOther']
        node.children()[0].__typename() == 'File'

        when:
        def user = mapper.readValue('{"user": {"id": "1", "name": "n", "role": "SUPERUSER", "posts": null}}',
                loader.loadClass('com.example.model.GetUser').DATA).user
        def known = mapper.readValue('{"user": {"id": "1", "name": "n", "role": "ADMIN"}}',
                loader.loadClass('com.example.model.GetUser').DATA).user

        then: 'a value that the schema added reads as the default constant, and a field that a directive left out is null'
        user.role().name() == 'UNKNOWN__'
        user.posts() == null
        known.role().name() == 'ADMIN'
        known.posts() == null

        when:
        def added = mapper.readValue('{"postAdded": {"id": "1"}}', loader.loadClass('com.example.model.PostAdded').DATA).postAdded

        then:
        added.likes() == null

        when:
        def viewer = mapper.readValue('{"viewer": {"id": "1", "tags": ["a", "b"]}, "mine": {"id": "1"}}',
                loader.loadClass('com.example.model.Viewer').DATA)

        then: 'several root fields are the data object'
        viewer.viewer().tags() == ['a', 'b']
        viewer.mine().name() == null

        when:
        def book = mapper.readValue('''{"book": {"id": "1", "title": "t", "tags": ["x", null], "inStock": true, "rating": 4.5, "subtitle": null,
            "status": "OPEN", "headline": "h", "editions": [{"year": 1999, "label": null, "isbns": [["a"], ["b", "c"]]}]}}''',
                loader.loadClass('com.example.model.GetBook').DATA).book

        then: 'aliases are the names of the components and the nested fragments are read'
        book.headline() == 'h'
        book.tags() == ['x', null]
        book.editions()[0].isbns() == [['a'], ['b', 'c']]
        book.editions()[0].label() == null

        cleanup:
        context?.close()
        Thread.currentThread().contextClassLoader = contextLoader
    }

    def "Micronaut Serialization writes the variables and the inputs, and leaves out a value that is not set"() {
        given:
        def loader = checkedCompilation().isolated()
        def contextLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = loader
        def context = loader.loadClass('io.micronaut.context.ApplicationContext').builder().classLoader(loader).start()
        def mapper = context.getBean(loader.loadClass('io.micronaut.serde.ObjectMapper'))
        def variables = loader.loadClass('com.example.model.GetUserVariables')
        def input = loader.loadClass('com.example.model.BookInput')
        def status = loader.loadClass('com.example.model.Status')

        expect:
        mapper.writeValueAsString(variables.getConstructors()[0].newInstance('7', true, null)) == '{"id":"7","withPosts":true}'
        mapper.writeValueAsString(input.getConstructors()[0].newInstance('t', null, 5, ['a'], status.OPEN, null)) ==
                '{"title":"t","pages":5,"tags":["a"],"status":"OPEN"}'

        cleanup:
        context?.close()
        Thread.currentThread().contextClassLoader = contextLoader
    }

    def "a client schema is one package with one package-info, which is null-marked with the JSpecify annotation"() {
        when:
        def custom = generate(Options.builder().mode(Mode.CLIENT).packageName('com.example').modelPackage('app.books').typeMappings([DateTime: 'java.time.OffsetDateTime']).build())

        then:
        files.every { it.path().startsWith('com/example/model/') || it.path().startsWith('META-INF/') }
        files.findAll { it.path().endsWith('package-info.java') }*.path() == ['com/example/model/package-info.java']
        custom.findAll { it.path().endsWith('.java') }.every { it.path().startsWith('app/books/') }
        custom.find { it.path() == 'app/books/package-info.java' }.content() == '@NullMarked\npackage app.books;\n\nimport org.jspecify.annotations.NullMarked;\n'
    }

    def "without a nullable annotation and a generated annotation, and with a type name suffix, the output compiles and says so"() {
        given:
        def plain = generate(Options.builder().mode(Mode.CLIENT).packageName('com.example').nullableAnnotation('').generatedAnnotation('')
                .typeNameSuffix('Model').typeMappings([DateTime: 'java.time.OffsetDateTime']).build())

        expect:
        plain.any { it.path() == 'com/example/model/GetUserModel.java' }
        plain.any { it.path() == 'com/example/model/GetUserUserModel.java' }
        plain.findAll { it.path().endsWith('.java') }.every { !it.content().contains('Nullable') && !it.content().contains('@Generated') }
        plain.find { it.path() == 'com/example/model/package-info.java' }.content() == 'package com.example.model;\n'
        ClientCompilation.plain(dir, plain).success
    }

    def "the Micronaut nullable annotation is a type annotation that works on the components too"() {
        given:
        def micronaut = generate(Options.builder().mode(Mode.CLIENT).packageName('com.example').nullableAnnotation('io.micronaut.core.annotation.Nullable')
                .typeMappings([DateTime: 'java.time.OffsetDateTime']).build())

        expect:
        micronaut.find { it.path() == 'com/example/model/GetBookBook.java' }.content().contains('List<@Nullable String> tags')
        ClientCompilation.plain(dir, micronaut).success
    }

    def "a field, an input field and an enum value that the schema deprecates are @Deprecated and say why"() {
        given:
        def book = files.find { it.path() == 'com/example/model/GetBookBook.java' }.content()
        def status = files.find { it.path() == 'com/example/model/Status.java' }.content()

        expect:
        book.contains('@Deprecated @Nullable String oldTitle')
        book.contains(' * @param oldTitle Deprecated: Use title.')
        status.contains('@Deprecated\n    CLOSED')
    }

    private ClientCompilation.Result checkedCompilation() {
        if (checked == null) {
            checkedDir = Files.createTempDirectory('client-emitter')
            checked = ClientCompilation.checked(checkedDir, files, callers(), FIXTURE)
        }
        checked
    }

    private static Map<String, String> callers() {
        ['ModelsClient.java', 'JavaCaller.java'].collectEntries { name -> ["com/example/caller/${name}".toString(), resource(name)] }
    }

    private static String resource(String name) {
        ClientEmitterSpec.getResourceAsStream("/client-callers/${name}").text
    }

    private List<GeneratedFile> generate(Options options = options()) {
        generate(options, null, null)
    }

    private List<GeneratedFile> generate(Options options, List<Path> schemaFiles, List<Path> operationFiles) {
        def builder = GraphQLCodeGenerator.builder().withOutputDirectory(Paths.get('build/unused')).withOptions(options)
        if (operationFiles == null) {
            builder.withSchemaDirectory(FIXTURE)
        } else {
            builder.withSchemaFiles(schemaFiles).withOperationFiles(operationFiles)
        }
        def result = builder.build().generate()
        assert result.diagnostics().findAll { it.severity() == Diagnostic.Severity.ERROR }.isEmpty()
        result.files()
    }

    static Options options() {
        Options.builder().mode(Mode.CLIENT).packageName('com.example').typeMappings([DateTime: 'java.time.OffsetDateTime']).build()
    }
}
