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
package io.micronaut.graphql.tools.processing;

import graphql.language.Directive;
import graphql.language.DirectivesContainer;
import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SDLDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.ScalarInfo;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

final class SchemaChecks {

    private final TypeDefinitionRegistry registry;
    private final ProcessorOptions options;
    private final Problems problems;
    private final Set<String> unreachableLines = new TreeSet<>();

    SchemaChecks(TypeDefinitionRegistry registry, ProcessorOptions options, Problems problems) {
        this.registry = registry;
        this.options = options;
        this.problems = problems;
    }

    boolean check() {
        checkAppliedDirectives();
        checkResolverDirectives();
        Optional<SchemaDefinition> schemaDefinition = registry.schemaDefinition();
        if (schemaDefinition.isEmpty()) {
            problems.error("the schema has no schema definition (schema { query: ... }) and no Query, Mutation or "
                    + "Subscription type; the library needs one to find the root operation types.");
            return false;
        }
        checkUnreachableTypes(schemaDefinition.get());
        return true;
    }

    Set<String> unreachableLines() {
        return unreachableLines;
    }

    private void checkResolverDirectives() {
        for (String directive : options.resolverDirectives()) {
            if (registry.getDirectiveDefinition(directive).isEmpty()) {
                problems.error(ProcessorOptions.RESOLVER_DIRECTIVES + " lists the directive " + directive
                        + ", which the schema does not declare.");
            }
        }
    }

    private void checkAppliedDirectives() {
        List<SDLDefinition<?>> definitions = new ArrayList<>();
        registry.schemaDefinition().ifPresent(definitions::add);
        definitions.addAll(registry.getSchemaExtensionDefinitions());
        definitions.addAll(sortedTypes());
        registry.objectTypeExtensions().values().forEach(definitions::addAll);
        registry.interfaceTypeExtensions().values().forEach(definitions::addAll);
        registry.unionTypeExtensions().values().forEach(definitions::addAll);
        registry.enumTypeExtensions().values().forEach(definitions::addAll);
        registry.inputObjectTypeExtensions().values().forEach(definitions::addAll);
        registry.scalarTypeExtensions().values().forEach(definitions::addAll);
        registry.scalars().values().stream()
                .filter(it -> !ScalarInfo.isGraphqlSpecifiedScalar(it.getName()))
                .forEach(definitions::add);
        registry.getDirectiveDefinitions().values().forEach(it ->
                it.getInputValueDefinitions().forEach(argument -> checkDirectives("@" + it.getName() + "("
                        + argument.getName() + ":)", argument)));

        for (SDLDefinition<?> definition : definitions) {
            String name = definition instanceof TypeDefinition<?> typeDefinition ? typeDefinition.getName() : "schema";
            if (definition instanceof DirectivesContainer<?> container) {
                checkDirectives(name, container);
            }
            children(definition).forEach(child -> checkDirectives(name + "." + child.getKey(), child.getValue()));
        }
    }

    private static List<Map.Entry<String, DirectivesContainer<?>>> children(SDLDefinition<?> definition) {
        List<Map.Entry<String, DirectivesContainer<?>>> result = new ArrayList<>();
        List<FieldDefinition> fields = definition instanceof ObjectTypeDefinition object ? object.getFieldDefinitions()
                : definition instanceof InterfaceTypeDefinition iface ? iface.getFieldDefinitions() : List.of();
        for (FieldDefinition field : fields) {
            result.add(Map.entry(field.getName(), field));
            for (InputValueDefinition argument : field.getInputValueDefinitions()) {
                result.add(Map.entry(field.getName() + "(" + argument.getName() + ":)", argument));
            }
        }
        if (definition instanceof InputObjectTypeDefinition input) {
            input.getInputValueDefinitions().forEach(it -> result.add(Map.entry(it.getName(), it)));
        }
        if (definition instanceof EnumTypeDefinition enumType) {
            enumType.getEnumValueDefinitions().forEach(it -> result.add(Map.entry(it.getName(), it)));
        }
        return result;
    }

    private void checkDirectives(String coordinate, DirectivesContainer<?> container) {
        for (Directive directive : container.getDirectives()) {
            if (!GraphQLSpecifiedDirectives.isSpecified(directive.getName())
                    && registry.getDirectiveDefinition(directive.getName()).isEmpty()) {
                problems.error(coordinate, "the directive @" + directive.getName()
                        + " is applied but not declared; declare it with directive @" + directive.getName()
                        + " on ... or remove it.", directive, null);
            }
        }
    }

