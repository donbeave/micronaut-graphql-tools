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
package io.micronaut.graphql.tools.internal

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLIgnore
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames
import io.micronaut.graphql.tools.binding.internal.model.JClass
import io.micronaut.graphql.tools.binding.internal.model.Nullability
import io.micronaut.graphql.tools.binding.internal.model.TypeRef
import io.micronaut.graphql.tools.binding.internal.rules.EnumTypeRules
import io.micronaut.graphql.tools.binding.internal.rules.InputTypeRules
import io.micronaut.inject.BeanDefinition

/** The runtime adapter of the type model, read from real beans and real introspections. */
class RuntimeTypeModelSpec extends AbstractTest {

    static final String SPEC_NAME = "RuntimeTypeModelSpec"

    static final String SCHEMA = """
schema { query: Query }
type Query { book(id: ID!, filter: BookFilter, genre: Genre): Book }
type Book { title: String }
input BookFilter { title: String }
enum Genre { FICTION }
"""

    private RuntimeTypeModel model = new RuntimeTypeModel()

    private BeanDefinition<?> definition(Class<?> type) {
        applicationContext.getBeanDefinition(type)
    }

    void setup() {
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.strict': false])
    }

    void "annotation names are the names of the annotation classes"() {
        expect:
        AnnotationNames.IGNORE == GraphQLIgnore.name
        AnnotationNames.ROOT_RESOLVER == GraphQLRootResolver.name
        AnnotationNames.TYPE_RESOLVER == GraphQLTypeResolver.name
        AnnotationNames.INPUT == GraphQLInput.name
        AnnotationNames.TYPE == GraphQLType.name
        AnnotationNames.SCHEMA_COORDINATE == io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate.name
        AnnotationNames.ARGUMENT == io.micronaut.graphql.tools.annotation.GraphQLArgument.name
        AnnotationNames.FIELD == io.micronaut.graphql.tools.annotation.GraphQLField.name
    }

    void "a resolver bean exposes its executable methods, its annotations and its parameters"() {
        when:
        JClass type = model.of(definition(Query))
        def book = type.bindableMethods().find { it.name() == 'book' }

        then:
        type.name() == Query.name
        type.kind() == JClass.Kind.CLASS
        type.annotations().has(AnnotationNames.ROOT_RESOLVER)
        type.bindableMethods()*.name().sort() == ['book', 'books', 'hidden']
        book.signature() == "${Query.name}#book(java.lang.String,${BookFilter.name},${Genre.name})".toString()
        book.declaringType().name() == Query.name
        book.parameters()*.name() == ['id', 'filter', 'genre']
        book.parameters().every { !it.nameIsSynthetic() }
        book.parameters()[1].annotations().has(AnnotationNames.INPUT) == false
        book.genericReturnType().rawName() == Book.name
        !book.suspend()
        type.bindableMethods().find { it.name() == 'hidden' }.annotations().has(AnnotationNames.IGNORE)
    }

    void "a type reference resolves only after the class that reaches it was resolved"() {
        given:
        def fresh = new RuntimeTypeModel()

        when:
        fresh.resolve(TypeRef.of(Book.name))

        then:
        thrown(IllegalArgumentException)

        when:
        def book = fresh.of(definition(Query)).bindableMethods().find { it.name() == 'book' }
        def returned = fresh.resolve(book.genericReturnType())

        then:
        returned.name() == Book.name
        !returned.introspected() || returned.introspected()
    }

    void "generic types and nullability are read from the arguments"() {
        when:
        def method = model.of(definition(Query)).bindableMethods().find { it.name() == 'books' }

        then:
        method.genericReturnType().rawName() == List.name
        method.genericReturnType().typeArguments()*.rawName() == [Book.name]
        method.parameters()[0].type().nullability() == Nullability.NULLABLE
        method.parameters()[1].type().primitive
    }

