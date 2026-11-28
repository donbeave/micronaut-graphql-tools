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

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.codegen.golden.Determinism
import io.micronaut.graphql.tools.codegen.golden.GoldenFiles
import io.micronaut.graphql.tools.codegen.golden.JavaInMemoryCompiler
import spock.lang.Specification
import spock.lang.TempDir

import java.lang.reflect.ParameterizedType
import java.nio.file.Files
import java.nio.file.Path

class ModelRecordSpec extends Specification {

    @TempDir Path dir

    static final String SCHEMA = '''scalar Date

enum Color { RED, GREEN }

interface Node {
  id: ID!
  children: [Node!]!
}

interface Counted {
  count: Int
  ratio: Float
  flag: Boolean
}

union SearchResult = Product | Folder

"""
An item that is for sale.
Ends a comment with */ here.
"""
type Product implements Node & Counted {
  id: ID!
  "The display name."
  name: String!
  nickname: String
  count: Int!
  ratio: Float!
  flag: Boolean
  stars: Int!
  score: Float!
  active: Boolean!
  maybeStars: Int
  tags: [String!]!
  aliases: [String]
  grid: [[Int!]!]
  color: Color!
  favorite: Color
  released: Date
  oldName: String @deprecated(reason: "Use name.")
  "Has an argument, so a type contract resolves it."
  reviews(first: Int): [Review!]!
  children: [Node!]!
}

type Folder implements Node {
  id: ID!
  children: [Folder!]!
}

type Review {
  body: String!
}

type Query {
  products: [Product!]!
}
'''

    // What the sealed-interface emission (#127) writes for the interfaces of SCHEMA, by hand, so that the records can be compiled.
    static final String INTERFACES = '''package com.example.model;

import java.util.List;

public interface Node {
    String id();

    List<Node> children();
}
'''

    static final List<GeneratedFile> STUBS = [
            new GeneratedFile('com/example/model/Node.java', INTERFACES),
            new GeneratedFile('com/example/model/Counted.java', '''package com.example.model;

public interface Counted {
    Integer count();

    Double ratio();

    Boolean flag();
}
'''),
            new GeneratedFile('com/example/model/SearchResult.java', 'package com.example.model;\n\npublic interface SearchResult {\n}\n')
    ]

    def "the object types are records that match the golden snapshot and compile with their interfaces"() {
        given:
        def files = generate(SCHEMA)

        expect:
        new GoldenFiles().assertMatches('model-record-java', files)
        new JavaInMemoryCompiler().assertCompiles(files + STUBS)
    }

    def "the output is identical for every order of the schema files"() {
        given:
        def files = [write('a.graphqls', SCHEMA), write('b.graphqls', 'extend type Review { stars: Int! }\n')]

        expect:
        Determinism.assertDeterministic(files) { order -> generate(*order) }
    }

    def "a non-null Int, Float and Boolean are primitives and every other scalar position is a reference type"() {
        given:
        def product = load().loadClass('com.example.model.Product')
        def types = product.recordComponents.collectEntries { [(it.name): it.genericType] }

        expect:
        types.stars == int
        types.score == double
        types.active == boolean
        types.maybeStars == Integer
        types.name == String
        types.id == String
        types.released == java.time.LocalDate
        types.color.typeName == 'com.example.model.Color'
        elements(types.tags) == String
        elements(types.grid) instanceof ParameterizedType
        elements(elements(types.grid)) == Integer
    }

    def "a strengthened scalar keeps the boxed class of the interface and a narrowed list keeps its element type"() {
        given:
        def loader = load()
        def product = loader.loadClass('com.example.model.Product')
        def folder = loader.loadClass('com.example.model.Folder')
        def node = loader.loadClass('com.example.model.Node')

        expect:
        product.getRecordComponents().find { it.name == 'count' }.genericType == Integer
        product.getRecordComponents().find { it.name == 'ratio' }.genericType == Double
        elements(folder.recordComponents.find { it.name == 'children' }.genericType) == node
        node.isAssignableFrom(folder)
    }

    def "a field with arguments is not a component, and the components keep the schema order"() {
        given:
        def product = load().loadClass('com.example.model.Product')

        expect:
        product.recordComponents*.name == ['id', 'name', 'nickname', 'count', 'ratio', 'flag', 'stars', 'score', 'active', 'maybeStars',
                'tags', 'aliases', 'grid', 'color', 'favorite', 'released', 'oldName', 'children']
    }

    def "a nullable position carries the nullable annotation, on the list and on its elements"() {
        given:
        def product = load().loadClass('com.example.model.Product')
        def annotated = product.recordComponents.collectEntries { [(it.name): it.annotatedType] }

        expect:
        annotated.nickname.isAnnotationPresent(Nullable)
        annotated.maybeStars.isAnnotationPresent(Nullable)
        annotated.aliases.isAnnotationPresent(Nullable)
        ((java.lang.reflect.AnnotatedParameterizedType) annotated.aliases).annotatedActualTypeArguments[0].isAnnotationPresent(Nullable)
        !annotated.name.isAnnotationPresent(Nullable)
        !annotated.id.isAnnotationPresent(Nullable)
        !((java.lang.reflect.AnnotatedParameterizedType) annotated.tags).annotatedActualTypeArguments[0].isAnnotationPresent(Nullable)
        !annotated.stars.isAnnotationPresent(Nullable)
    }

