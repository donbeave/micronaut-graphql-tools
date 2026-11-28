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
package io.micronaut.graphql.tools.codegen.ir.client;

import io.micronaut.graphql.tools.codegen.Origin;
import io.micronaut.graphql.tools.codegen.ir.shared.Names;
import io.micronaut.graphql.tools.codegen.ir.shared.Operation;
import io.micronaut.graphql.tools.codegen.ir.shared.TypeRef;

import java.util.List;
import java.util.Objects;

/**
 * An operation of a client: the assembled document and what a request class needs to send it and to read the response.
 *
 * <p>{@code documentText} is the document that the compile-time checks of the processor assemble and validate: the operation first,
 * then the fragments it uses by name, printed compactly, with {@code __typename} selected in every selection set of an interface
 * or union. {@code documentHash} is a SHA-256 of its UTF-8 bytes.</p>
 *
 * @param names        the name of the operation and of its request class
 * @param kind         whether it is a query, a mutation or a subscription
 * @param variables    the variables in the order of the declaration
 * @param variablesType the name of the generated type that holds the variables
 * @param payload      whether the result is the value of the only root field or the {@code data} object
 * @param result       the type of the result: for {@link Payload#DATA} a reference to the generated data model, for
 *                     {@link Payload#FIELD} the type of the root field
 * @param selection    the selection set of the operation after the injection of {@code __typename}
 * @param documentText the assembled document
 * @param documentHash the SHA-256 of {@code documentText} as 64 lower-case hex digits
 * @param fragments    the names of the fragments the document carries, in the order of the document (by name)
 * @param origin       where the document defines the operation
 */
public record OperationDocument(Names names, Operation kind, List<Variable> variables, Names variablesType, Payload payload,
                                TypeRef result, SelectionSet selection, String documentText, String documentHash,
                                List<String> fragments, Origin origin) {

    /**
     * Creates an operation, keeping immutable copies of the lists.
     */
    public OperationDocument {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(variablesType, "variablesType");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(documentText, "documentText");
        Objects.requireNonNull(documentHash, "documentHash");
        Objects.requireNonNull(origin, "origin");
        variables = List.copyOf(variables);
        fragments = List.copyOf(fragments);
    }
}
