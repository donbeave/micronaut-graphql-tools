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

import graphql.language.AstPrinter;
import graphql.language.Definition;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.idl.DirectiveInfo;
import graphql.schema.idl.ScalarInfo;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.aop.Intercepted;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.micronaut.graphql.tools.GraphQLUtils.printType;

/**
 * Records, while the schema is bound, what each manifest line is made of, and renders the lines only when the
 * {@link BindingManifest} is read. Binding records cheap facts (a class, an {@link Argument}, a definition); the text
 * is built from them on the first read, never by reflection, so a start that does not read the manifest does not
 * render it.
 */
@Internal
final class BindingManifestRecorder {

    static final String KIND_ROOT = "root";
    static final String KIND_RESOLVER = "resolver";
    static final String KIND_PROPERTY = "property";
    static final String KIND_CLAIMED = "claimed";

    private static final String ARROW = " → ";
    private static final String DOT = " · ";
    private static final String NONE = "-";

    // key: what the line describes (a type, a coordinate), so that a later binding of the same coordinate replaces it
    private final Map<String, Supplier<String>> lines = new HashMap<>();
    private final Set<String> diagnostics = new TreeSet<>();
    private final Set<String> scalars = new TreeSet<>();
    private final boolean recording;

    /**
     * @param recording whether the facts the manifest is made of are recorded; when {@code false} only the reached
     *                  scalars are kept, which the unreachable-type check needs, and {@link #build} returns nothing
     */
    BindingManifestRecorder(boolean recording) {
        this.recording = recording;
    }

    /**
     * @return whether the manifest facts are recorded; call sites that would allocate to describe a fact ask first
     */
    boolean isRecording() {
        return recording;
    }

    void type(String typeName, String kind, @Nullable Class<?> javaType) {
        if (!recording) {
            return;
        }
        lines.put(typeName, () -> line(typeName, kind, javaType == null ? NONE : javaType.getTypeName(), NONE, NONE));
    }

    void scalar(String typeName) {
        scalars.add(typeName);
    }

    /**
     * @param objectTypeDefinition the object type of the field
     * @param fieldDefinition      the field
     * @param kind                 {@code root}, {@code resolver} or {@code property}
     * @param member               the member that binds the field, rendered when the manifest is read
     * @param declaredType         the declared type of the member
     * @param subscription         {@code true} for a field of the subscription type
     */
    void field(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition, String kind,
               Supplier<String> member, Argument<?> declaredType, boolean subscription) {
        if (!recording) {
            return;
        }
        lines.put(objectTypeDefinition.getName() + "." + fieldDefinition.getName(),
                () -> line(coordinate(objectTypeDefinition, fieldDefinition), kind, member.get(),
                        JavaTypeNames.of(declaredType), ReturnTypes.convertedTypeName(declaredType, subscription)));
    }

    /**
     * Records a field that a {@link RuntimeWiringCustomizer} claims: the library does not bind it, so it has no Java
     * member.
     *
     * @param objectTypeDefinition the object type of the field
     * @param fieldDefinition      the field
     * @param customizer           the class name of the customizer that claims it
     */
    void claimed(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition, String customizer) {
        if (!recording) {
            return;
        }
        lines.put(objectTypeDefinition.getName() + "." + fieldDefinition.getName(),
                () -> line(coordinate(objectTypeDefinition, fieldDefinition), KIND_CLAIMED, customizer, NONE, NONE));
    }

    private static String coordinate(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition) {
        StringBuilder coordinate = new StringBuilder(objectTypeDefinition.getName()).append('.')
                .append(fieldDefinition.getName());
        if (!fieldDefinition.getInputValueDefinitions().isEmpty()) {
            coordinate.append(fieldDefinition.getInputValueDefinitions().stream()
                    .map(it -> it.getName() + ": " + printType(it.getType()))
                    .collect(Collectors.joining(", ", "(", ")")));
        }
        return coordinate.append(": ").append(printType(fieldDefinition.getType())).toString();
    }

