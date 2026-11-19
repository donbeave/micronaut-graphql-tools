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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The contract that BookLabelResolver inherits is recorded by the visitor and read from the bean definition, with no reflection. */
class InheritedContractTest {

    @Test
    fun theVisitorRecordsTheInheritedContract() {
        ApplicationContext.run("test").use { context ->
            check(context)
        }
    }

    private fun check(context: ApplicationContext) {
        val metadata = context.getBeanDefinition(BookLabelResolver::class.java).annotationMetadata

        val contracts = metadata.findAnnotation<Annotation>(ResolverContracts::class.java.name).get()
        val models: List<String> = contracts.annotationClassValues("value").map { it.name }
        val declaredBy: List<String> = contracts.stringValues("declaredBy").toList()

        assertEquals(listOf(Book::class.java.name), models)
        assertEquals(listOf(BookLabelResolverContract::class.java.name), declaredBy)
    }
}
