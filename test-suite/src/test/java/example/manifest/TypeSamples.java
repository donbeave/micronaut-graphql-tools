package example.manifest;

import graphql.execution.DataFetcherResult;
import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Methods whose return and parameter types the binding manifest renders: primitives, arrays, nested classes and
 * generics.
 */
@Singleton
@Executable
public class TypeSamples {

    public int primitive() {
        return 0;
    }

    public int[] primitiveArray() {
        return new int[0];
    }

    public String[] objectArray() {
        return new String[0];
    }

    public byte[] bytes() {
        return new byte[0];
    }

    public Outer.Inner nested() {
        return null;
    }

    public List<String> list() {
        return null;
    }

    public Map<String, List<Long>> map() {
        return null;
    }

    public CompletionStage<Optional<Outer.Inner>> wrapped() {
        return null;
    }

    public List<DataFetcherResult<Outer.Inner>> results() {
        return null;
    }

    public Map<Outer.Inner, Map<String, int[]>> deep() {
        return null;
    }

    public void parameters(Map<String, Integer> map, long count, Outer.Inner[] inners, List<Outer.Inner> list) {
    }

    public List<? extends Number> wildcard() {
        return null;
    }

    @SuppressWarnings("rawtypes")
    public List rawList() {
        return null;
    }

    public List<String>[] genericArray() {
        return null;
    }

    public void unusual(List<? super Integer> lower, @SuppressWarnings("rawtypes") Map raw, List<Long>[] arrays) {
    }

    /**
     * A class with a nested class.
     */
    public static final class Outer {

        /**
         * A nested class.
         */
        public static final class Inner {
        }

    }

}