    void resolves(String abstractTypeName, String objectTypeName, Class<?> javaType) {
        if (!recording) {
            return;
        }
        lines.put(abstractTypeName + "/" + objectTypeName,
                () -> line(abstractTypeName + "/" + objectTypeName, "resolves", javaType.getTypeName(), NONE, NONE));
    }

    void enumValue(String enumTypeName, String value, Class<?> enumClass) {
        if (!recording) {
            return;
        }
        lines.put(enumTypeName + "." + value,
                () -> line(enumTypeName + "." + value, "enumValue", enumClass.getTypeName() + "#" + value, NONE,
                        NONE));
    }

    void inputField(String inputTypeName, String fieldName, graphql.language.Type<?> graphQlType, Class<?> inputClass,
                    BeanProperty<?, ?> property) {
        if (!recording) {
            return;
        }
        lines.put(inputTypeName + "." + fieldName, () -> line(inputTypeName + "." + fieldName + ": "
                        + printType(graphQlType), "inputField", inputClass.getTypeName() + "#" + property.getName(),
                JavaTypeNames.of(property.asArgument()), ReturnTypes.convertedTypeName(property.asArgument(), false)));
    }

    /**
     * @param kind  the kind of finding, for example {@code unreachable}
     * @param parts what it is about, separated by {@code " · "} in the line
     */
    void diagnostic(String kind, String... parts) {
        if (!recording) {
            return;
        }
        diagnostics.add("!" + kind + " " + Arrays.stream(parts)
                .map(BindingManifestRecorder::requirePart)
                .collect(Collectors.joining(DOT)));
    }

    /**
     * @param typeName a scalar
     * @return {@code true} if a bound field, argument or input field uses it
     */
    boolean isScalarReached(String typeName) {
        return scalars.contains(typeName);
    }

    /**
     * @return the diagnostic lines recorded so far
     */
    Set<String> getDiagnostics() {
        return diagnostics;
    }

    /**
     * @param registry the schema definitions
     * @param schema   the schema built from them
     * @return {@code null} when nothing is recorded, otherwise the manifest, which renders its header, and the
     * recorded lines plus the scalar lines, sorted, when it is first read
     */
    @Nullable
    BindingManifest build(TypeDefinitionRegistry registry, GraphQLSchema schema) {
        if (!recording) {
            return null;
        }
        Map<String, Supplier<String>> recorded = new HashMap<>(lines);
        Set<String> recordedDiagnostics = new TreeSet<>(diagnostics);
        Set<String> recordedScalars = new TreeSet<>(scalars);
        return new BindingManifest(() -> {
            List<String> body = new ArrayList<>(recorded.size() + recordedDiagnostics.size() + recordedScalars.size());
            recorded.values().forEach(it -> body.add(it.get()));
            body.addAll(recordedDiagnostics);
            for (String scalar : recordedScalars) {
                GraphQLType type = schema.getType(scalar);
                Class<?> coercing = type instanceof GraphQLScalarType scalarType
                        ? scalarType.getCoercing().getClass() : null;
                body.add(line(scalar, "scalar", coercing == null ? NONE : coercing.getTypeName(), NONE, NONE));
            }
            body.sort(Comparator.naturalOrder());

            List<String> header = List.of(
                    BindingManifest.FORMAT,
                    "# binder: micronaut-graphql-tools " + version(BindingManifestRecorder.class.getPackage()
                            .getImplementationVersion()),
                    "# graphql-java: " + GraphQLJavaVersion.get(),
                    "# schema-sha256: " + schemaSha256(registry)
            );
            return new BindingManifest.Content(header, body);
        });
    }

    /**
     * @param executable a method
     * @param ownerType  the class the method is declared on, possibly an AOP proxy
     * @return {@code Class#method(parameter types)}, the types as Micronaut's compile-time metadata describes them
     */
    static String methodMember(Executable<?, ?> executable, Class<?> ownerType) {
        return userClass(ownerType).getTypeName() + "#" + MicronautUtils.getMethodName(executable)
                + JavaTypeNames.parameters(executable);
    }

