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
package example.language

import graphql.schema.idl.SchemaParser
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary
import io.micronaut.graphql.tools.customizer.GraphQLSchemaMappingDictionaryCustomizer
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Singleton

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

// The Groovy runtime (#182): properties that Groovy generates, GString results, and Groovy 4 sealed types.
@CompileStatic
@Factory
class GroovyFixtures {

    static final String LANGUAGE = 'groovy-language'

    @Bean
    @Singleton
    @Requires(env = GroovyFixtures.LANGUAGE)
    GraphQLTypeDefinitionRegistryCustomizer languageSchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse('''
                type Member { name: String! age: Int! active: Boolean! nickname: String greeting: String! }
                input MemberInput { name: String! age: Int! active: Boolean! }
                input CoordinatesInput { latitude: Float! longitude: Float! }
                type Circle { radius: Int! }
                type Square { side: Int! }
                union Shape = Circle | Square
                interface Animal { name: String! }
                type Dog implements Animal { name: String! tricks: Int! }
                type Cat implements Animal { name: String! lives: Int! }
                extend type Query {
                    member: Member!
                    echoMember(input: MemberInput!): Member!
                    describe(input: CoordinatesInput!): String!
                    greet(name: String!): String!
                    greetLater(name: String!): String!
                    greetLaterOnExecutor(name: String!): String!
                    shapes: [Shape!]!
                    animals: [Animal!]!
                }
            '''))
        } as GraphQLTypeDefinitionRegistryCustomizer
    }

    @Bean
    @Singleton
    @Requires(env = GroovyFixtures.LANGUAGE)
    GraphQLSchemaMappingDictionaryCustomizer languageMappings() {
        return { SchemaMappingDictionary dictionary ->
            dictionary.registerType('Circle', Circle).registerType('Square', Square)
                    .registerType('Dog', Dog).registerType('Cat', Cat)
        } as GraphQLSchemaMappingDictionaryCustomizer
    }
}

// tag::model[]
@CompileStatic
@GraphQLType
class Member {
    String name = 'Ada'
    int age = 36
    boolean active = true
    String nickname

    GString getGreeting() {
        return "Hello, ${name}"
    }
}
// end::model[]

// tag::input[]
@CompileStatic
@GraphQLInput
class MemberInput {
    String name
    int age
    boolean active
}
// end::input[]

// tag::constructor-input[]
@CompileStatic
@GraphQLInput
class CoordinatesInput {
    final double latitude
    final double longitude

    CoordinatesInput(double latitude, double longitude) {
        this.latitude = latitude
        this.longitude = longitude
    }
}
// end::constructor-input[]

// tag::sealed[]
sealed interface Shape permits Circle, Square {
}

@CompileStatic
@GraphQLType
final class Circle implements Shape {
    int radius = 2
}

@CompileStatic
@GraphQLType
final class Square implements Shape {
    int side = 3
}
// end::sealed[]

sealed interface Animal permits Dog, Cat {
    String getName()
}

@CompileStatic
@GraphQLType
final class Dog implements Animal {
    String name = 'Rex'
    int tricks = 4
}

@CompileStatic
@GraphQLType
final class Cat implements Animal {
    String name = 'Tom'
    int lives = 9
}

// tag::resolver[]
@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(env = GroovyFixtures.LANGUAGE)
class GroovyQueries {

    Member member() {
        return new Member()
    }

    Member echoMember(MemberInput input) {
        return new Member(name: input.name, age: input.age, active: input.active)
    }

    String describe(CoordinatesInput input) {
        return "${input.latitude},${input.longitude}"
    }

    GString greet(String name) {
        return "Hello, ${name}"
    }

    CompletionStage<GString> greetLater(String name) {
        return CompletableFuture.completedFuture("Hello, ${name}" as GString)
    }

    @ExecuteOn('io')
    GString greetLaterOnExecutor(String name) {
        return "${Thread.currentThread().name}:${name}"
    }

    List<Shape> shapes() {
        return [new Circle(), new Square()] as List<Shape>
    }

    List<Animal> animals() {
        return [new Dog(), new Cat()] as List<Animal>
    }
}
// end::resolver[]
