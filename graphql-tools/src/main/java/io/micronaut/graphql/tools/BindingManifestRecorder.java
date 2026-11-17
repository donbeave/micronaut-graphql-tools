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

import graphql.GraphQL;
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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import static io.micronaut.graphql.tools.GraphQLUtils.printType;

/**
 * Collects the lines of the {@link BindingManifest} while the schema is bound.
 */
@Internal
final class BindingManifestRecorder {

    static final String KIND_ROOT = "root";
    static final String KIND_RESOLVER = "resolver";
    static final String KIND_PROPERTY = "property";

    private static final String ARROW = " → ";
    private static final String DOT = " · ";
    private static final String NONE = "-";

    // key: what the line describes (a type, a coordinate), so that a later binding of the same coordinate replaces it
    private final Map<String, String> lines = new HashMap<>();
    private final Set<String> diagnostics = new TreeSet<>();
    private final Set<String> scalars = new TreeSet<>();

    void type(String typeName, String kind, @Nullable Class<?> javaType) {
        lines.put(typeName, line(typeName, kind, javaType == null ? NONE : javaType.getTypeName(), NONE, NONE));
    }

    void scalar(String typeName) {
        scalars.add(typeName);
    }

    void field(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition, String kind, String member,
               Argument<?> declaredType, @Nullable Type genericType, boolean subscription) {
        StringBuilder coordinate = new StringBuilder(objectTypeDefinition.getName()).append('.')
                .append(fieldDefinition.getName());
        if (!fieldDefinition.getInputValueDefinitions().isEmpty()) {
            coordinate.append(fieldDefinition.getInputValueDefinitions().stream()
                    .map(it -> it.getName() + ": " + printType(it.getType()))
                    .collect(Collectors.joining(", ", "(", ")")));
        }
        coordinate.append(": ").append(printType(fieldDefinition.getType()));

        lines.put(objectTypeDefinition.getName() + "." + fieldDefinition.getName(), line(coordinate.toString(), kind,
                member, typeName(declaredType, genericType), convertedTypeName(declaredType, genericType, subscription)));
    }

    void resolves(String abstractTypeName, String objectTypeName, Class<?> javaType) {
        lines.put(abstractTypeName + "/" + objectTypeName,
                line(abstractTypeName + "/" + objectTypeName, "resolves", javaType.getTypeName(), NONE, NONE));
    }

    void enumValue(String enumTypeName, String value, Class<?> enumClass) {
        lines.put(enumTypeName + "." + value,
                line(enumTypeName + "." + value, "enumValue", enumClass.getTypeName() + "#" + value, NONE, NONE));
    }

    void inputField(String inputTypeName, String fieldName, graphql.language.Type<?> graphQlType, Class<?> inputClass,
                    BeanProperty<?, ?> property) {
        Type genericType = genericType(property);
        lines.put(inputTypeName + "." + fieldName, line(inputTypeName + "." + fieldName + ": " + printType(graphQlType),
                "inputField", inputClass.getTypeName() + "#" + property.getName(),
                typeName(property.asArgument(), genericType), convertedTypeName(property.asArgument(), genericType, false)));
    }

