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
