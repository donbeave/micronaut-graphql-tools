package io.micronaut.graphql.tools.ignore;

import io.micronaut.graphql.tools.annotation.GraphQLIgnore;
import io.micronaut.graphql.tools.annotation.GraphQLType;

/**
 * The getter of {@code name} is ignored: the type resolver binds the field, with no ambiguity.
 */
@GraphQLType
public class JavaIgnoreItem {

    @GraphQLIgnore
    public String getName() {
        return "getter";
    }

    public String getLabel() {
        return "label";
    }

}
