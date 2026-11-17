package io.micronaut.graphql.tools.schema;

/**
 * Plain Java types for resolving an object that is an instance of several mapped Java types: an entity hierarchy with
 * a runtime subclass, and two unrelated interfaces.
 */
final class MostSpecificFixture {

    private MostSpecificFixture() {
    }

    public static class Base {
    }

    public static class Entity extends Base {
    }

    /** Stands for a generated subclass, such as a lazy-loading proxy of an entity. */
    public static class Proxy extends Entity {
    }

    public interface Left {
    }

    public interface Right {
    }

    public static class OnlyLeft implements Left {
    }

    public static class OnlyRight implements Right {
    }

    public static class Both implements Left, Right {
    }

}
