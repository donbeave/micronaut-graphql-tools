package io.micronaut.graphql.tools.mapping.resolver.introduction;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;

@Answering
@GraphQLRootResolver
@Requires(property = "spec.name", value = "IntroductionCoordinateSpec")
public interface IntroductionCoordinateResolver {

    @GraphQLSchemaCoordinate("Query.hello")
    String greeting();

}
