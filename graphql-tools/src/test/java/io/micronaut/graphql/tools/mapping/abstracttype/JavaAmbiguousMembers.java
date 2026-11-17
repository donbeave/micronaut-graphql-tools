package io.micronaut.graphql.tools.mapping.abstracttype;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.graphql.tools.annotation.GraphQLType;

public final class JavaAmbiguousMembers {

    private JavaAmbiguousMembers() {
    }

    public interface Wrapped {
        String getLabel();
    }

    @GraphQLType
    public interface Boxed extends Wrapped {
    }

    @GraphQLType
    public interface Bagged extends Wrapped {
    }

    @Introspected
    public static class BoxedAndBagged implements Boxed, Bagged {
        @Override
        public String getLabel() {
            return "both";
        }
    }

    @Introspected
    public static class OnlyBoxed implements Boxed {
        @Override
        public String getLabel() {
            return "boxed";
        }
    }

    @GraphQLType
    public interface Marked extends Wrapped {
    }

    @GraphQLType
    public interface Stamped extends Wrapped {
    }

    @GraphQLType(Marked.class)
    @Introspected
    public static class MarkedAndStamped implements Marked, Stamped {
        @Override
        public String getLabel() {
            return "exact";
        }
    }

}
