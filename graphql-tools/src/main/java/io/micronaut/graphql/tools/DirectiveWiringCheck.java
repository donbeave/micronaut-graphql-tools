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
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.GraphQLToolsConfiguration.UnwiredDirectives;
import io.micronaut.graphql.tools.binding.BindingCode;
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
import java.util.regex.Pattern;

@Internal
final class DirectiveWiringCheck {

    private static final Logger LOG = LoggerFactory.getLogger(DirectiveWiringCheck.class);

    private DirectiveWiringCheck() {
    }

    static void check(TypeDefinitionRegistry registry,
                      RuntimeWiring runtimeWiring,
                      Collection<String> everyElementDirectiveNames,
                      Collection<String> schemaDirectiveNames,
                      GraphQLToolsConfiguration configuration,
                      BindingManifestRecorder manifestRecorder) {
        Set<String> wired = new TreeSet<>(runtimeWiring.getRegisteredDirectiveWiring().keySet());
        wired.addAll(everyElementDirectiveNames);
        Set<String> resolverDirectives = new TreeSet<>(configuration.getResolverDirectives());
        wired.addAll(resolverDirectives);
        Set<String> inert = new TreeSet<>();
        List<Pattern> inertPatterns = new ArrayList<>();
        List<String> inertPatternSources = new ArrayList<>();
        for (String entry : configuration.getInertDirectives()) {
            if (entry.indexOf('*') >= 0) {
                inertPatternSources.add(entry);
                inertPatterns.add(pattern(entry));
            } else {
                inert.add(entry);
            }
        }
        Set<String> schemaCustomizer = new TreeSet<>(configuration.getSchemaCustomizerDirectives());

        Map<String, String> invalidInert = new TreeMap<>();
        for (String name : inert) {
            if (isBuiltIn(name)) {
                invalidInert.put(name, "built into graphql-java");
            } else if (registry.getDirectiveDefinition(name).isEmpty()) {
                invalidInert.put(name, "not declared in the schema");
            } else if (resolverDirectives.contains(name)) {
                invalidInert.put(name, "listed in " + GraphQLToolsConfiguration.PREFIX + ".resolver-directives");
            } else if (wired.contains(name)) {
                invalidInert.put(name, "has a wiring");
            }
        }
        for (int i = 0; i < inertPatterns.size(); i++) {
            Pattern pattern = inertPatterns.get(i);
            boolean matches = registry.getDirectiveDefinitions().keySet().stream()
                    .anyMatch(it -> !isBuiltIn(it) && !wired.contains(it) && pattern.matcher(it).matches());
            if (!matches) {
                invalidInert.put(inertPatternSources.get(i), "matches no declared directive that lacks a wiring");
            }
        }
        Map<String, String> invalidSchemaCustomizer = new TreeMap<>();
        for (String name : schemaCustomizer) {
            if (isBuiltIn(name)) {
                invalidSchemaCustomizer.put(name, "built into graphql-java");
            } else if (registry.getDirectiveDefinition(name).isEmpty()) {
                invalidSchemaCustomizer.put(name, "not declared in the schema");
            }
        }

        UnwiredDirectives mode = configuration.getUnwiredDirectives();
        if (mode == null) {
            mode = configuration.isStrict() ? UnwiredDirectives.FAIL : UnwiredDirectives.WARN;
        }
        Map<String, List<String>> unwired = new TreeMap<>();
        if (mode != UnwiredDirectives.IGNORE) {
            appliedDirectives(registry).forEach((name, coordinates) -> {
                boolean declared = registry.getDirectiveDefinition(name).isPresent();
                if (!declared || isBuiltIn(name) || inert.contains(name)
                        || inertPatterns.stream().anyMatch(it -> it.matcher(name).matches())) {
                    return;
                }
                boolean customizer = schemaCustomizer.contains(name);
                boolean isWired = wired.contains(name);
                List<String> missing = coordinates.stream()
                        .filter(it -> isGraphQLJavaWiringless(it) ? !customizer : !isWired)
                        .toList();
                if (!missing.isEmpty()) {
                    unwired.put(name, missing);
                }
            });
        }

        unwired.keySet().forEach(name -> manifestRecorder.diagnostic(BindingCode.UNWIRED_DIRECTIVE, name));
        boolean fail = mode == UnwiredDirectives.FAIL;
        if (mode == UnwiredDirectives.WARN && !unwired.isEmpty()) {
            String reason = configuration.getUnwiredDirectives() == null
                    ? GraphQLToolsConfiguration.PREFIX + ".strict is false"
                    : GraphQLToolsConfiguration.PREFIX + ".unwired-directives is warn";
            LOG.warn("{}, so the startup goes on: {}", reason, UnwiredDirectivesException.describeUnwired(unwired));
        }
        if ((fail && !unwired.isEmpty()) || !invalidInert.isEmpty() || !invalidSchemaCustomizer.isEmpty()) {
            throw new UnwiredDirectivesException(fail ? unwired : Map.of(), invalidInert, invalidSchemaCustomizer);
        }

        if (!configuration.getInertDirectives().isEmpty()) {
            LOG.info("Directives without a wiring, declared inert by {}: @{}",
                    GraphQLToolsConfiguration.PREFIX + ".inert-directives",
                    String.join(", @", new TreeSet<>(configuration.getInertDirectives())));
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

    // graphql-java calls no SchemaDirectiveWiring for the schema definition or for the arguments of a directive definition
    static boolean isGraphQLJavaWiringless(String coordinate) {
        return coordinate.equals("schema") || coordinate.startsWith("@");
    }

    private static Pattern pattern(String glob) {
        StringBuilder regex = new StringBuilder();
        for (String part : glob.split("\\*", -1)) {
            if (regex.length() > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
    }

    // graphql-java implements these itself; @include and @skip cannot be applied in a schema, but are built in as well
    private static boolean isBuiltIn(String name) {
        return GraphQLSpecifiedDirectives.isBuiltIn(name);
    }

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
