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
package example.enums;

import example.graphql.type.SecurityErrorCode;
import example.graphql.type.ValidationErrorCode;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLSchema;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

@MicronautTest
class EnumValuesTest {

    @Inject
    GraphQLSchema schema;

    @Test
    void schemaEnumValuesAreTheJavaConstants() {
        GraphQLEnumType security = (GraphQLEnumType) schema.getType("SecurityErrorCode");
        assertSame(SecurityErrorCode.NOT_AUTHENTICATED, security.getValue("NOT_AUTHENTICATED").getValue());
        assertSame(SecurityErrorCode.INCORRECT_CREDENTIALS, security.getValue("INCORRECT_CREDENTIALS").getValue());
        GraphQLEnumType validation = (GraphQLEnumType) schema.getType("ValidationErrorCode");
        assertSame(ValidationErrorCode.DUPLICATE, validation.getValue("DUPLICATE").getValue());
    }

}
