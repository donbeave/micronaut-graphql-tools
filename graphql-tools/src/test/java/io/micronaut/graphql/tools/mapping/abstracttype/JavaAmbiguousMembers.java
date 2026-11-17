package io.micronaut.graphql.tools.mapping.abstracttype;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.graphql.tools.annotation.GraphQLType;

/**
 * Java fixtures for {@code AbstractTypeAmbiguitySpec}: members whose Java types are unrelated interfaces, and a class
 * with a bean introspection that implements both. Nothing else may implement two of them.
 */
public final class JavaAmbiguousMembers {

    private JavaAmbiguousMembers() {
    }

    /** The Java type the field declares. */
    public interface Wrapped {
        String getLabel();
    }

    @GraphQLType
    public interface Boxed extends Wrapped {
    }

    @GraphQLType
    public interface Bagged extends Wrapped {
    }

    /** An instance of the Java types of both members, with an introspection: ambiguous. */
    @Introspected
    public static class BoxedAndBagged implements Boxed, Bagged {
        @Override
        public String getLabel() {
            return "both";
        }
    }

    /** An instance of one member only. */
    @Introspected
    public static class OnlyBoxed implements Boxed {
        @Override
        public String getLabel() {
            return "boxed";
        }
    }

}
