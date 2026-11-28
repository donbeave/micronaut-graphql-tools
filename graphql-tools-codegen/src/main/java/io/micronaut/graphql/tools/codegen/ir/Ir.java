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

import io.micronaut.graphql.tools.codegen.ir.client.FragmentDefinition;
import io.micronaut.graphql.tools.codegen.ir.client.OperationDocument;

import java.util.List;
import java.util.Objects;

/**
 * The intermediate representation of a schema, as the rules produce it from the merged definitions and the options. Every list
 * is in schema order: the definitions of the schema files in the order of their relative paths, then the extensions of a type
 * after its base definition. The IR holds every decision, so an emitter makes no naming or mapping decision.
 *
 * <p>Server mode fills the lists of the schema and the contracts. Client mode fills the operations and the fragments that they
 * use, and derives the result models of the operations into {@code models} and {@code abstractTypes}; it lists only the enums,
 * inputs and scalars that an operation reaches, and no contract.</p>
 *
 * @param enums         the enum types
 * @param inputs        the input types
 * @param models        the object types that are generated as models
 * @param abstractTypes the interfaces, unions and selected object types that are generated as interfaces
 * @param scalars       the custom scalars with a mapping
 * @param contracts     the resolver contracts
 * @param nameClasses   the classes of name constants, in the order of the types
 * @param operations    the operations of a client, in the order of their documents; empty in server mode
 * @param fragments     the named fragments that the operations use, by name; empty in server mode
 */
public record Ir(List<EnumType> enums, List<InputType> inputs, List<ModelType> models, List<AbstractType> abstractTypes,
                 List<ScalarType> scalars, List<ResolverContract> contracts, List<NameClass> nameClasses,
                 List<OperationDocument> operations, List<FragmentDefinition> fragments) {

    /** An IR without a node. */
    public static final Ir EMPTY = new Ir(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of());

    /**
     * Creates an IR, keeping immutable copies of the lists.
     */
    public Ir {
        Objects.requireNonNull(enums, "enums");
        enums = List.copyOf(enums);
        inputs = List.copyOf(inputs);
        models = List.copyOf(models);
        abstractTypes = List.copyOf(abstractTypes);
        scalars = List.copyOf(scalars);
        contracts = List.copyOf(contracts);
        nameClasses = List.copyOf(nameClasses);
        operations = List.copyOf(operations);
        fragments = List.copyOf(fragments);
    }
}
