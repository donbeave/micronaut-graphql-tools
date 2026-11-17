package io.micronaut.graphql.tools.mapping.input;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A Java resolver for {@code InputGenericArgumentsSpec}: unlike a Groovy fixture, it casts list elements to the
 * declared class, so a wrong element type shows up as the {@link ClassCastException} a Java application gets.
 */
@Requires(property = "spec.name", value = "InputGenericArgumentsSpecJava")
@GraphQLRootResolver
public class JavaGenericArgumentsQuery {

    public String countries(List<JavaPriceSelection> selections) {
        List<String> result = new ArrayList<>();
        for (JavaPriceSelection selection : selections) {
            result.add(selection.getCountry());
        }
        return String.join(" ", result);
    }

    public String regions(List<? extends JavaPriceSelection> selections) {
        List<String> result = new ArrayList<>();
        for (JavaPriceSelection selection : selections) {
            result.add(selection.getClass().getSimpleName() + ":" + selection.getCountry());
        }
        return String.join(" ", result);
    }

    public String supers(List<? super JavaPriceSelection> selections) {
        List<String> result = new ArrayList<>();
        for (Object selection : selections) {
            result.add(selection.getClass().getSimpleName() + ":" + ((JavaPriceSelection) selection).getCountry());
        }
        return String.join(" ", result);
    }

    public String markets(Collection<JavaPriceSelection> selections) {
        List<String> result = new ArrayList<>();
        for (JavaPriceSelection selection : selections) {
            result.add(selection.getCountry());
        }
        return selections.getClass().getSimpleName() + ":" + String.join(" ", result);
    }

}
