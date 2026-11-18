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
package example.qualified;

import com.apollographql.apollo.api.ApolloResponse;
import com.apollographql.java.rx3.Rx3Apollo;
import example.AbstractTest;
import example.client.AllBooksQuery;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

@MicronautTest(environments = QualifiedBeansFactory.ENVIRONMENT)
class QualifiedBeansTest extends AbstractTest {

    @Inject
    ApplicationContext applicationContext;

    @Inject
    GraphQLSchema schema;

    @Inject
    GraphQL graphQL;

    @Inject
    @Named("admin")
    GraphQL adminGraphQL;

    @Test
    void qualifiedBeansDoNotReplaceTheLibraryBeans() {
        assertEquals(2, applicationContext.getBeansOfType(GraphQL.class).size());
        assertEquals(2, applicationContext.getBeansOfType(GraphQLSchema.class).size());
        assertNotSame(adminGraphQL, graphQL);
        assertSame(schema, graphQL.getGraphQLSchema());
        assertNotNull(schema.getQueryType().getFieldDefinition("allBooks"));
    }

    @Test
    void requestsRunTheLibrarySchema() {
        ApolloResponse<AllBooksQuery.Data> response =
                Rx3Apollo.single(apolloClient.query(new AllBooksQuery())).blockingGet();

        assertFalse(response.hasErrors());
        assertNotNull(response.data);
        assertEquals(5, response.data.allBooks.size());
    }

}