    void "the properties of an introspected class are read, and the settable ones are those the converter can set"() {
        when:
        JClass input = model.of(BeanIntrospector.SHARED.getIntrospection(BookFilter))

        then:
        input.introspected()
        input.readableProperties()*.name().sort() == ['internal', 'title']
        input.writableProperties()*.name().sort() == ['internal', 'title']
        input.writableProperties().find { it.name() == 'title' }.accessorText() == 'getTitle()'
        input.writableProperties().find { it.name() == 'internal' }.annotations().has(AnnotationNames.IGNORE)
        input.writableProperties().find { it.name() == 'title' }.type().rawName() == String.name
    }

    void "the input rule reads the adapter: the property that no input field names is unmapped or ignored"() {
        when:
        def verdict = InputTypeRules.check(['title'], model.of(BeanIntrospector.SHARED.getIntrospection(BookFilter)))

        then:
        verdict.unmappedProperties().isEmpty()
        verdict.ignoredProperties() == ["${BookFilter.name}#internal".toString()]

        when:
        verdict = InputTypeRules.check([], model.of(BeanIntrospector.SHARED.getIntrospection(BookFilter)))

        then:
        verdict.unmappedProperties() == ['title']
    }

    void "the enum rule reads the adapter: constants, the schema values they miss and the ignore hatch"() {
        when:
        def verdict = EnumTypeRules.check(['FICTION', 'SCIENCE'], model.of(Genre))

        then:
        model.of(Genre).kind() == JClass.Kind.ENUM
        model.of(Genre).enumConstants()*.name() == ['FICTION', 'DRAFT', 'POETRY']
        verdict.missingSchemaValues() == ['SCIENCE']
        verdict.unboundConstants() == ['POETRY']
        verdict.ignoredConstants() == ['DRAFT']
    }

    void "supertypes, assignability and generic markers"() {
        when:
        JClass type = model.of(Titles)

        then:
        type.isAssignableTo(Comparator.name)
        type.isAssignableTo(Titles.name)
        type.isAssignableTo(Object.name)
        !type.isAssignableTo(List.name)
        type.typeArgumentsOf(Comparator.name)*.rawName() == [String.name]
        type.typeArgumentsOf(List.name).isEmpty()
        model.of(Query).supertypesAnnotatedWith(AnnotationNames.ROOT_RESOLVER).isEmpty()
    }

    void "the kinds of class"() {
        expect:
        model.of(Comparator).kind() == JClass.Kind.INTERFACE
        model.of(AbstractList).kind() == JClass.Kind.ABSTRACT_CLASS
        model.of(Book).kind() == JClass.Kind.CLASS
        model.of(Query).enumConstants().isEmpty()
        !model.of(Titles).introspected()
        model.of(Titles).readableProperties().isEmpty()
        model.of(Titles).bindableMethods().isEmpty()
    }

    void "the bean model lists the enabled resolver beans of the registry"() {
        given:
        // the factory destroys the registry once it has bound the schema, so take it first
        def registry = applicationContext.getBean(GraphQLResolversRegistry)
        getGraphQLBean()
        def beans = new RuntimeBeanModel(registry, model)

        expect:
        beans.rootResolvers()*.type()*.name() == [Query.name]
        beans.rootResolvers()*.beanDescription() == [Query.name]
        beans.typeResolvers()*.type()*.name() == [BookResolver.name]
        beans.typeResolvers().first().contractTypes() == [Book.name]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {

        Book book(String id, BookFilter filter, Genre genre) {
            null
        }

        List<Book> books(@io.micronaut.core.annotation.Nullable String name, int limit) {
            []
        }

        @GraphQLIgnore
        String hidden() {
            'hidden'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(Book)
    static class BookResolver {
        String title(Book book) {
            'title'
        }
    }

    @GraphQLType
    static class Book {
    }

    @GraphQLInput
    static class BookFilter {
        String title
        @GraphQLIgnore
        String internal
    }

    @Introspected
    enum Genre {
        FICTION,
        @GraphQLIgnore DRAFT,
        POETRY
    }

    static class Titles implements Comparator<String> {
        @Override
        int compare(String first, String second) {
            first <=> second
        }
    }
}
