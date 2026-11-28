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

import io.micronaut.core.annotation.AccessorsStyle
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.graphql.tools.codegen.golden.GoldenFiles
import io.micronaut.graphql.tools.codegen.golden.JavaInMemoryCompiler
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir

import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.nio.file.Files
import java.nio.file.Path

class SealedInterfaceSpec extends Specification {

    @TempDir Path dir

    static final String SCHEMA = '''interface Node {
  id: ID!
}

"A node with a name."
interface Named implements Node {
  id: ID!
  "The display name."
  name: String!
  nickname: String
  children: [Node!]!
  oldName: String @deprecated(reason: "Use name.")
  count: Int
  "Has an argument, so a type contract resolves it."
  related(first: Int): [Node!]!
}

interface Lonely implements Node {
  id: ID!
}

union SearchResult = Product | Folder

"The types that have a picture."
union Media = Product | Video

type Product implements Node & Named {
  id: ID!
  name: String!
  nickname: String
  children: [Folder!]!
  oldName: String
  count: Int!
}

type Folder implements Node {
  id: ID!
}

type Video implements Node {
  id: ID!
}

type Query {
  search: [SearchResult!]!
}
'''

    static final String SWITCH = '''package com.example.app;

import com.example.model.Folder;
import com.example.model.Product;
import com.example.model.SearchResult;

public final class Describe {

    public static String of(SearchResult result) {
        return switch (result) {
            case Product product -> "product " + product.id();
            case Folder folder -> "folder " + folder.id();
        };
    }
}
'''

    def "unions and interfaces are sealed interfaces that permit their members and match the golden snapshot"() {
        given:
        def files = generate(SCHEMA)

        expect:
        new GoldenFiles().assertMatches('sealed-interfaces-java', files)
        new JavaInMemoryCompiler().assertCompiles(files)
    }

    def "the compiled interfaces are sealed with the permitted members in schema order"() {
        given:
        def loader = load(generate(SCHEMA))
        def permits = { String name -> loader.loadClass('com.example.model.' + name).permittedSubclasses*.simpleName }

        expect:
        permits('Node') == ['Named', 'Lonely', 'Product', 'Folder', 'Video']
        permits('Named') == ['Product']
        permits('SearchResult') == ['Product', 'Folder']
        permits('Media') == ['Product', 'Video']
        loader.loadClass('com.example.model.Node').isSealed()
        loader.loadClass('com.example.model.Named').isSealed()
        loader.loadClass('com.example.model.Media').isSealed()
    }

    def "an interface without an implementation is not sealed, and the sealed interface that permits it says non-sealed"() {
        given:
        def loader = load(generate(SCHEMA))
        def lonely = loader.loadClass('com.example.model.Lonely')

        expect:
        !lonely.isSealed()
        !Modifier.isFinal(lonely.modifiers)
        loader.loadClass('com.example.model.Node').permittedSubclasses.contains(lonely)
        generate(SCHEMA).find { it.path() == 'com/example/model/Lonely.java' }.content().contains('public non-sealed interface Lonely extends Node {')
    }

    def "a type in two unions implements both, and it implements every interface that it belongs to"() {
        given:
        def product = load(generate(SCHEMA)).loadClass('com.example.model.Product')

        expect:
        product.interfaces*.simpleName as Set == ['Node', 'Named', 'SearchResult', 'Media'] as Set
    }

    def "an interface declares its fields as accessors, without the fields that have arguments, and keeps boxed types and the nullable annotation"() {
        given:
        def named = load(generate(SCHEMA)).loadClass('com.example.model.Named')
        def methods = named.declaredMethods.collectEntries { [(it.name): it] }

        expect:
        methods.keySet() == ['id', 'name', 'nickname', 'children', 'oldName', 'count'] as Set
        methods.name.returnType == String
        methods.count.returnType == Integer
        methods.nickname.annotatedReturnType.isAnnotationPresent(Nullable)
        !methods.name.annotatedReturnType.isAnnotationPresent(Nullable)
        ((ParameterizedType) methods.children.genericReturnType).actualTypeArguments[0].typeName == 'com.example.model.Node'
        methods.oldName.isAnnotationPresent(Deprecated)
        methods.values().every { Modifier.isAbstract(it.modifiers) }
    }

    def "an interface with accessors is introspected with fluent accessors, and a union has no annotation of the binding"() {
        given:
        def loader = load(generate(SCHEMA))
        def named = loader.loadClass('com.example.model.Named')
        def union = loader.loadClass('com.example.model.SearchResult')

        expect:
        named.isAnnotationPresent(Introspected)
        named.getAnnotation(AccessorsStyle).readPrefixes() == [''] as String[]
        union.declaredMethods.length == 0
        !union.isAnnotationPresent(Introspected)
    }

    def "descriptions become Javadoc and a deprecation becomes @Deprecated on the accessor"() {
        when:
        def named = generate(SCHEMA).find { it.path() == 'com/example/model/Named.java' }.content()

        then:
        named.contains('/**\n * A node with a name.\n */')
        named.contains('    /**\n     * The display name.\n     */\n    String name();')
        named.contains('@Deprecated\n    @Nullable String oldName();')
        !named.contains('related')
    }

