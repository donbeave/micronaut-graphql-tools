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
package example.directives;

import example.AbstractTest;
import example.client.ExampleClient.Directives;
import graphql.schema.GraphQLSchema;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class DirectiveWiringTest extends AbstractTest {

    @Inject
    GraphQLSchema schema;

    @Test
    void aNamedSchemaDirectiveRunsOnTheFieldThatAppliesIt() {
        GraphQLResponse<Directives> response = execute();

        assertEquals("QUIET", response.data().shout());
    }

    @Test
    void anEveryElementSchemaDirectiveRunsOnTheFieldThatAppliesIt() {
        GraphQLResponse<Directives> response = execute();

        assertEquals("[t] value", response.data().tagged());
    }

    @Test
    void aWiringDoesNotChangeAFieldThatDoesNotApplyItsDirective() {
        assertEquals("introduced introduced", client.introduced().data());
    }

    @Test
    void aSchemaLevelDirectiveIsHandledByTheSchemaCustomizerThatItIsListedFor() {
        assertNotNull(schema.getSchemaAppliedDirective("audit"));
        assertEquals("audited", execute().data().__schema().description());
    }

    private GraphQLResponse<Directives> execute() {
        GraphQLResponse<Directives> response = client.directives();
        assertTrue(response.errors().isEmpty(), String.valueOf(response.errors()));
        assertNotNull(response.data());
        return response;
    }

}