    /**
     * @param executable a method of a bean introspection ({@code @GraphQLField})
     * @param beanType   the introspected class
     * @return {@code Class#method(parameter types)}, the class being the one the method is written in when the
     * compile-time accessors say so, otherwise the introspected class
     */
    static String introspectedMethodMember(Executable<?, ?> executable, Class<?> beanType) {
        String member = CompileTimeAccessors.get().get(beanType.getTypeName() + "#"
                + MicronautUtils.getMethodName(executable) + "/" + executable.getArguments().length);
        return member != null ? member : methodMember(executable, beanType);
    }

    /**
     * @param beanProperty a bean property
     * @return {@code Class#getter()}, the class being the one that declares the getter, as the compile-time accessors
     * say; {@code Class#property} when they do not know the property
     */
    static String propertyMember(BeanProperty<?, ?> beanProperty) {
        String beanType = beanProperty.getDeclaringBean().getBeanType().getTypeName();
        String member = CompileTimeAccessors.get().get(beanType + "#" + beanProperty.getName());
        return member != null ? member : beanType + "#" + beanProperty.getName();
    }

    private static Class<?> userClass(Class<?> type) {
        if (Intercepted.class.isAssignableFrom(type) && type.getSuperclass() != null) {
            return type.getSuperclass();
        }
        return type;
    }

    private static String line(String subject, String kind, String member, String declaredType, String convertedType) {
        return requirePart(subject) + ARROW + kind + DOT + requirePart(member) + DOT + requirePart(declaredType) + DOT
                + requirePart(convertedType);
    }

    /**
     * The manifest is not escaped: a part that contains a separator or a line break cannot be written.
     */
    private static String requirePart(String part) {
        if (part.contains(ARROW) || part.contains(DOT) || part.contains("\n") || part.contains("\r")) {
            throw new IllegalStateException("Cannot write the binding manifest: \"" + part
                    + "\" contains a separator (\"" + ARROW.strip() + "\" or \"" + DOT.strip() + "\") or a line break.");
        }
        return part;
    }

    private static String version(@Nullable String version) {
        return version == null || version.isBlank() ? "unknown" : version;
    }

    /**
     * @return the SHA-256 of the definitions of the registry (schema definition first, then every definition by name,
     * without the scalars and directives graphql-java defines), each printed by {@link AstPrinter}, joined by
     * {@code LF}
     */
    private static String schemaSha256(TypeDefinitionRegistry registry) {
        List<Definition<?>> definitions = new ArrayList<>();
        registry.schemaDefinition().ifPresent(definitions::add);
        definitions.addAll(registry.getSchemaExtensionDefinitions());

        Map<String, List<Definition<?>>> named = new TreeMap<>();
        registry.types().values().forEach(it -> add(named, it.getName(), it));
        registry.scalars().values().stream()
                .filter(it -> !ScalarInfo.isGraphqlSpecifiedScalar(it.getName()))
                .forEach(it -> add(named, it.getName(), it));
        registry.getDirectiveDefinitions().values().stream()
                .filter(it -> !DirectiveInfo.isGraphqlSpecifiedDirective(it.getName()))
                .forEach(it -> add(named, it.getName(), it));
        addExtensions(named, registry.objectTypeExtensions());
        addExtensions(named, registry.interfaceTypeExtensions());
        addExtensions(named, registry.unionTypeExtensions());
        addExtensions(named, registry.enumTypeExtensions());
        addExtensions(named, registry.scalarTypeExtensions());
        addExtensions(named, registry.inputObjectTypeExtensions());
        named.values().forEach(definitions::addAll);

        String printed = definitions.stream().map(AstPrinter::printAst).collect(Collectors.joining("\n"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(printed.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void addExtensions(Map<String, List<Definition<?>>> named,
                                      Map<String, ? extends List<? extends Definition<?>>> extensions) {
        extensions.forEach((name, list) -> list.forEach(it -> add(named, name, it)));
    }

    private static void add(Map<String, List<Definition<?>>> named, String name, Definition<?> node) {
        named.computeIfAbsent(name, it -> new ArrayList<>()).add(node);
    }

}
