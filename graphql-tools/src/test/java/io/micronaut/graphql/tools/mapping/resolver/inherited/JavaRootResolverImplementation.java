package io.micronaut.graphql.tools.mapping.resolver.inherited;

import io.micronaut.context.annotation.Requires;

/**
 * The bean of {@link JavaAbstractRootResolver}: no resolver annotation of its own.
 */
@Requires(property = "spec.name", value = "InheritedAbstractResolverSpec")
public class JavaRootResolverImplementation extends JavaAbstractRootResolver {

    @Override
    public String hello() {
        return "hello";
    }

}
