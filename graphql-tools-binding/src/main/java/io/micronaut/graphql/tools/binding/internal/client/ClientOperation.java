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
package io.micronaut.graphql.tools.binding.internal.client;

import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.List;
import java.util.Map;

/**
 * An operation of a client, assembled: the operation and the fragments it uses as one document, and what the runtime needs to
 * send it and to read the response without parsing GraphQL.
 *
 * @param document      the assembled document, printed compactly: the operation first, then the fragments it uses by name
 * @param operationName the name of the operation, empty for an anonymous operation
 * @param operationType {@code query}, {@code mutation} or {@code subscription}
 * @param variables     the names of the variables the operation declares, without the dollar sign, in declaration order
 * @param payload       whether the payload is the value of the only root field or the whole {@code data} object
 * @param definition    the operation as it is printed in the document, with the type name injected
 * @param fragments     the fragments that the operation uses, as printed, by name
 * @param schema        the client schema that the document was validated against, null when the checks are off
 */
@Internal
public record ClientOperation(String document, String operationName, String operationType, List<String> variables,
                              PayloadMode payload, OperationDefinition definition, Map<String, FragmentDefinition> fragments,
                              @Nullable GraphQLSchema schema) {

    public ClientOperation {
        variables = List.copyOf(variables);
        fragments = Map.copyOf(fragments);
    }
}
