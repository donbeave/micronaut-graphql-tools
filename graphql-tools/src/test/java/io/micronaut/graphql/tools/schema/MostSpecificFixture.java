package io.micronaut.graphql.tools.schema;

final class MostSpecificFixture {

    private MostSpecificFixture() {
    }

    public static class Base {
    }

    public static class Entity extends Base {
    }

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
