package io.micronaut.graphql.tools;

import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;

public class CustomWiring implements SchemaDirectiveWiring {

    @Override
    public GraphQLInputObjectField onInputObjectField(SchemaDirectiveWiringEnvironment<GraphQLInputObjectField> environment) {
        return SchemaDirectiveWiring.super.onInputObjectField(environment);
    }

    @Override
    public GraphQLInputObjectType onInputObjectType(SchemaDirectiveWiringEnvironment<GraphQLInputObjectType> environment) {
        return SchemaDirectiveWiring.super.onInputObjectType(environment);
    }

}
