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
package io.micronaut.graphql.tools.mapping.resolver.inherited

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton

import java.lang.annotation.Inherited

class InheritedAnnotationSpec extends AbstractTest {

    static final String SPEC_NAME = 'InheritedAnnotationSpec'

    void "the root and the type resolver annotations are both @Inherited, on purpose"() {
        expect:
        GraphQLRootResolver.isAnnotationPresent(Inherited)
        GraphQLTypeResolver.isAnnotationPresent(Inherited)
    }

    void "a subclass without an annotation of its own is a root resolver and a type resolver through its superclass"() {
        given:
        startContext('''schema { query: Query }
type Query { greeting: String }
type Book { title: String, label: String }
''', SPEC_NAME, ['micronaut.graphql.tools.strict': false])

        when:
        def result = executeQuery('{ greeting }')

        then:
        result.errors.isEmpty()
        result.data.greeting == 'hello'
        applicationContext.getBeanDefinition(RootChild).hasAnnotation(GraphQLRootResolver)
        applicationContext.getBeanDefinition(TypeChild).hasAnnotation(GraphQLTypeResolver)
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class RootParent {
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class RootChild extends RootParent {
        String greeting() {
            'hello'
        }
    }

    @GraphQLType
    static class Book {
        String title
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLTypeResolver(Book)
    static class TypeParent {
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class TypeChild extends TypeParent {
        String label(Book book) {
            'label ' + book.title
        }
    }

}
