package example.tck

import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.GraphQLScalarMapping
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton

// The fixtures of the TCK scenario `input-errors`.

@CompileStatic
@GraphQLInput
class Basket {
    List<BasketLine> lines
}

@CompileStatic
@GraphQLInput
class BasketLine {
    UUID ticket
}

@CompileStatic
@GraphQLInput
class Guarded {
    int size

    void setSize(int size) {
        throw new IllegalStateException('internal detail')
    }
}

@CompileStatic
@Factory
@Requires(property = 'tck.scenario', value = 'input-errors')
class InputErrorsScalars {

    @Bean
    @Singleton
    GraphQLScalarMapping ticket() {
        return GraphQLScalarMapping.builder(GraphQLScalarType.newScalar().name('Ticket').coercing(new Coercing<Object, String>() {
            @Override
            String serialize(Object value) {
                return value.toString()
            }

            @Override
            Object parseValue(Object value) {
                return value
            }

            @Override
            Object parseLiteral(Object value) {
                return ((StringValue) value).value
            }
        }).build()).outputClasses(UUID).inputClasses(UUID).build()
    }

    @Bean
    @Singleton
    GraphQLScalarMapping blob() {
        return GraphQLScalarMapping.builder(GraphQLScalarType.newScalar().name('Blob').coercing(new Coercing<Object, Object>() {
            @Override
            Object serialize(Object value) {
                return value
            }

            @Override
            Object parseValue(Object value) {
                return value
            }

            @Override
            Object parseLiteral(Object value) {
                throw new UnsupportedOperationException()
            }
        }).build()).outputClasses(Map).inputClasses(Map).build()
    }
}

@CompileStatic
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'input-errors')
class InputErrorsQueries {

    String fine() {
        return 'fine'
    }

    String ticket(UUID id) {
        return id.toString()
    }

    String ticketNonNull(UUID id) {
        return id.toString()
    }

    String blob(Map<String, Object> data) {
        return data.toString()
    }

    String basket(Basket basket) {
        return basket.lines[0].ticket.toString()
    }

    String guarded(Guarded guarded) {
        return 'unreachable'
    }
}
