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

import graphql.Directives;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.DirectivesContainer;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.schema.idl.DirectiveInfo;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.exceptions.UnwiredDirectivesException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Checks that every directive the schema applies has a wiring or is declared inert
 * ({@link GraphQLToolsConfiguration#getInertDirectives()}). graphql-java's built-in directives are exempt: graphql-java
 * implements them itself.
 */
@Internal
final class DirectiveWiringCheck {

    private static final Logger LOG = LoggerFactory.getLogger(DirectiveWiringCheck.class);

    // graphql-java implements these itself; @include and @skip cannot be applied in a schema, but are built in as well
    private static final Set<String> BUILT_IN = Set.of(
            Directives.IncludeDirective.getName(),
            Directives.SkipDirective.getName(),
            Directives.DeprecatedDirective.getName(),
            Directives.SpecifiedByDirective.getName(),
            Directives.OneOfDirective.getName()
    );

    private DirectiveWiringCheck() {
    }

    /**
     * @param registry                   the registry the schema is generated from
     * @param runtimeWiring              the runtime wiring; its named directive wirings count as wired
     * @param everyElementDirectiveNames the directives that wirings for every element implement
     * @param schemaDirectiveNames       the directives that {@link SchemaDirective} beans implement
     * @param configuration              the options
     * @throws UnwiredDirectivesException if a directive has no wiring or the inert list is invalid
     */
    static void check(TypeDefinitionRegistry registry,
                      RuntimeWiring runtimeWiring,
                      Collection<String> everyElementDirectiveNames,
                      Collection<String> schemaDirectiveNames,
                      GraphQLToolsConfiguration configuration) {
        Set<String> wired = new TreeSet<>(runtimeWiring.getRegisteredDirectiveWiring().keySet());
        wired.addAll(everyElementDirectiveNames);
        Set<String> inert = new TreeSet<>(configuration.getInertDirectives());

        Map<String, String> invalidInert = new TreeMap<>();
        for (String name : inert) {
            if (isBuiltIn(name)) {
                invalidInert.put(name, "built into graphql-java");
            } else if (registry.getDirectiveDefinition(name).isEmpty()) {
                invalidInert.put(name, "not declared in the schema");
            } else if (wired.contains(name)) {
                invalidInert.put(name, "has a wiring");
            }
        }

        Map<String, List<String>> unwired = new TreeMap<>();
        appliedDirectives(registry).forEach((name, coordinates) -> {
            boolean declared = registry.getDirectiveDefinition(name).isPresent();
            // an undeclared directive is graphql-java's error to report
            if (declared && !isBuiltIn(name) && !wired.contains(name) && !inert.contains(name)) {
                unwired.put(name, List.copyOf(coordinates));
            }
        });

        if (!unwired.isEmpty() || !invalidInert.isEmpty()) {
            throw new UnwiredDirectivesException(unwired, invalidInert);
        }

        if (!inert.isEmpty()) {
            LOG.info("Directives without a wiring, declared inert by {}: @{}",
                    GraphQLToolsConfiguration.PREFIX + ".inert-directives", String.join(", @", inert));
        }
        Set<String> notDeclared = new TreeSet<>();
        for (String name : schemaDirectiveNames) {
            if (registry.getDirectiveDefinition(name).isEmpty()) {
                notDeclared.add(name);
            }
        }
        if (!notDeclared.isEmpty()) {
            LOG.info("SchemaDirective beans wire directives that the schema does not declare; they are not used: @{}",
                    String.join(", @", notDeclared));
        }
    }

    private static boolean isBuiltIn(String name) {
        return BUILT_IN.contains(name) || DirectiveInfo.isGraphqlSpecifiedDirective(name);
    }

    /**
     * @param registry the registry
     * @return the name of every directive the schema applies, to the schema coordinates that apply it, sorted
     */
    static Map<String, Set<String>> appliedDirectives(TypeDefinitionRegistry registry) {
        Map<String, Set<String>> applied = new TreeMap<>();

        registry.schemaDefinition().ifPresent(it -> collect(applied, it, "schema"));
        registry.getSchemaExtensionDefinitions().forEach(it -> collect(applied, it, "schema"));

        List<TypeDefinition<?>> types = new ArrayList<>();
        for (TypeDefinition<?> type : registry.types().values()) {
            types.add(type);
        }
        types.addAll(registry.scalars().values());
        registry.objectTypeExtensions().values().forEach(types::addAll);
        registry.interfaceTypeExtensions().values().forEach(types::addAll);
        registry.unionTypeExtensions().values().forEach(types::addAll);
        registry.enumTypeExtensions().values().forEach(types::addAll);
        registry.scalarTypeExtensions().values().forEach(types::addAll);
        registry.inputObjectTypeExtensions().values().forEach(types::addAll);

        for (TypeDefinition<?> type : types) {
            String name = type.getName();
            collect(applied, type, name);
            if (type instanceof ObjectTypeDefinition objectType) {
                collectFields(applied, name, objectType.getFieldDefinitions());
            } else if (type instanceof InterfaceTypeDefinition interfaceType) {
                collectFields(applied, name, interfaceType.getFieldDefinitions());
            } else if (type instanceof InputObjectTypeDefinition inputType) {
                for (InputValueDefinition field : inputType.getInputValueDefinitions()) {
                    collect(applied, field, name + "." + field.getName());
                }
            } else if (type instanceof EnumTypeDefinition enumType) {
                for (EnumValueDefinition value : enumType.getEnumValueDefinitions()) {
                    collect(applied, value, name + "." + value.getName());
                }
            }
        }

        for (DirectiveDefinition definition : registry.getDirectiveDefinitions().values()) {
            for (InputValueDefinition argument : definition.getInputValueDefinitions()) {
                collect(applied, argument, "@" + definition.getName() + "(" + argument.getName() + ":)");
            }
        }
        return applied;
    }

    private static void collectFields(Map<String, Set<String>> applied, String typeName, List<FieldDefinition> fields) {
        for (FieldDefinition field : fields) {
            String coordinate = typeName + "." + field.getName();
            collect(applied, field, coordinate);
            for (InputValueDefinition argument : field.getInputValueDefinitions()) {
                collect(applied, argument, coordinate + "(" + argument.getName() + ":)");
            }
        }
    }

    private static void collect(Map<String, Set<String>> applied, DirectivesContainer<?> container, String coordinate) {
        for (Directive directive : container.getDirectives()) {
            applied.computeIfAbsent(directive.getName(), it -> new TreeSet<>()).add(coordinate);
        }
    }

}
