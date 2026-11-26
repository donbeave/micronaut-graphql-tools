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
package io.micronaut.graphql.tools.internal.wiring;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;
import io.micronaut.graphql.tools.internal.checks.StrictModeChecks;
import io.micronaut.graphql.tools.internal.exceptions.BindingFailuresException;
import io.micronaut.graphql.tools.internal.exceptions.checks.StrictModeException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Runs the strict mode checks over the finished binding and throws the findings. */
@Internal
final class StrictModeFindings {

    private final WiringState s;

    StrictModeFindings(WiringState s) {
        this.s = s;
    }

    Optional<SchemaBindingException> checkStrictMode(Set<String> unboundTypes) {
        StrictModeChecks checks = new StrictModeChecks(s.manifestRecorder, s.checkPolicy);
        boolean recording = s.manifestRecorder.isRecording();
        Set<String> allowedTypes = new HashSet<>(s.configuration.getAllow().getUnreachable());
        if (!s.checkPolicy.failing(BindingCode.UNREACHABLE_TYPE)) {
            allowedTypes.addAll(unboundTypes);
        }

        Map<String, Class<?>> processed = WiringState.snapshot(s.processedTypes);
        if (checks.enabled(BindingCode.UNBOUND_RESOLVER_METHOD)) {
            Set<String> accountedMethods = new HashSet<>(s.resolverBindings.keySet());
            accountedMethods.addAll(s.ambiguities.shadowedResolverMethods());
            checks.unboundResolverMethods(s.graphQLResolversRegistry.getResolverMethods(), accountedMethods);
        }
        if (recording) {
            checks.ignoredResolverMethods(s.graphQLResolversRegistry.getIgnoredMethods());
        }
        if (checks.enabled(BindingCode.ROOT_METHOD_SEVERAL_OPERATIONS)) {
            checks.rootMethodsOfSeveralOperationTypes(s.rootMethodBindings);
        }
        if (checks.enabled(BindingCode.UNBOUND_ENUM_CONSTANT) || recording) {
            checks.unboundEnumConstants(processed, s.typeDefinitionRegistry, s.typeModel);
        }
        if (checks.enabled(BindingCode.UNUSED_DICTIONARY_ENTRY)) {
            Set<String> usedTypes = new HashSet<>(processed.keySet());
            usedTypes.addAll(s.boundUnreturnedInterfaces);
            checks.unusedDictionaryEntries(s.schemaMappingDictionary.getTypes(), usedTypes, allowedTypes);
        }
        if (checks.enabled(BindingCode.INTERFACE_READ_THROUGH_IMPLEMENTATION)) {
            checks.interfacesReadThroughImplementations(s.interfacesReadThroughImplementations);
        }
        checks.logWarnings();

        return !checks.getProblems().isEmpty()
                ? Optional.of(new StrictModeException(checks.getCodes(), checks.getProblems()))
                : Optional.empty();
    }

    static void throwFindings(List<SchemaBindingException> findings) {
        if (findings.size() == 1) {
            throw findings.get(0);
        }
        if (!findings.isEmpty()) {
            throw new BindingFailuresException(findings);
        }
    }
}
