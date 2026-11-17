package io.micronaut.graphql.tools.mapping.input;

import io.micronaut.graphql.tools.annotation.GraphQLInput;

/**
 * A Java input class for {@code InputGenericArgumentsSpec}.
 */
@GraphQLInput
public class JavaPriceSelection {

    private String country;

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

}
