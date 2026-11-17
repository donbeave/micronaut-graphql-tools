package io.micronaut.graphql.tools.mapping.resolver.inherited;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public abstract class JavaAbstractRootResolver {

    public abstract String hello();

}
