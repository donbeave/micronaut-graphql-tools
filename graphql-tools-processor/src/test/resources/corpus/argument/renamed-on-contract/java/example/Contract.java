package example;

import io.micronaut.graphql.tools.annotation.GraphQLArgument;

public interface Contract {

    String size(@GraphQLArgument("short") Integer width, @GraphQLArgument("long") Integer length);

}
