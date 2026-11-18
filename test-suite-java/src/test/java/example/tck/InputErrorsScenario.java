package example.tck;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.GraphQLScalarMapping;
import io.micronaut.graphql.tools.annotation.GraphQLInput;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The fixtures of the TCK scenario {@code input-errors}.
 */
public final class InputErrorsScenario {

    private InputErrorsScenario() {
    }

    @GraphQLInput
    public static class Basket {
        private List<BasketLine> lines;

        public List<BasketLine> getLines() {
            return lines;
        }

        public void setLines(List<BasketLine> lines) {
            this.lines = lines;
        }
    }

    @GraphQLInput
    public static class BasketLine {
        private UUID ticket;

        public UUID getTicket() {
            return ticket;
        }

        public void setTicket(UUID ticket) {
            this.ticket = ticket;
        }
    }

    @GraphQLInput
    public static class Guarded {
        public int getSize() {
            return 0;
        }

        public void setSize(int size) {
            throw new IllegalStateException("internal detail");
        }
    }

    @Factory
    @Requires(property = "tck.scenario", value = "input-errors")
    public static class Scalars {

        @Bean
        @Singleton
        GraphQLScalarMapping ticket() {
            return GraphQLScalarMapping.builder(GraphQLScalarType.newScalar().name("Ticket").coercing(new Coercing<Object, String>() {
                @Override
                public String serialize(Object value) {
                    return value.toString();
                }

                @Override
                public Object parseValue(Object value) {
                    return value;
                }

                @Override
                public Object parseLiteral(Object value) {
                    return ((StringValue) value).getValue();
                }
            }).build()).outputClasses(UUID.class).inputClasses(UUID.class).build();
        }

        @Bean
        @Singleton
        GraphQLScalarMapping blob() {
            return GraphQLScalarMapping.builder(GraphQLScalarType.newScalar().name("Blob").coercing(new Coercing<Object, Object>() {
                @Override
                public Object serialize(Object value) {
                    return value;
                }

                @Override
                public Object parseValue(Object value) {
                    return value;
                }

                @Override
                public Object parseLiteral(Object value) {
                    throw new UnsupportedOperationException();
                }
            }).build()).outputClasses(Map.class).inputClasses(Map.class).build();
        }
    }

    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "input-errors")
    public static class Queries {

        public String fine() {
            return "fine";
        }

        public String ticket(UUID id) {
            return id.toString();
        }

        public String ticketNonNull(UUID id) {
            return id.toString();
        }

        public String blob(Map<String, Object> data) {
            return data.toString();
        }

        public String basket(Basket basket) {
            return basket.getLines().get(0).getTicket().toString();
        }

        public String guarded(Guarded guarded) {
            return "unreachable";
        }
    }

}
