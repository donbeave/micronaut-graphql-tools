package io.micronaut.graphql.tools.mapping.resolver.inherited;

import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "InheritedConcreteResolverSpec")
public class JavaOverridingRootResolver extends JavaConcreteRootResolver {

    @Override
    public String hello() {
        return "from the bean";
    }

}
