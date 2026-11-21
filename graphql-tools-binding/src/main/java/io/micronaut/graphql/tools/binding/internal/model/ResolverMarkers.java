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
package io.micronaut.graphql.tools.binding.internal.model;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the marker interfaces ({@code QueryResolver}, {@code MutationResolver}, {@code SubscriptionResolver} and
 * {@code TypeResolver<T>}) fix for a resolver class. The visitor computes it from the hierarchy of the class and writes it
 * into the annotation metadata; the startup reads it from there. The visitor and the startup ask the same questions of
 * it, so a contract method binds the same way at both.
 *
 * @param operations      the root operations ({@code query}, {@code mutation}, {@code subscription}) that the markers of
 *                        the class name, sorted; empty when no marker names one, which leaves every root type open
 * @param sourceType      the binary name of the type argument of the marker {@code TypeResolver}, or {@code null}
 * @param contractMethods the methods of the contract interfaces that the class implements, one entry per method of each
 *                        contract (an interface that extends a marker); {@code null} when the class implements no contract
 *                        interface that declares a method, so every public method is a candidate
 */
@Internal
public record ResolverMarkers(List<String> operations, @Nullable String sourceType,
                              @Nullable List<ContractMethod> contractMethods) {

    /** A class that no marker touches. */
    public static final ResolverMarkers NONE = new ResolverMarkers(List.of(), null, null);

    /**
     * @param name       the name of a method
     * @param parameters its number of parameters, without the continuation of a Kotlin suspend function
     * @return whether a marker contract leaves the method a candidate for binding: a method of the class implements a
     * contract method when it has its name and its number of parameters
     */
    public boolean admits(String name, int parameters) {
        return contractMethods == null || contractMethods.stream().anyMatch(it -> it.matches(name, parameters));
    }

    /**
     * @param name       the name of a method
     * @param parameters its number of parameters
     * @return whether the method is a candidate on the root side: no contract interface declares methods, or a contract
     * that is not bound to a source type declares it
     */
    public boolean admitsRoot(String name, int parameters) {
        return contractMethods == null
                || contractMethods.stream().anyMatch(it -> it.matches(name, parameters) && it.root());
    }

    /**
     * @param name       the name of a method
     * @param parameters its number of parameters
     * @return whether the method is a candidate on the type side: no contract interface declares methods, or a contract
     * that names a source type declares it. A bean that mixes a root marker and a {@code TypeResolver} contract binds each
     * method on the side of the contract that declares it.
     */
    public boolean admitsType(String name, int parameters) {
        return contractMethods == null
                || contractMethods.stream().anyMatch(it -> it.matches(name, parameters) && it.model() != null);
    }

    /**
     * @param name       the name of a method
     * @param parameters its number of parameters
     * @return the root operations whose fields the method may bind, sorted; empty when every root type is open. The
     * operations of the contracts that declare the method decide, not those of the other contracts of the class.
     */
    public List<String> operationsOf(String name, int parameters) {
        if (contractMethods == null) {
            return operations;
        }
        Set<String> result = new TreeSet<>();
        boolean found = false;
        for (ContractMethod method : contractMethods) {
            if (method.matches(name, parameters) && method.root()) {
                found = true;
                if (method.operations().isEmpty()) {
                    return List.of();
                }
                result.addAll(method.operations());
            }
        }
        return found ? List.copyOf(result) : operations;
    }

    /**
     * @param name       the name of a method
     * @param parameters its number of parameters
     * @return the binary names of the source types of the contracts that declare the method; empty when no contract that
     * declares it names one
     */
    public List<String> modelsOf(String name, int parameters) {
        if (contractMethods == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (ContractMethod method : contractMethods) {
            if (method.matches(name, parameters) && method.model() != null && !result.contains(method.model())) {
                result.add(method.model());
            }
        }
        return result;
    }

    /**
     * A method that one contract interface declares, with what that contract fixes.
     *
     * @param operations the root operations of the contract, sorted; empty for a contract that fixes none
     * @param model      the binary name of the source type of the contract, or {@code null} when it names none
     * @param name       the name of the method
     * @param arity      the number of parameters of the method, without the continuation of a Kotlin suspend function
     */
    public record ContractMethod(List<String> operations, @Nullable String model, String name, int arity) {

        private static final char SEPARATOR = '|';

        /**
         * @return the entry as the annotation metadata stores it
         */
        public String encode() {
            return String.join(",", operations) + SEPARATOR + (model == null ? "" : model) + SEPARATOR + name + '/' + arity;
        }

        /**
         * @param entry an entry written by {@link #encode()}
         * @return the method
         */
        public static ContractMethod decode(String entry) {
            int first = entry.indexOf(SEPARATOR);
            int second = entry.indexOf(SEPARATOR, first + 1);
            int slash = entry.lastIndexOf('/');
            String operations = entry.substring(0, first);
            String model = entry.substring(first + 1, second);
            return new ContractMethod(operations.isEmpty() ? List.of() : List.of(operations.split(",")),
                    model.isEmpty() ? null : model, entry.substring(second + 1, slash),
                    Integer.parseInt(entry.substring(slash + 1)));
        }

        private boolean matches(String methodName, int parameters) {
            return arity == parameters && name.equals(methodName);
        }

        private boolean root() {
            return model == null || !operations.isEmpty();
        }
    }
}
