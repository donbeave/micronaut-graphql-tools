/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.manifest;

import graphql.execution.DataFetcherResult;
import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

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

    public static final class Outer {

        public static final class Inner {
        }

    }

}
