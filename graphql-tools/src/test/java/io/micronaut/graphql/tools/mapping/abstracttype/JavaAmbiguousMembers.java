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

    /**
     * The members of the exact-class case. They are separate from Boxed and Bagged, so that {@link BoxedAndBagged} is
     * not ambiguous in that case's union.
     */
    @GraphQLType
    public interface Marked extends Wrapped {
    }

    @GraphQLType
    public interface Stamped extends Wrapped {
    }

    /** An instance of both exact-case members, mapped by its exact class to {@link Marked}: not ambiguous. */
    @GraphQLType(Marked.class)
    @Introspected
    public static class MarkedAndStamped implements Marked, Stamped {
        @Override
        public String getLabel() {
            return "exact";
        }
    }

}