    def "a member that typeMappings maps to an external class makes a plain interface and a warning that says why"() {
        given:
        def schema = 'union Result = Product | External\ntype Product { id: ID! }\ntype External { id: ID! }\n'
        def options = base().typeMappings([External: 'com.acme.External']).build()
        def result = run(schema, options)
        def external = new GeneratedFile('com/acme/External.java', 'package com.acme;\n\npublic class External {\n}\n')

        expect:
        new GoldenFiles().assertMatches('sealed-interfaces-mapped-java', result.files())
        new JavaInMemoryCompiler().assertCompiles(result.files() + external)
        !result.files().find { it.path() == 'com/example/model/Result.java' }.content().contains('sealed')
        result.diagnostics().collect { [it.severity(), it.code()] } == [[Diagnostic.Severity.WARNING, Diagnostic.MAPPED_MEMBER]]
        result.diagnostics()[0].message().contains("'Result'")
        result.diagnostics()[0].message().contains('plain interface')
    }

    def "sealedAbstractTypes NEVER writes plain interfaces and every member still implements them"() {
        given:
        def files = generate(SCHEMA, base().sealedAbstractTypes(SealedAbstractTypes.NEVER).build())
        def loader = load(files)

        expect:
        files.every { !it.content().contains('sealed') }
        !loader.loadClass('com.example.model.Node').isSealed()
        !loader.loadClass('com.example.model.SearchResult').isSealed()
        loader.loadClass('com.example.model.SearchResult').isAssignableFrom(loader.loadClass('com.example.model.Folder'))
    }

    def "an object type that interfaceTypes selects is a non-sealed member of its interface, and a union with such a member is plain"() {
        given:
        def schema = 'interface Node { id: ID! }\nunion Any = Entity | Item\ntype Entity implements Node { id: ID! }\n' +
                'type Item implements Node { id: ID! }\n'
        def result = run(schema, base().interfaceTypes(['Entity']).build())
        def loader = load(result.files())

        expect:
        new JavaInMemoryCompiler().assertCompiles(result.files())
        loader.loadClass('com.example.model.Node').isSealed()
        loader.loadClass('com.example.model.Node').permittedSubclasses*.simpleName == ['Entity', 'Item']
        !loader.loadClass('com.example.model.Entity').isSealed()
        loader.loadClass('com.example.model.Entity').isInterface()
        !loader.loadClass('com.example.model.Any').isSealed()
        result.diagnostics().collect { it.code() } == [Diagnostic.SELECTED_MEMBER]
    }

    def "an interface that extends a sealed interface is itself sealed when it has members"() {
        given:
        def loader = load(generate(SCHEMA))
        def named = loader.loadClass('com.example.model.Named')

        expect:
        named.isSealed()
        named.interfaces*.simpleName == ['Node']
        named.permittedSubclasses*.simpleName == ['Product']
    }

    def "the golden schema of the interfaces compiles with the emitted interfaces in every combination of the options"() {
        expect:
        new JavaInMemoryCompiler().assertCompiles(generate(SCHEMA, base().sealedAbstractTypes(sealing).typeNamePrefix(prefix).build()))

        where:
        [sealing, prefix] << [SealedAbstractTypes.values().toList(), ['', 'Gql']].combinations()
    }

    @Requires({ Runtime.version().feature() >= 21 })
    def "a switch over a generated union compiles without a default branch, and a new member makes it fail to compile"() {
        given:
        def compiler = new JavaInMemoryCompiler(21)
        def app = new GeneratedFile('com/example/app/Describe.java', SWITCH)

        expect:
        compiler.assertCompiles(generate(SCHEMA) + app)

        when:
        def widened = SCHEMA.replace('union SearchResult = Product | Folder', 'union SearchResult = Product | Folder | Video')
        def errors = compiler.compile(generate(widened) + app)

        then:
        errors.size() == 1
        errors[0].contains('Describe.java')
        errors[0].contains('does not cover all possible input values')

        when:
        def dropped = SCHEMA.replace('union SearchResult = Product | Folder', 'union SearchResult = Product')
        def narrowed = compiler.compile(generate(dropped) + app)

        then:
        narrowed.any { it.contains('Describe.java') }
    }

    @Requires({ Runtime.version().feature() >= 21 })
    def "a switch over a generated interface is exhaustive over its permitted subtypes"() {
        given:
        def source = '''package com.example.app;

import com.example.model.*;

public final class Kinds {

    public static String of(Node node) {
        return switch (node) {
            case Named named -> "named " + named.name();
            case Lonely lonely -> "lonely";
            case Folder folder -> "folder";
            case Video video -> "video";
        };
    }
}
'''
        def files = generate(SCHEMA) + new GeneratedFile('com/example/app/Kinds.java', source)

        expect:
        new JavaInMemoryCompiler(21).assertCompiles(files)
    }

    private ClassLoader load(List<GeneratedFile> files) {
        new JavaInMemoryCompiler().load(files)
    }

    private List<GeneratedFile> generate(String schema, Options options = base().build()) {
        def result = run(schema, options)
        assert result.diagnostics().findAll { it.severity() == Diagnostic.Severity.ERROR }.isEmpty()
        result.files()
    }

    private GenerationResult run(String schema, Options options) {
        Path file = Files.writeString(dir.resolve('schema.graphqls'), schema)
        GraphQLCodeGenerator.builder().withSchemaFiles(file).withOutputDirectory(dir.resolve('out')).withOptions(options).build().generate()
    }

    private static Options.Builder base() {
        Options.builder().packageName('com.example')
    }
}
