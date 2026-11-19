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
package example.contracts;

import example.data.Book;
import example.graphql.type.resolver.BookLabelResolver;
import example.graphql.type.resolver.BookLabelResolverContract;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.graphql.tools.binding.internal.meta.ResolverContracts;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The contract that BookLabelResolver inherits is recorded by the visitor and read from the bean definition, with no reflection. */
@MicronautTest
class InheritedContractTest {

    @Inject
    ApplicationContext context;

    @Test
    void theVisitorRecordsTheInheritedContract() {
        AnnotationMetadata metadata = context.getBeanDefinition(BookLabelResolver.class).getAnnotationMetadata();

        assertEquals(List.of(Book.class), List.of(metadata.classValues(ResolverContracts.class, "value")));
        assertEquals(List.of(BookLabelResolverContract.class.getName()),
                List.of(metadata.stringValues(ResolverContracts.class, "declaredBy")));
    }
}