    private void checkUnreachableTypes(SchemaDefinition schemaDefinition) {
        Set<String> reached = reachableTypes(schemaDefinition);

        Map<String, TypeDefinition<?>> unreachable = new TreeMap<>();
        Map<String, TypeDefinition<?>> unreachableValueTypes = new TreeMap<>();
        Set<String> directiveTypes = directiveArgumentTypes();
        for (TypeDefinition<?> type : registry.types().values()) {
            if (reached.contains(type.getName())) {
                continue;
            }
            if (type instanceof ObjectTypeDefinition || type instanceof InterfaceTypeDefinition
                    || type instanceof UnionTypeDefinition) {
                unreachable.put(type.getName(), type);
            } else if (!directiveTypes.contains(type.getName())) {
                unreachableValueTypes.put(type.getName(), type);
            }
        }
        registry.scalars().forEach((name, scalar) -> {
            if (!ScalarInfo.isGraphqlSpecifiedScalar(name) && !reached.contains(name) && !directiveTypes.contains(name)) {
                unreachableValueTypes.put(name, scalar);
            }
        });

        for (String allowed : options.allowUnreachable()) {
            boolean defined = registry.types().containsKey(allowed) || registry.scalars().containsKey(allowed);
            if (!defined) {
                problems.error(ProcessorOptions.ALLOW_UNREACHABLE + " lists " + allowed
                        + ", which the schema does not define.");
            } else if (!unreachable.containsKey(allowed) && !unreachableValueTypes.containsKey(allowed)) {
                problems.error(ProcessorOptions.ALLOW_UNREACHABLE + " lists " + allowed
                        + ", which is not an unreachable object, interface, union, enum, input or scalar type.");
            }
        }

        unreachableLines.addAll(unreachableValueTypes.keySet());
        unreachable.keySet().stream()
                .filter(it -> options.ignoreUnreachable() || options.allowUnreachable().contains(it))
                .forEach(unreachableLines::add);
        if (options.ignoreUnreachable()) {
            return;
        }
        Map<String, TypeDefinition<?>> checked = new TreeMap<>(unreachable);
        if (options.strict()) {
            checked.putAll(unreachableValueTypes);
        }
        checked.forEach((name, type) -> {
            if (!options.allowUnreachable().contains(name)) {
                problems.error(name, "the " + kind(type) + " is unreachable: "
                        + (unreachableValueTypes.containsKey(name)
                        ? "no field, argument or input field reachable from a root operation type uses it"
                        : "no field reachable from a root operation type returns it")
                        + ". Use it, remove it from the schema, or list it in " + ProcessorOptions.ALLOW_UNREACHABLE
                        + ".", type, null);
            }
        });
    }

    Set<String> reachableTypes(SchemaDefinition schemaDefinition) {
        Set<String> reached = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        for (OperationTypeDefinition operation : schemaDefinition.getOperationTypeDefinitions()) {
            pending.add(operation.getTypeName().getName());
        }
        while (!pending.isEmpty()) {
            String name = pending.poll();
            if (!reached.add(name)) {
                continue;
            }
            TypeDefinition<?> type = registry.getType(name).orElse(null);
            if (type instanceof ObjectTypeDefinition object) {
                fieldTypes(object.getFieldDefinitions()).forEach(pending::add);
                object.getImplements().forEach(it -> pending.add(typeName(it)));
            } else if (type instanceof InterfaceTypeDefinition iface) {
                fieldTypes(iface.getFieldDefinitions()).forEach(pending::add);
                registry.getImplementationsOf(iface).forEach(it -> pending.add(it.getName()));
            } else if (type instanceof UnionTypeDefinition union) {
                union.getMemberTypes().forEach(it -> pending.add(typeName(it)));
            } else if (type instanceof InputObjectTypeDefinition input) {
                input.getInputValueDefinitions().forEach(it -> pending.add(typeName(it.getType())));
            }
        }
        return reached;
    }

    private static Stream<String> fieldTypes(List<FieldDefinition> fields) {
        return fields.stream().flatMap(field -> Stream.concat(Stream.of(typeName(field.getType())),
                field.getInputValueDefinitions().stream().map(it -> typeName(it.getType()))));
    }

    private Set<String> directiveArgumentTypes() {
        Set<String> result = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        registry.getDirectiveDefinitions().values().stream()
                .filter(it -> !GraphQLSpecifiedDirectives.isSpecified(it.getName()))
                .forEach(it -> it.getInputValueDefinitions().forEach(argument -> pending.add(typeName(argument.getType()))));
        while (!pending.isEmpty()) {
            String name = pending.poll();
            if (result.add(name)) {
                registry.getType(name, InputObjectTypeDefinition.class).ifPresent(input ->
                        input.getInputValueDefinitions().forEach(field -> pending.add(typeName(field.getType()))));
            }
        }
        return result;
    }

    private List<TypeDefinition<?>> sortedTypes() {
        List<TypeDefinition<?>> result = new ArrayList<>();
        registry.types().values().forEach(result::add);
        result.sort(Comparator.comparing(TypeDefinition::getName));
        return result;
    }

    static String kind(TypeDefinition<?> type) {
        if (type instanceof ObjectTypeDefinition) {
            return "object type";
        } else if (type instanceof InterfaceTypeDefinition) {
            return "interface";
        } else if (type instanceof UnionTypeDefinition) {
            return "union";
        } else if (type instanceof EnumTypeDefinition) {
            return "enum";
        } else if (type instanceof InputObjectTypeDefinition) {
            return "input type";
        } else if (type instanceof ScalarTypeDefinition) {
            return "scalar";
        }
        return "type";
    }

    static String typeName(Type<?> type) {
        Type<?> result = type;
        while (!(result instanceof TypeName)) {
            result = result instanceof NonNullType nonNull ? nonNull.getType() : ((ListType) result).getType();
        }
        return ((TypeName) result).getName();
    }

}
