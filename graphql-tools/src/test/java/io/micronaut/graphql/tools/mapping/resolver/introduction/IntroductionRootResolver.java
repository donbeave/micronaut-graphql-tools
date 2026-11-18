package io.micronaut.graphql.tools.mapping.resolver.introduction;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Answering
@GraphQLRootResolver
@Requires(property = "spec.name", value = "IntroductionResolverSpec")
public interface IntroductionRootResolver {

    String hello();

    String goodbye();

}
