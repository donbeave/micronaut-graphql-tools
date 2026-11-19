package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public record Book(String title, Genre genre) {
}
