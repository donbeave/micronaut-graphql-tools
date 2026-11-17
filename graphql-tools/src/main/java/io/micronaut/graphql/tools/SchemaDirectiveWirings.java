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
package io.micronaut.graphql.tools;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.exceptions.DuplicateDirectiveWiringException;
import io.micronaut.graphql.tools.exceptions.UnwrappedDirectiveWiringException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Registers the {@link SchemaDirective} beans with the runtime wiring, and finds the {@link SchemaDirectiveWiring}
 * beans that nothing wires. It runs before the application's {@link RuntimeWiringCustomizer} beans,
 * so these cannot replace a {@code SchemaDirective} bean's wiring unnoticed.
 */
@Internal
final class SchemaDirectiveWirings implements RuntimeWiringCustomizer {

    private final List<SchemaDirective> namedDirectives = new ArrayList<>();
    private final List<SchemaDirectiveWiring> everyElementWirings;
    private final List<BeanRegistration<SchemaDirectiveWiring>> wirings;
    private final List<SchemaDirectiveWiring> wrappedWirings;
    private final Set<String> everyElementDirectiveNames = new LinkedHashSet<>();
    private final Set<String> directiveNames = new LinkedHashSet<>();

    /**
     * @param wirings    the {@link SchemaDirectiveWiring} beans
     * @param directives the {@link SchemaDirective} beans
     */
    SchemaDirectiveWirings(Collection<BeanRegistration<SchemaDirectiveWiring>> wirings,
                           Collection<BeanRegistration<SchemaDirective>> directives) {
        List<BeanRegistration<SchemaDirective>> everyElement = new ArrayList<>();
        for (BeanRegistration<SchemaDirective> registration : directives) {
            SchemaDirective directive = registration.getBean();
            directiveNames.addAll(directive.getDirectiveNames());
            if (directive.isEveryElement()) {
                everyElement.add(registration);
                everyElementDirectiveNames.addAll(directive.getDirectiveNames());
            } else {
                namedDirectives.add(directive);
            }
        }
        // lowest order first; stable for equal orders
        everyElement.sort(Comparator.comparingInt(BeanRegistration::getOrder));
        this.everyElementWirings = everyElement.stream().map(it -> it.getBean().getWiring()).toList();

        this.wirings = List.copyOf(wirings);
        this.wrappedWirings = namedAndEveryElement(directives);
    }

    private static List<SchemaDirectiveWiring> namedAndEveryElement(Collection<BeanRegistration<SchemaDirective>> directives) {
        return directives.stream().map(it -> it.getBean().getWiring()).toList();
    }

    /**
     * Fails on the {@link SchemaDirectiveWiring} beans that neither a {@link SchemaDirective} bean nor a
     * {@link RuntimeWiringCustomizer} registered. It runs after the customizers, which may register a bean's wiring with
     * {@code RuntimeWiring.Builder#directive}. A singleton is matched by instance. A prototype bean is a new instance for
     * every injection point, so it is matched by its class. Limitation: an unwrapped prototype bean is therefore missed when
     * another wired instance has the same class.
     *
     * @param runtimeWiring the runtime wiring after every customizer
     * @throws UnwrappedDirectiveWiringException if a bean is wired by nothing
     */
    void checkUnwrapped(RuntimeWiring runtimeWiring) {
        List<SchemaDirectiveWiring> registered = new ArrayList<>(wrappedWirings);
        registered.addAll(runtimeWiring.getRegisteredDirectiveWiring().values());
        registered.addAll(runtimeWiring.getDirectiveWiring());
        Set<SchemaDirectiveWiring> byInstance = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Class<?>> byClass = new HashSet<>();
        for (SchemaDirectiveWiring wiring : registered) {
            byInstance.add(wiring);
            byClass.add(wiring.getClass());
        }

        List<String> unwrappedWirings = new ArrayList<>();
        for (BeanRegistration<SchemaDirectiveWiring> registration : wirings) {
            SchemaDirectiveWiring bean = registration.getBean();
            boolean wired = registration.getBeanDefinition().isSingleton()
                    ? byInstance.contains(bean)
                    : byClass.contains(bean.getClass());
            if (!wired) {
                unwrappedWirings.add(bean.getClass().getName() + " (" + registration.getBeanDefinition() + ")");
            }
        }
        if (!unwrappedWirings.isEmpty()) {
            Collections.sort(unwrappedWirings);
            throw new UnwrappedDirectiveWiringException(unwrappedWirings);
        }
    }

    /**
     * @return the directives that {@link SchemaDirective#everyElement(Collection, SchemaDirectiveWiring)} beans
     * implement; the named ones are in the runtime wiring
     */
    Set<String> getEveryElementDirectiveNames() {
        return everyElementDirectiveNames;
    }

    /**
     * @return the directives that {@link SchemaDirective} beans of either kind implement
     */
    Set<String> getDirectiveNames() {
        return directiveNames;
    }

    @Override
    public void customize(RuntimeWiring.Builder builder) {
        Map<String, List<SchemaDirective>> byName = new LinkedHashMap<>();
        for (SchemaDirective directive : namedDirectives) {
            byName.computeIfAbsent(directive.getName(), it -> new ArrayList<>()).add(directive);
        }
        Map<String, List<String>> duplicates = new TreeMap<>();
        byName.forEach((name, list) -> {
            if (list.size() > 1) {
                duplicates.put(name, list.stream().map(it -> it.getWiring().getClass().getName()).toList());
            }
        });
        if (!duplicates.isEmpty()) {
            throw new DuplicateDirectiveWiringException(duplicates);
        }

        byName.forEach((name, list) -> builder.directive(name, list.get(0).getWiring()));
        everyElementWirings.forEach(builder::directiveWiring);
    }

}
