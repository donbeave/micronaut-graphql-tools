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
package io.micronaut.graphql.tools.codegen.ir;

import io.micronaut.graphql.tools.codegen.Origin;
import io.micronaut.graphql.tools.codegen.ir.shared.Names;
import io.micronaut.graphql.tools.codegen.ir.shared.Operation;

import java.util.List;
import java.util.Objects;

/**
 * A resolver contract: an interface that extends a marker interface of the library and declares the resolver methods. The
 * default grouping is one contract for each field of a root operation type, named by the {@code contractNameTemplate} option.
 *
 * @param operation the operation of the root type
 * @param names     the coordinate of the field as the SDL name, and the generated name in the resolver package
 * @param marker    the qualified name of the marker interface that the contract extends
 * @param methods   the methods of the contract
 * @param origin    where the first field of the contract is defined
 */
public record ResolverContract(Operation operation, Names names, String marker, List<ContractMethod> methods, Origin origin) {

    /**
     * Creates a contract, keeping an immutable copy of the list.
     */
    public ResolverContract {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(origin, "origin");
        methods = List.copyOf(methods);
    }
}