    def "a record is introspected, implements its interfaces and unions, and has no serialization annotation"() {
        given:
        def loader = load()
        def product = loader.loadClass('com.example.model.Product')

        expect:
        product.isRecord()
        product.isAnnotationPresent(Introspected)
        !product.isAnnotationPresent(GraphQLType)
        product.interfaces*.simpleName as Set == ['Node', 'Counted', 'SearchResult'] as Set
        product.annotations*.annotationType()*.name.every { !it.toLowerCase().contains('serde') && !it.contains('jackson') }
        product.recordComponents.every { component -> component.annotations*.annotationType()*.name.every { !it.toLowerCase().contains('serde') } }
    }

    def "descriptions become Javadoc, a deprecation becomes @Deprecated and a comment terminator cannot break the file"() {
        when:
        def product = generate(SCHEMA).find { it.path() == 'com/example/model/Product.java' }.content()

        then:
        product.contains('''/**
 * An item that is for sale.
 * Ends a comment with *&#47; here.
 *
 * @param name The display name.
 * @param oldName Deprecated: Use name.
 */''')
        product.contains('        @Deprecated @Nullable String oldName,\n')
        !product.contains('reviews')
        product.contains('@Generated("io.micronaut.graphql.tools.codegen")')
        product.contains('@Introspected')
    }

    def "a deprecated field without a reason says so in its @param line and a description precedes the reason"() {
        when:
        def old = generate('type Old { id: ID! "Gone" gone: String @deprecated  bye: Int @deprecated }\n')
                .find { it.path() == 'com/example/model/Old.java' }.content()

        then:
        old.contains(' * @param gone Gone\n * Deprecated: No longer supported\n')
        old.contains(' * @param bye Deprecated: No longer supported\n')
    }

    def "a generated name that differs from the SDL name gets @GraphQLType and the references use the generated name"() {
        given:
        def files = generate('type Pet { owner: Owner! }\ntype Owner { name: String }\n', base()
                .typeNameSuffix('Model').build())
        def loader = new JavaInMemoryCompiler().load(files)
        def pet = loader.loadClass('com.example.model.PetModel')

        expect:
        new GoldenFiles().assertMatches('model-record-renamed-java', files)
        pet.getAnnotation(GraphQLType).name() == 'Pet'
        pet.recordComponents.find { it.name == 'owner' }.genericType.typeName == 'com.example.model.OwnerModel'
    }

    def "with the JSpecify annotation the model package is @NullMarked"() {
        given:
        def files = generate('type Pet { name: String! nickname: String tags: [String] }\n', Options.builder().packageName('com.example')
                .nullableAnnotation('org.jspecify.annotations.Nullable').build())

        expect:
        new GoldenFiles().assertMatches('model-record-jspecify-java', files)
        new JavaInMemoryCompiler().assertCompiles(files)
    }

    def "without a nullable annotation no component is annotated"() {
        when:
        def pet = generate('type Pet { nickname: String }\n', base().nullableAnnotation('').build())
                .find { it.path() == 'com/example/model/Pet.java' }.content()

        then:
        pet.contains('public record Pet(\n        String nickname\n) {')
        !pet.contains('Nullable')
    }

    def "the model style BEAN writes no record"() {
        expect:
        generate('type Pet { name: String }\n', base().modelStyle(ModelStyle.BEAN).build())
                .every { !it.path().endsWith('Pet.java') }
    }

    private ClassLoader load() {
        new JavaInMemoryCompiler().load(generate(SCHEMA) + STUBS)
    }

    private static java.lang.reflect.Type elements(java.lang.reflect.Type type) {
        ((ParameterizedType) type).actualTypeArguments[0]
    }

    private List<GeneratedFile> generate(String schema, Options options = options()) {
        generate(options, write('schema.graphqls', schema))
    }

    private List<GeneratedFile> generate(Path... schemaFiles) {
        generate(options(), schemaFiles)
    }

    private List<GeneratedFile> generate(Options options, Path... schemaFiles) {
        def result = GraphQLCodeGenerator.builder().withSchemaFiles(schemaFiles).withOutputDirectory(dir.resolve('out')).withOptions(options)
                .build().generate()
        assert result.diagnostics().findAll { it.severity() == Diagnostic.Severity.ERROR }.isEmpty()
        assert !result.files().isEmpty()
        result.files()
    }

    private static Options.Builder base() {
        Options.builder().packageName('com.example').typeMappings([Date: 'java.time.LocalDate'])
    }

    private static Options options() {
        base().build()
    }

    private Path write(String name, String text) {
        Files.writeString(dir.resolve(name), text)
    }
}