    /**
     * @param kind  the kind of finding, for example {@code unreachable}
     * @param parts what it is about, separated by {@code " · "} in the line
     */
    void diagnostic(String kind, String... parts) {
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
     * @return the manifest: header, and the recorded lines plus the scalar lines, sorted
     */
    BindingManifest build(TypeDefinitionRegistry registry, GraphQLSchema schema) {
        List<String> body = new ArrayList<>(lines.values());
        body.addAll(diagnostics);
        for (String scalar : scalars) {
            GraphQLType type = schema.getType(scalar);
            Class<?> coercing = type instanceof GraphQLScalarType scalarType ? scalarType.getCoercing().getClass() : null;
            body.add(line(scalar, "scalar", coercing == null ? NONE : coercing.getTypeName(), NONE, NONE));
        }
        body.sort(Comparator.naturalOrder());

        List<String> header = List.of(
                BindingManifest.FORMAT,
                "# binder: micronaut-graphql-tools " + version(BindingManifestRecorder.class.getPackage()
                        .getImplementationVersion()),
                "# graphql-java: " + graphQlJavaVersion(),
                "# schema-sha256: " + schemaSha256(registry)
        );

        return new BindingManifest(header, body);
    }

    /**
     * @param executable a method
     * @param ownerType  the class the method is declared on, possibly an AOP proxy
     * @return {@code Class#method(parameter types)}
     */
    static String methodMember(Executable<?, ?> executable, Class<?> ownerType) {
        return userClass(ownerType).getTypeName() + "#" + MicronautUtils.getMethodName(executable)
                + String.join(", ", JavaTypeNames.parameterTypes(executable)).transform(it -> "(" + it + ")");
    }

    /**
     * @param executable a method of a bean introspection ({@code @GraphQLField})
     * @param beanType   the introspected class, used when reflection cannot find the method
     * @return {@code Class#method(parameter types)}, the class being the one the method is written in
     */
    static String introspectedMethodMember(Executable<?, ?> executable, Class<?> beanType) {
        return methodMember(executable, JavaTypeNames.reflect(executable)
                .<Class<?>>map(Method::getDeclaringClass)
                .orElse(beanType));
    }

    /**
     * @param executable a method
     * @return its generic return type by reflection, or {@code null} without reflection
     */
    @Nullable
    static Type genericReturnType(Executable<?, ?> executable) {
        return JavaTypeNames.reflect(executable).map(Method::getGenericReturnType).orElse(null);
    }

    /**
     * @param beanProperty a bean property
     * @return the generic return type of its getter by reflection, or {@code null}
     */
    @Nullable
    static Type genericType(BeanProperty<?, ?> beanProperty) {
        Method getter = getter(beanProperty);
        return getter == null ? null : getter.getGenericReturnType();
    }

    /**
     * @param beanProperty a bean property
     * @return {@code Class#getter()}, the class being the one that declares the getter; {@code Class#property} when the
     * getter cannot be found by reflection
     */
    static String propertyMember(BeanProperty<?, ?> beanProperty) {
        Method getter = getter(beanProperty);
        if (getter != null) {
            return getter.getDeclaringClass().getTypeName() + "#" + getter.getName() + "()";
        }
        return beanProperty.getDeclaringBean().getBeanType().getTypeName() + "#" + beanProperty.getName();
    }

    @Nullable
    private static Method getter(BeanProperty<?, ?> beanProperty) {
        Class<?> beanType = beanProperty.getDeclaringBean().getBeanType();
        String name = beanProperty.getName();
        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String candidate : List.of("get" + capitalized, "is" + capitalized, name)) {
            try {
                return beanType.getMethod(candidate);
            } catch (NoSuchMethodException e) {
                // try the next accessor name
            } catch (SecurityException | LinkageError e) {
                // no reflection (a native image without reflection configuration): use the compile-time metadata
                JavaTypeNames.fallback(beanType.getTypeName() + "#" + name, e);
                return null;
            }
        }
        return null;
    }

    private static String typeName(Argument<?> argument, @Nullable Type genericType) {
        return genericType != null ? genericType.getTypeName() : JavaTypeNames.of(argument);
    }

    private static String convertedTypeName(Argument<?> argument, @Nullable Type genericType, boolean subscription) {
        return genericType != null
                ? ReturnTypes.convertedTypeName(genericType, subscription)
                : ReturnTypes.convertedTypeName(argument, subscription);
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
     * @return the version graphql-java's jar manifest declares ({@code Bundle-Version}, without a {@code .0} patch
     * level), or {@code unknown}
     */
    private static String graphQlJavaVersion() {
        try {
            ClassLoader classLoader = GraphQL.class.getClassLoader();
            Enumeration<URL> resources = classLoader.getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                try (InputStream inputStream = resources.nextElement().openStream()) {
                    Manifest manifest = new Manifest(inputStream);
                    if ("graphql-java".equals(manifest.getMainAttributes().getValue("Bundle-SymbolicName"))) {
                        String version = manifest.getMainAttributes().getValue("Bundle-Version");
                        return version(version == null ? null : version.replaceFirst("^(\\d+\\.\\d+)\\.0$", "$1"));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // fall through
        }
        return "unknown";
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
