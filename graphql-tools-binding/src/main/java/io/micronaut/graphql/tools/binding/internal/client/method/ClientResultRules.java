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
package io.micronaut.graphql.tools.binding.internal.client.method;

import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.internal.client.ClientDocuments;
import io.micronaut.graphql.tools.binding.internal.client.ClientOperation;
import io.micronaut.graphql.tools.binding.internal.client.PayloadMode;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.model.JClass;
import io.micronaut.graphql.tools.binding.internal.model.TypeModel;
import io.micronaut.graphql.tools.binding.internal.model.TypeRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rules of the result type of an operation method against the selection of its operation. The payload is what the runtime
 * returns: the declared return type without the asynchronous wrapper and without {@code GraphQLResponse}. When the operation
 * selects one root field the payload is that field's value, and its type is compared with the type of the field and its
 * selection; otherwise it is the {@code data} object, and its properties are the root fields. The rules need the schema, so they
 * run with the checks on.
 */
@Internal
public final class ClientResultRules {

    private static final String RESPONSE = "io.micronaut.graphql.tools.client.GraphQLResponse";
    private static final Set<String> VOID = Set.of("void", "java.lang.Void", "kotlin.Unit");
    private static final Set<String> ASYNC = Set.of("java.util.concurrent.CompletableFuture", "java.util.concurrent.CompletionStage",
            "org.reactivestreams.Publisher", "reactor.core.publisher.Mono", "reactor.core.publisher.Flux", "kotlinx.coroutines.flow.Flow");
    private static final List<String> ASYNC_SUPERTYPES = List.of("org.reactivestreams.Publisher", "java.util.concurrent.CompletionStage");

    private ClientResultRules() {
    }

    /**
     * @param method    the operation method
     * @param operation the operation that the method runs
     * @param model     the types of the result
     * @param scalars   the custom scalars that the client has mapped so far
     * @return the findings, in the order of the selection
     */
    public static List<Finding> check(ClientMethod method, ClientOperation operation, TypeModel model, ClientScalars scalars) {
        GraphQLSchema schema = operation.schema();
        TypeRef payload = schema == null ? null : payload(method.returnType(), model);
        if (payload == null) {
            return List.of();
        }
        GraphQLObjectType root = switch (operation.definition().getOperation()) {
            case QUERY -> schema.getQueryType();
            case MUTATION -> schema.getMutationType();
            case SUBSCRIPTION -> schema.getSubscriptionType();
        };
        if (root == null) {
            return List.of();
        }
        ClientSelections selections = new ClientSelections(schema, operation.fragments());
        Map<String, List<ClientSelections.SelectedField>> fields = selections.collect(
                List.of(operation.definition().getSelectionSet()), root).common;
        List<Finding> findings = new ArrayList<>();
        ResultTypes types = new ResultTypes(model, scalars, selections, method.member(), findings);
        if (operation.payload() == PayloadMode.FIELD && fields.size() == 1) {
            Map.Entry<String, List<ClientSelections.SelectedField>> only = fields.entrySet().iterator().next();
            types.matchField(payload, only.getKey(), only.getValue());
        } else if (operation.payload() == PayloadMode.DATA) {
            types.matchObject(payload, root, fields, "", ClientDocuments.location(operation.definition().getSourceLocation()));
        }
        return findings;
    }

    // the type that the runtime decodes: null for no payload, a method that returns nothing or a type that is not known
    private static @Nullable TypeRef payload(TypeRef returned, TypeModel model) {
        TypeRef type = returned;
        if (isAsync(type, model)) {
            type = type.typeArguments().isEmpty() ? null : type.typeArguments().get(0);
        }
        if (type != null && !type.array() && type.rawName().equals(RESPONSE)) {
            type = type.typeArguments().isEmpty() ? null : type.typeArguments().get(0);
        }
        return type == null || VOID.contains(type.rawName()) ? null : type;
    }

    private static boolean isAsync(TypeRef type, TypeModel model) {
        if (type.array() || type.primitive()) {
            return false;
        }
        if (ASYNC.contains(type.rawName())) {
            return true;
        }
        try {
            JClass declared = model.resolve(type);
            return ASYNC_SUPERTYPES.stream().anyMatch(declared::isAssignableTo);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
