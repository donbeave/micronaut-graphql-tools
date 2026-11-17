package io.micronaut.graphql.tools.mapping.resolver.inherited;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

/**
 * An abstract root resolver: its subclass is the bean, through the inherited annotation, and implements the method.
 */
@GraphQLRootResolver
public abstract class JavaAbstractRootResolver {

    public abstract String hello();

}
