package io.micronaut.graphql.tools.ignore;

import io.micronaut.graphql.tools.annotation.GraphQLIgnore;
import io.micronaut.graphql.tools.annotation.GraphQLType;

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
