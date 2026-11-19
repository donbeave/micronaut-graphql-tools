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
package example.contracts

import example.data.Book
import example.graphql.type.resolver.BookLabelResolver
import example.graphql.type.resolver.BookLabelResolverContract
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.binding.internal.meta.ResolverContracts
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/** The contract that BookLabelResolver inherits is recorded by the visitor and read from the bean definition, with no reflection. */
@MicronautTest
class InheritedContractSpec extends Specification {

    @Inject
    ApplicationContext context

    def 'the visitor records the inherited contract'() {
        when:
        def metadata = context.getBeanDefinition(BookLabelResolver).annotationMetadata

        then:
        metadata.classValues(ResolverContracts, 'value').toList() == [Book]
        metadata.stringValues(ResolverContracts, 'declaredBy').toList() == [BookLabelResolverContract.name]
    }
}
