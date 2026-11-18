package io.micronaut.graphql.tools.mapping.resolver.inherited;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public abstract class JavaConcreteRootResolver {

    public String hello() {
        return "from the superclass";
    }

}
