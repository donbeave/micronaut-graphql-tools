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
package io.micronaut.graphql.tools.internal.checks;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.GraphQLToolsConfiguration.Precedence;
import io.micronaut.graphql.tools.TypeMappingContext;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.internal.manifest.BindingManifestRecorder;
import io.micronaut.graphql.tools.internal.model.BeanDefinitionAndMethod;
import io.micronaut.graphql.tools.internal.model.GraphQLResolversRegistry;
import io.micronaut.graphql.tools.internal.model.ModelMember;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.graphql.tools.internal.MicronautUtils.toMap;

/**
 * Collects the fields that both a member of the Java type and a type resolver method can bind. Each phase that binds
 * fields has a log of its own; the results are merged when the phases are done.
 */
@Internal
public final class AmbiguityLog {

    private final Precedence precedence;
    private final BindingManifestRecorder manifestRecorder;
    private final Map<String, String> ambiguousBindings = new TreeMap<>();
    private final Set<String> shadowedResolverMethods = new HashSet<>();

  public   AmbiguityLog(Precedence precedence, BindingManifestRecorder manifestRecorder) {
        this.precedence = precedence;
        this.manifestRecorder = manifestRecorder;
    }

    public void record(TypeMappingContext mappingContext, ModelMember modelMember,
                List<BeanDefinitionAndMethod> resolvers) {
        String coordinate = mappingContext.getGraphQlObjectType() + "." + mappingContext.getGraphQlField();
        ambiguousBindings.put(coordinate, describeMembers(modelMember.members()) + " | "
                + describeMembers(toMap(resolvers)));
        if (precedence == Precedence.PROPERTY_FIRST) {
            resolvers.forEach(it -> shadowedResolverMethods.add(GraphQLResolversRegistry.describeMethod(
                    it.getBeanDefinition(), it.getExecutableMethod())));
        }
        if (manifestRecorder.isRecording()) {
            manifestRecorder.diagnostic(BindingCode.PRECEDENCE_AMBIGUITY, coordinate, Stream.concat(
                            Stream.of(modelMember.manifestMember()),
                            resolvers.stream().map(it -> BindingManifestRecorder.methodMember(it.getExecutableMethod(),
                                    it.getExecutableMethod().getDeclaringType())).sorted())
                    .collect(Collectors.joining(" | ")));
        }
    }

    public Ambiguities snapshot() {
        return new Ambiguities(ambiguousBindings, shadowedResolverMethods);
    }

    static String describeMembers(Map<Class<?>, List<String>> members) {
        return members.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().sorted().map(it -> entry.getKey().getName() + " " + it))
                .collect(Collectors.joining(", "));
    }

    public static Map<Class<?>, List<String>> merge(Map<Class<?>, List<String>> first,
                                             Map<Class<?>, List<String>> second) {
        Map<Class<?>, List<String>> result = new TreeMap<>(Comparator.comparing(Class::getName));
        first.forEach((clazz, names) -> result.computeIfAbsent(clazz, it -> new ArrayList<>()).addAll(names));
        second.forEach((clazz, names) -> result.computeIfAbsent(clazz, it -> new ArrayList<>()).addAll(names));
        return result;
    }

}
