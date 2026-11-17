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

import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.Node;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.graphql.tools.processing.ModelMembers.Property;
import io.micronaut.graphql.tools.processing.ResolverBeans.Bean;
import io.micronaut.graphql.tools.processing.ResolverBeans.Method;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The checks of the Java code against the schema. They walk the schema from the root operation types the way the
 * library binds it at startup ({@code GraphQLRuntimeWiringGenerator}): a root field binds the root resolver method of
 * its name; the Java type a method or property returns is the Java type of the field's GraphQL type; a field of an
 * object type binds a property or {@code @GraphQLField} method of the introspected Java type, or a type resolver
 * method, as {@code precedence} and {@code resolverDirectives} decide.
 *
 * <p>Where the startup decides with something the compilation does not have, the checks do not guess: they skip that
 * part and record it, so that they never reject what the startup accepts. Such parts are runtime beans (the Java
 * classes of custom scalars, the {@code SchemaMappingDictionary}, and with it the members of unions and interfaces) and
 * implementations of contracts that are not in this compilation.</p>
 */
final class BindingChecks {

    static final String DATA_FETCHING_ENVIRONMENT = "graphql.schema.DataFetchingEnvironment";
    static final String COMPLETION_STAGE = "java.util.concurrent.CompletionStage";
    static final String PUBLISHER = "org.reactivestreams.Publisher";
    static final String DATA_FETCHER_RESULT = "graphql.execution.DataFetcherResult";
    static final String OPTIONAL = "java.util.Optional";
    static final String ARGUMENT_VALUE = "io.micronaut.graphql.tools.ArgumentValue";

    /**
     * The Java types of the built-in scalars, as the library's {@code SystemTypes} lists them.
     */
    static final Map<String, Set<String>> BUILT_IN_SCALARS = Map.of(
            "String", Set.of("java.lang.String"),
            "ID", Set.of("java.lang.String"),
            "Boolean", Set.of("boolean", "java.lang.Boolean"),
            "Int", Set.of("int", "java.lang.Integer"),
            "Float", Set.of("float", "java.lang.Float", "double", "java.lang.Double"),
            "Long", Set.of("long", "java.lang.Long"),
            "Short", Set.of("short", "java.lang.Short"),
            "BigDecimal", Set.of("java.math.BigDecimal"),
            "BigInteger", Set.of("java.math.BigInteger")
    );

    private static final Pattern SCHEMA_COORDINATE = Pattern.compile("[_A-Za-z][_0-9A-Za-z]*\\.[_A-Za-z][_0-9A-Za-z]*");
    private static final List<String> RETURN_TYPE_WRAPPERS = List.of(COMPLETION_STAGE, PUBLISHER, DATA_FETCHER_RESULT,
            OPTIONAL);

    private final TypeDefinitionRegistry registry;
    private final ProcessorOptions options;
    private final Problems problems;
    private final JavaTypes javaTypes;
    private final ResolverBeans beans;
    private final ModelMembers models;
    private final Collection<TypeElement> types;

    private final Set<String> operationTypes = new TreeSet<>();
    private String subscriptionType;
    private final Map<String, TypeElement> processedTypes = new LinkedHashMap<>();
    private final Map<String, String> mappedBy = new LinkedHashMap<>();
    private final Set<String> traversedModelClasses = new TreeSet<>();
    private final Map<Method, Set<String>> boundMethods = new IdentityHashMap<>();
    private final Map<Method, Boolean> shadowedMethods = new IdentityHashMap<>();
    private final BindingManifestLines manifest;
    private final TypeNames typeNames;
    private final Map<String, String> skipped = new TreeMap<>();
    private boolean objectTypesSkipped;
    // what the manifest leaves out: types whose lines are left out, types to leave out with what only they reach, fields,
    // and resolver methods whose !unbound line cannot be decided
    private final Set<String> omittedTypes = new TreeSet<>();
    private final Set<String> omissionRoots = new TreeSet<>();
    private final Set<String> omittedFields = new TreeSet<>();
    private final Set<String> undecidedMembers = new TreeSet<>();

    /**
     * @param registry  the schema
     * @param options   the options
     * @param problems  where errors are reported
     * @param javaTypes the type helpers
     * @param types     the types of the compilation
     * @param manifest  where the lines of the binding manifest are recorded
     */
    BindingChecks(TypeDefinitionRegistry registry, ProcessorOptions options, Problems problems, JavaTypes javaTypes,
                  Collection<TypeElement> types, BindingManifestLines manifest) {
        this.registry = registry;
        this.options = options;
        this.problems = problems;
        this.javaTypes = javaTypes;
        this.types = types;
        this.beans = new ResolverBeans(javaTypes, types);
        this.models = new ModelMembers(javaTypes, types);
        this.manifest = manifest;
        this.typeNames = new TypeNames(javaTypes);
    }

    /**
     * @return the parts of the schema the checks could not decide at compile time and left to the startup, by
     * coordinate, with the reason; the processor reports each as a note
     */
    Map<String, String> skipped() {
        return skipped;
    }

    /**
     * Records a part of the schema the checks leave to the startup.
     *
     * @param coordinate         the type or field
     * @param reason             why it cannot be decided here
     * @param mayReachObjectTypes whether the startup may bind object types through it that the checks therefore do not
     *                           reach (members of unions and interfaces, fields of unchecked types); a custom scalar or
     *                           an unchecked input class cannot
     */
    private void skip(String coordinate, String reason, boolean mayReachObjectTypes) {
        skipped.merge(coordinate, reason, (first, second) -> first.equals(second) ? first : first + "; " + second);
        objectTypesSkipped |= mayReachObjectTypes;
    }

    /**
     * Runs the checks.
     *
     * @param schemaDefinition the schema definition
     */
    void check(SchemaDefinition schemaDefinition) {
        for (OperationTypeDefinition operation : schemaDefinition.getOperationTypeDefinitions()) {
            operationTypes.add(operation.getTypeName().getName());
            if (operation.getName().equals("subscription")) {
                subscriptionType = operation.getTypeName().getName();
            }
        }

        checkContracts();
        checkSchemaCoordinates();
        checkTypeResolverAssignments();

        if (beans.rootBeans().isEmpty()) {
            problems.error("the compilation has no root resolver: no bean is or implements a @GraphQLRootResolver.");
            return;
        }

        for (OperationTypeDefinition operation : schemaDefinition.getOperationTypeDefinitions()) {
            registry.getType(operation.getTypeName().getName(), ObjectTypeDefinition.class).ifPresent(type -> {
                manifest.type(type.getName(), "object", null);
                for (FieldDefinition field : type.getFieldDefinitions()) {
                    bindRootField(type, field);
                }
            });
        }

        checkUnboundMethods();
        checkSharedRootMethods();
        checkSchemaCoordinateBindings(schemaDefinition);
        recordImplementedInterfaces();
    }

    // ---------------------------------------------------------------- contracts and coordinates (before binding)

    /**
     * An interface of the compilation that carries a resolver annotation is a contract: exactly one bean must
     * implement it. Without one its fields are not bound; with two, every field it declares has two methods.
     */
    private void checkContracts() {
        Map<String, List<Bean>> implementors = new LinkedHashMap<>();
        for (Bean bean : beans.beans()) {
            for (TypeElement supertype : javaTypes.hierarchy(bean.type())) {
                if (supertype.getKind() == ElementKind.INTERFACE && isContract(supertype)) {
                    implementors.computeIfAbsent(javaTypes.name(supertype), it -> new ArrayList<>()).add(bean);
                }
            }
        }
        for (TypeElement type : types) {
            if (type.getKind() == ElementKind.INTERFACE && isContract(type)
                    && !implementors.containsKey(javaTypes.name(type))) {
                problems.error(javaTypes.name(type), "the resolver contract has no bean class: no bean of this "
                        + "compilation implements it, so the fields it declares are not bound. Implement it in a class "
                        + "with a bean annotation such as @Singleton.", null, type);
            }
        }
        implementors.forEach((contract, list) -> {
            if (list.size() > 1) {
                Bean reported = list.stream().filter(it -> types.contains(it.type())).findFirst().orElse(list.get(0));
                problems.error(contract, "the resolver contract has several bean classes ("
                        + list.stream().map(Bean::name).collect(Collectors.joining(", ")) + "), so each of its "
                        + "fields has several resolver methods. Exactly one bean may implement it.", null, reported.type());
            }
        });
    }

    private static boolean isContract(TypeElement type) {
        return Annotations.has(type, Annotations.ROOT_RESOLVER) || Annotations.has(type, Annotations.TYPE_RESOLVER);
    }

    /**
     * Every {@code @GraphQLSchemaCoordinate} is validated, never trusted, as at startup.
     */
    private void checkSchemaCoordinates() {
        for (Bean bean : beans.beans()) {
            for (Method method : bean.methods()) {
                if (method.coordinate() != null) {
                    String problem = coordinateProblem(bean, method);
                    if (problem != null) {
                        problems.error(method.coordinate(), "@GraphQLSchemaCoordinate on "
                                + javaTypes.describe(method.element()) + ": " + problem, null, method.element());
                    }
                }
            }
        }
        for (TypeElement type : types) {
            if (beans.beans().stream().anyMatch(it -> it.type().equals(type))) {
                continue;
            }
            for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
                Annotations.find(method, Annotations.SCHEMA_COORDINATE).flatMap(Annotations::stringValue)
                        .filter(it -> beans.isBean(type))
                        .ifPresent(coordinate -> problems.error(coordinate, "@GraphQLSchemaCoordinate on "
                                + javaTypes.describe(method) + ": the bean is neither a @GraphQLRootResolver nor a "
                                + "@GraphQLTypeResolver.", null, method));
            }
        }
    }

    private String coordinateProblem(Bean bean, Method method) {
        String coordinate = method.coordinate();
        if (!SCHEMA_COORDINATE.matcher(coordinate).matches()) {
            return "the value is not a schema coordinate of the form Type.field.";
        }
        String typeName = coordinate.substring(0, coordinate.indexOf('.'));
        String fieldName = coordinate.substring(coordinate.indexOf('.') + 1);
        Optional<TypeDefinition> type = registry.getType(typeName);
        if (type.isEmpty()) {
            return "the schema has no type " + typeName + ".";
        }
        List<FieldDefinition> fields;
        if (type.get() instanceof ObjectTypeDefinition object) {
            fields = object.getFieldDefinitions();
        } else if (type.get() instanceof InterfaceTypeDefinition iface) {
            fields = iface.getFieldDefinitions();
        } else {
            return typeName + " is neither an object type nor an interface.";
        }
        if (fields.stream().noneMatch(it -> it.getName().equals(fieldName))) {
            return "the type " + typeName + " has no field " + fieldName + ".";
        }
        String methodName = method.element().getSimpleName().toString();
        if (!fieldName.equals(methodName)) {
            return "the field name " + fieldName + " differs from the method name " + methodName + ".";
        }
        boolean operationType = operationTypes.contains(typeName);
        if (bean.root() && !operationType) {
            return "a @GraphQLRootResolver binds fields of the root operation types " + operationTypes
                    + " only, not of " + typeName + ".";
        }
        if (!bean.contracts().isEmpty() && operationType) {
            return "a @GraphQLTypeResolver cannot bind a field of the root operation type " + typeName + ".";
        }
        return null;
    }

    /**
     * A type resolver of several classes binds each method for the class of its first parameter; a method whose first
     * parameter is none of them fails, as at startup.
     */
    private void checkTypeResolverAssignments() {
        for (Bean bean : beans.beans()) {
            for (Method method : bean.unassigned()) {
                if (bean.isCandidate(method)) {
                    problems.error(bean.name(), "the type resolver implements contracts for several classes ("
                            + bean.contracts().values().stream().map(javaTypes::name).sorted()
                            .collect(Collectors.joining(", ")) + "), and the first parameter of "
                            + javaTypes.describe(method.element()) + " is none of them, so it is not clear which "
                            + "class it resolves.", null, method.element());
                }
            }
        }
    }

    // ---------------------------------------------------------------- binding

    private void bindRootField(ObjectTypeDefinition type, FieldDefinition field) {
        String coordinate = type.getName() + "." + field.getName();
        List<Method> candidates = new ArrayList<>();
        for (Bean bean : beans.rootBeans()) {
            candidates.addAll(bean.find(bean.methods(), field.getName(), coordinate));
        }
        if (candidates.isEmpty()) {
            problems.error(coordinate, "no root resolver method binds the field: none of the root resolvers ("
                    + beans.rootBeans().stream().map(Bean::name).collect(Collectors.joining(", "))
                    + ") has a public method " + field.getName() + suggestion(field, null) + ".", field, null);
            return;
        }
        if (candidates.size() > 1) {
            // one finding, reported once, on a method javac compiles in this round where there is one
            Method reported = candidates.stream().filter(it -> types.contains(it.bean().type())).findFirst()
                    .orElse(candidates.get(0));
            problems.error(coordinate, "several root resolver methods can bind the field: "
                    + candidates.stream().map(it -> javaTypes.describe(it.element())).collect(Collectors.joining(", "))
                    + ". Keep one, or scope each to its root operation type with @GraphQLSchemaCoordinate.",
                    field, reported.element());
            // the error accounts for the methods; they are not also reported as binding no field
            candidates.forEach(it -> shadowedMethods.put(it, true));
            return;
        }
        Method method = candidates.get(0);
        bound(method, coordinate);
        recordField(type, field, "root", beanMethodMember(method), method.element().getReturnType(),
                type.getName().equals(subscriptionType));
        checkMethod(method.element(), method.bean().type(), null, type, field);
    }

    /**
     * Binds an object type to the Java type a field returns, as {@code processObjectTypeDefinition}: a custom class or
     * interface, the same (or an alike) Java type wherever the type is reached, with a bean introspection, then each
     * of its fields.
     */
    private void bindObjectType(ObjectTypeDefinition type, TypeMirror javaType, String coordinate, Node<?> node,
                                Element element) {
        TypeElement javaClass = javaTypes.element(javaType);
        String javaName = javaTypes.erasedName(javaType);
        if (javaClass == null || isBuiltInJavaClass(javaName) || javaClass.getKind() == ElementKind.ENUM
                || javaClass.getKind() == ElementKind.ANNOTATION_TYPE) {
            problems.error(coordinate, "the field is mapped to the incorrect class: the object type " + type.getName()
                    + " needs a custom class, the Java type is " + javaName + ".", node, element);
            return;
        }
        if (!mapType(type, javaClass, coordinate, node, element)) {
            return;
        }

        Optional<TypeElement> introspected = introspection(type, javaClass, coordinate, node, element);
        if (introspected.isEmpty()) {
            return;
        }
        if (models.usesFieldAccess(introspected.get())) {
            skip(type.getName(), javaTypes.name(introspected.get()) + " is introspected with accessKind = FIELD, which the "
                    + "processor does not model", true);
            omittedTypes.add(type.getName());
            return;
        }
        manifest.type(type.getName(), "object", javaTypes.name(introspected.get()));
        TypeElement sourceClass = models.sourceClass(introspected.get());
        traversedModelClasses.add(javaTypes.name(sourceClass));

        for (FieldDefinition field : type.getFieldDefinitions()) {
            bindField(type, field, introspected.get(), sourceClass);
        }
    }

    /**
     * Records the Java type of a schema type, or checks it against the Java type recorded before: one schema type has
     * one Java type wherever it is reached. Two Java types bind an object type alike when they have the same contract
     * and the same introspection.
     *
     * @return whether the type is reached for the first time, so that it still has to be checked
     */
    private boolean mapType(TypeDefinition<?> type, TypeElement javaClass, String coordinate, Node<?> node,
                            Element element) {
        TypeElement previous = processedTypes.get(type.getName());
        if (previous == null) {
            processedTypes.put(type.getName(), javaClass);
            mappedBy.put(type.getName(), coordinate);
            return true;
        }
        boolean same = type instanceof ObjectTypeDefinition ? alike(previous, javaClass) : previous.equals(javaClass);
        if (!same) {
            problems.error(coordinate, "the " + SchemaChecks.kind(type) + " " + type.getName() + " is mapped to "
                    + javaTypes.name(javaClass) + " here and to " + javaTypes.name(previous) + " by "
                    + mappedBy.get(type.getName()) + ". One schema type needs one Java type.", node, element);
        }
        return false;
    }

    private boolean alike(TypeElement first, TypeElement second) {
        if (first.equals(second)) {
            return true;
        }
        if (!models.contractOf(first).orElse(first).equals(models.contractOf(second).orElse(second))) {
            return false;
        }
        Optional<TypeElement> firstIntrospection = models.introspectedType(first);
        return firstIntrospection.isPresent() && firstIntrospection.equals(models.introspectedType(second));
    }

    /**
     * The Java type whose bean introspection binds an object type, as {@code GraphQLBeanIntrospectionRegistry}
     * decides, with its errors: a contract interface with its own introspection (every class annotated
     * {@code @GraphQLType(contract)} must implement it); a Java interface without one is read through its single
     * implementation (a strict-mode finding); a class needs an introspection.
     *
     * @return the introspected type, or empty after an error or if it cannot be decided in this compilation
     */
    private Optional<TypeElement> introspection(ObjectTypeDefinition type, TypeElement javaClass, String coordinate,
                                                Node<?> node, Element element) {
        TypeElement contract = javaClass.getKind() == ElementKind.INTERFACE ? javaClass
                : models.contractOf(javaClass).orElse(null);
        if (contract != null && models.isIntrospected(contract)) {
            for (TypeElement implementation : models.implementations(contract)) {
                if (!implementsContract(implementation, contract, coordinate, node)) {
                    return Optional.empty();
                }
            }
            return Optional.of(contract);
        }
        if (javaClass.getKind() == ElementKind.INTERFACE) {
            List<TypeElement> implementations = models.implementations(javaClass);
            if (implementations.isEmpty()) {
                if (isChecked(javaClass)) {
                    // a JDK interface, or one of this compilation, which no class of a dependency can implement
                    problems.error(coordinate, "the object type " + type.getName() + " is mapped to the Java interface "
                            + javaTypes.name(javaClass) + ", which has no introspection of its own and no class "
                            + "annotated @GraphQLType(" + javaClass.getSimpleName() + ".class). Return a class with a bean "
                            + "introspection, or annotate the interface with @GraphQLType.", node, element);
                } else {
                    skip(type.getName(), "the Java interface " + javaTypes.name(javaClass) + " has no introspection of its "
                            + "own and no implementation in this compilation", true);
                    omittedTypes.add(type.getName());
                }
                return Optional.empty();
            }
            if (implementations.size() > 1) {
                problems.error(coordinate, "the object type " + type.getName() + " is mapped to the Java interface "
                        + javaTypes.name(javaClass) + ", which has no introspection of its own and several classes "
                        + "annotated @GraphQLType(" + javaClass.getSimpleName() + ".class): " + implementations.stream()
                        .map(javaTypes::name).collect(Collectors.joining(", ")) + ". Annotate the interface with "
                        + "@GraphQLType or @Introspected.", node, element);
                return Optional.empty();
            }
            TypeElement implementation = implementations.get(0);
            if (!implementsContract(implementation, javaClass, coordinate, node)) {
                return Optional.empty();
            }
            if (options.strict()) {
                problems.error(coordinate, "the object type " + type.getName() + " is mapped to the Java interface "
                        + javaTypes.name(javaClass) + ", which has no introspection of its own, so it is read through "
                        + "the introspection of " + javaTypes.name(implementation) + " and a getter only the class "
                        + "declares binds silently (strict mode). Annotate the interface with @GraphQLType or "
                        + "@Introspected.", node, javaClass);
            }
            return Optional.of(implementation);
        }
        if (!models.isIntrospected(javaClass)) {
            if (isChecked(javaClass)) {
                problems.error(coordinate, "the object type " + type.getName() + " is mapped to "
                        + javaTypes.name(javaClass) + ", which has no bean introspection. Annotate it with "
                        + "@GraphQLType.", node, javaClass);
            } else {
                skip(type.getName(), javaTypes.name(javaClass) + " is not in this compilation and carries no "
                        + "introspection annotation", true);
                omittedTypes.add(type.getName());
            }
            return Optional.empty();
        }
        return Optional.of(javaClass);
    }

    private boolean implementsContract(TypeElement implementation, TypeElement contract, String coordinate,
                                       Node<?> node) {
        if (javaTypes.isA(implementation.asType(), contract.getQualifiedName().toString())) {
            return true;
        }
        problems.error(coordinate, javaTypes.name(implementation) + " is annotated @GraphQLType("
                + contract.getSimpleName() + ".class) but does not implement " + javaTypes.name(contract) + ".", node,
                implementation);
        return false;
    }

    /**
     * @return whether the processor can tell that a Java type has no bean introspection: it is in this compilation
     * (a source, or, in an incremental compilation, an unchanged class of the class output), or a JDK class, which never
     * has one
     */
    private boolean isChecked(TypeElement javaClass) {
        return types.contains(javaClass) || javaClass.getQualifiedName().toString().startsWith("java.")
                || javaTypes.isInClassOutput(javaClass);
    }

    private static boolean isBuiltInJavaClass(String javaName) {
        return BUILT_IN_SCALARS.values().stream().anyMatch(it -> it.contains(javaName));
    }

    /**
     * Checks an enum, as {@code processEnumTypeDefinition}: a Java enum with a constant for every schema value, and in
     * strict mode no constant without a schema value.
     */
    private void checkEnumType(EnumTypeDefinition type, TypeMirror javaType, boolean input, String coordinate,
                               Node<?> node, Element element) {
        TypeElement javaClass = javaTypes.element(javaType);
        if (javaClass == null || javaClass.getKind() != ElementKind.ENUM) {
            problems.error(coordinate, "the " + (input ? "argument or input field" : "field") + " is mapped to the "
                    + "incorrect class: the enum " + type.getName() + " needs a Java enum, the Java type is "
                    + javaTypes.erasedName(javaType) + ".", node, element);
            return;
        }
        if (!mapType(type, javaClass, coordinate, node, element)) {
            return;
        }
        List<Element> constants = javaClass.getEnclosedElements().stream()
                .filter(it -> it.getKind() == ElementKind.ENUM_CONSTANT)
                .map(it -> (Element) it)
                .toList();
        Set<String> constantNames = constants.stream().map(it -> it.getSimpleName().toString())
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> values = type.getEnumValueDefinitions().stream().map(it -> it.getName())
                .collect(Collectors.toCollection(TreeSet::new));
        List<String> missing = values.stream().filter(it -> !constantNames.contains(it)).toList();
        if (!missing.isEmpty()) {
            problems.error(type.getName(), "the Java enum " + javaTypes.name(javaClass) + " has no constant for the "
                    + "schema values " + missing + ".", type, javaClass);
        } else {
            manifest.type(type.getName(), "enum", javaTypes.name(javaClass));
            values.forEach(it -> manifest.enumValue(type.getName(), it, javaTypes.name(javaClass)));
        }
        constants.stream().map(it -> it.getSimpleName().toString()).filter(it -> !values.contains(it)).sorted()
                .forEach(it -> manifest.diagnostic("unbound", javaTypes.name(javaClass) + "#" + it));
        if (options.strict()) {
            for (Element constant : constants) {
                if (!values.contains(constant.getSimpleName().toString())) {
                    problems.error(type.getName(), "the enum constant " + javaTypes.name(javaClass) + "#"
                            + constant.getSimpleName() + " has no value in the schema enum " + type.getName()
                            + " (strict mode). Add the value to the schema or remove the constant.", type, constant);
                }
            }
        }
    }

    /**
     * Checks a union or interface a field returns: the field must declare a Java interface. Its members come from the
     * {@code SchemaMappingDictionary}, a runtime bean, so they are not checked here.
     */
    private void checkAbstractType(TypeDefinition<?> type, TypeMirror javaType, String coordinate, Node<?> node,
                                   Element element) {
        TypeElement javaClass = javaTypes.element(javaType);
        if (javaClass == null || javaClass.getKind() != ElementKind.INTERFACE) {
            problems.error(coordinate, "the field is mapped to the incorrect class: the " + SchemaChecks.kind(type)
                    + " " + type.getName() + " needs a Java interface, the Java type is "
                    + javaTypes.erasedName(javaType) + ".", node, element);
            return;
        }
        if (mapType(type, javaClass, coordinate, node, element)) {
            manifest.type(type.getName(), type instanceof UnionTypeDefinition ? "union" : "interface",
                    javaTypes.name(javaClass));
            skip(type.getName(), "the Java classes of its object types come from the SchemaMappingDictionary, "
                    + "a runtime bean", true);
            omissionRoots.addAll(memberTypes(type));
        }
    }

    /**
     * Binds an input type, as {@code processInputObjectTypeDefinition}: an introspected class with a property for every
     * input field, of a matching type, and, in strict mode or with {@code input.unmappedProperties=fail}, no property
     * a request can set that the input type does not define.
     */
    private void bindInputType(InputObjectTypeDefinition type, TypeMirror javaType, String coordinate, Node<?> node,
                               Element element) {
        TypeElement javaClass = javaTypes.element(javaType);
        if (javaClass == null || javaClass.getKind() != ElementKind.CLASS && javaClass.getKind() != ElementKind.RECORD) {
            problems.error(coordinate, "the argument or input field is mapped to the incorrect class: the input type "
                    + type.getName() + " needs a custom class, the Java type is " + javaTypes.erasedName(javaType)
                    + ".", node, element);
            return;
        }
        if (!mapType(type, javaClass, coordinate, node, element)) {
            return;
        }
        if (!models.isIntrospected(javaClass)) {
            if (isChecked(javaClass)) {
                problems.error(coordinate, "the input type " + type.getName() + " is mapped to "
                        + javaTypes.name(javaClass) + ", which has no bean introspection. Annotate it with "
                        + "@GraphQLInput.", node, javaClass);
            } else {
                skip(type.getName(), javaTypes.name(javaClass) + " is not in this compilation and carries no "
                        + "introspection annotation", false);
                omittedTypes.add(type.getName());
            }
            return;
        }
        if (models.usesFieldAccess(javaClass)) {
            skip(type.getName(), javaTypes.name(javaClass) + " is introspected with accessKind = FIELD, which the processor "
                    + "does not model", false);
            omittedTypes.add(type.getName());
            return;
        }

        Map<String, Property> properties = models.properties(javaClass);
        for (InputValueDefinition field : type.getInputValueDefinitions()) {
            String fieldCoordinate = type.getName() + "." + field.getName();
            Property property = properties.get(field.getName());
            if (property == null) {
                problems.error(fieldCoordinate, "the input class " + javaTypes.name(javaClass) + " has no property "
                        + field.getName() + ".", field, javaClass);
                continue;
            }
            checkInputType(property.type(), field.getType(), true, fieldCoordinate, field, property.accessor());
            manifest.inputField(type.getName(), field.getName(), field.getType(), javaTypes.name(javaClass),
                    property.name(), typeNames.render(declaredType(property)),
                    typeNames.converted(declaredType(property), false));
            if (options.detectOmission() && javaTypes.is(property.type(), ARGUMENT_VALUE)) {
                boolean writeOnly = !property.accessor().getParameters().isEmpty();
                if (!property.settable() || writeOnly) {
                    problems.error(fieldCoordinate, ARGUMENT_VALUE + " needs a " + (writeOnly ? "readable" : "writable")
                            + " property: it is " + (writeOnly ? "read to set it" : "set") + " to omitted() when the client "
                            + "leaves it out.", field, property.accessor());
                }
            }
        }

        Set<String> fields = type.getInputValueDefinitions().stream().map(InputValueDefinition::getName)
                .collect(Collectors.toSet());
        List<String> unmapped = properties.values().stream()
                .filter(Property::settable)
                .map(Property::name)
                .filter(it -> !fields.contains(it))
                .sorted()
                .toList();
        unmapped.forEach(it -> manifest.diagnostic("unbound", javaTypes.name(javaClass) + "#" + it));
        manifest.type(type.getName(), "input", javaTypes.name(javaClass));
        if (options.failOnUnmappedProperties() && !unmapped.isEmpty()) {
            problems.error(type.getName(), "the input class " + javaTypes.name(javaClass) + " has properties that "
                    + "the input type does not define, so no request can set them: " + String.join(", ", unmapped)
                    + " (" + (options.unmappedProperties() == null ? "strict mode" : ProcessorOptions.UNMAPPED_PROPERTIES
                    + "=fail") + "). Remove them, add them to the input type, or set "
                    + ProcessorOptions.UNMAPPED_PROPERTIES + "=ignore.", type, javaClass);
        }
    }

    private void bindField(ObjectTypeDefinition type, FieldDefinition field, TypeElement introspected,
                           TypeElement sourceClass) {
        String coordinate = type.getName() + "." + field.getName();
        Optional<Property> property = models.property(introspected, field.getName());
        List<ExecutableElement> fieldMethods = models.fieldMethods(introspected, field.getName());

        if (property.isPresent() && !fieldMethods.isEmpty() || fieldMethods.size() > 1) {
            List<String> members = new ArrayList<>();
            property.ifPresent(it -> members.add(javaTypes.describe(it.accessor())));
            fieldMethods.forEach(it -> members.add(javaTypes.describe(it)));
            problems.error(coordinate, "several members of " + javaTypes.name(introspected) + " can bind the field: "
                    + String.join(", ", members) + ".", field, fieldMethods.get(0));
            return;
        }

        boolean declaredByInterface = isDeclaredByInterface(type, field.getName());
        List<Method> resolvers = typeResolverMethods(sourceClass, coordinate, field.getName());
        boolean member = property.isPresent() || !fieldMethods.isEmpty();
        String directive = resolverDirective(field);

        if (member) {
            Element memberElement = property.<Element>map(Property::accessor).orElseGet(() -> fieldMethods.get(0));
            if (!resolvers.isEmpty()) {
                if (directive == null && options.precedence() == ProcessorOptions.Precedence.STRICT) {
                    problems.error(coordinate, "ambiguous binding: " + describeMember(property, fieldMethods)
                            + " and the type resolver method " + describeMethods(resolvers) + " can both bind the "
                            + "field. Remove one, mark the field with a directive listed in "
                            + ProcessorOptions.RESOLVER_DIRECTIVES + ", or set " + ProcessorOptions.PRECEDENCE + ".",
                            field, resolvers.get(0).element());
                    // the error accounts for the method; it is not also reported as binding no field
                    resolvers.forEach(it -> shadowedMethods.put(it, true));
                    return;
                }
                if (directive == null) {
                    // the precedence decides; the manifest records that it had to
                    manifest.diagnostic("ambiguous", coordinate, Stream.concat(
                            Stream.of(property.map(it -> propertyMember(it, introspected))
                                    .orElseGet(() -> methodMember(fieldMethods.get(0)))),
                            resolvers.stream().map(this::beanMethodMember).sorted())
                            .collect(Collectors.joining(" | ")));
                }
                if (directive == null && options.precedence() == ProcessorOptions.Precedence.PROPERTY_FIRST) {
                    resolvers.forEach(it -> shadowedMethods.put(it, true));
                    bindMember(type, field, introspected, property, fieldMethods);
                    return;
                }
                bindResolver(type, field, sourceClass, resolvers);
                return;
            }
            if (declaredByInterface && (directive != null
                    || options.precedence() == ProcessorOptions.Precedence.RESOLVER_FIRST)
                    && hasInterfaceResolver(introspected, field.getName())) {
                skip(coordinate, "a type resolver of a Java interface of " + javaTypes.name(introspected)
                        + " has a method " + field.getName() + " and may bind it", true);
                omitField(type, field);
                return;
            }
            if (directive != null) {
                problems.error(coordinate, "the field carries @" + directive + ", which needs a type resolver method, "
                        + "but only " + describeMember(property, fieldMethods) + " can bind it. Add a type resolver "
                        + "method for " + javaTypes.name(sourceClass) + ".", field, memberElement);
                return;
            }
            bindMember(type, field, introspected, property, fieldMethods);
            return;
        }

        if (resolvers.isEmpty()) {
            if (declaredByInterface && hasInterfaceResolver(introspected, field.getName())) {
                skip(coordinate, "a type resolver of a Java interface of " + javaTypes.name(introspected)
                        + " has a method " + field.getName() + " and may bind it", true);
                omitField(type, field);
                return;
            }
            problems.error(coordinate, "nothing binds the field: " + javaTypes.name(introspected)
                    + " has no property or @GraphQLField method " + field.getName() + ", and no type resolver of "
                    + javaTypes.name(sourceClass) + " has a method " + field.getName() + suggestion(field, sourceClass)
                    + ".", field, introspected);
            return;
        }
        bindResolver(type, field, sourceClass, resolvers);
    }

    private void bindMember(ObjectTypeDefinition type, FieldDefinition field, TypeElement introspected,
                            Optional<Property> property, List<ExecutableElement> fieldMethods) {
        String coordinate = type.getName() + "." + field.getName();
        if (property.isPresent()) {
            ExecutableElement accessor = property.get().accessor();
            recordField(type, field, "property", propertyMember(property.get(), introspected),
                    declaredType(property.get()), false);
            if (!field.getInputValueDefinitions().isEmpty()) {
                problems.error(coordinate, "the field has " + field.getInputValueDefinitions().size()
                        + " argument(s), but the property " + javaTypes.describe(accessor) + " takes none. Bind it with "
                        + "a method instead: " + field.getName() + suggestion(field, null) + ".", field, accessor);
                return;
            }
            checkReturnType(property.get().type(), field.getType(), true, false, coordinate, field, accessor);
            return;
        }
        ExecutableElement method = fieldMethods.get(0);
        recordField(type, field, "property", methodMember(method), method.getReturnType(), false);
        checkMethod(method, introspected, null, type, field);
    }

    private void bindResolver(ObjectTypeDefinition type, FieldDefinition field, TypeElement sourceClass,
                              List<Method> resolvers) {
        String coordinate = type.getName() + "." + field.getName();
        if (resolvers.size() > 1) {
            problems.error(coordinate, "several type resolver methods can bind the field: " + describeMethods(resolvers)
                    + ".", field, resolvers.get(0).element());
            resolvers.forEach(it -> shadowedMethods.put(it, true));
            return;
        }
        Method method = resolvers.get(0);
        bound(method, coordinate);
        recordField(type, field, "resolver", beanMethodMember(method), method.element().getReturnType(), false);
        checkMethod(method.element(), method.bean().type(), sourceClass, type, field);
    }

    private List<Method> typeResolverMethods(TypeElement sourceClass, String coordinate, String fieldName) {
        List<Method> result = new ArrayList<>();
        for (Bean bean : beans.typeBeans(sourceClass)) {
            result.addAll(bean.find(bean.methodsByModelClass().get(javaTypes.name(sourceClass)), fieldName, coordinate));
        }
        return result;
    }

    /**
     * @return whether a type resolver of a Java interface that the introspected type implements has a method of the
     * field's name: only then can the startup bind a field an SDL interface declares through that interface
     */
    private boolean hasInterfaceResolver(TypeElement introspected, String fieldName) {
        for (Bean bean : beans.beans()) {
            for (TypeElement modelClass : bean.contracts().values()) {
                if (modelClass.getKind() == ElementKind.INTERFACE && !modelClass.equals(introspected)
                        && javaTypes.isA(introspected.asType(), modelClass.getQualifiedName().toString())
                        && bean.methodsByModelClass().getOrDefault(javaTypes.name(modelClass), List.of()).stream()
                        .anyMatch(it -> it.element().getSimpleName().contentEquals(fieldName))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isDeclaredByInterface(ObjectTypeDefinition type, String fieldName) {
        return type.getImplements().stream()
                .map(it -> registry.getType(SchemaChecks.typeName(it), InterfaceTypeDefinition.class))
                .flatMap(Optional::stream)
                .anyMatch(it -> it.getFieldDefinitions().stream().anyMatch(field -> field.getName().equals(fieldName)));
    }

    private String resolverDirective(FieldDefinition field) {
        for (String directive : options.resolverDirectives()) {
            if (field.hasDirective(directive)) {
                return directive;
            }
        }
        return null;
    }

    private void bound(Method method, String coordinate) {
        boundMethods.computeIfAbsent(method, it -> new TreeSet<>()).add(coordinate);
    }

    // ---------------------------------------------------------------- methods, arguments and return types

    /**
     * Checks the parameters and the return type of a method that binds a field: the number of parameters besides
     * {@code DataFetchingEnvironment}, the source object first for a type resolver, then one parameter per argument in
     * the order of the schema. The types are those of the method as a member of its bean or introspected class, so that
     * a method a generic superclass declares is checked with the type arguments the class gives it.
     */
    private void checkMethod(ExecutableElement method, TypeElement owner, TypeElement sourceClass,
                             ObjectTypeDefinition type, FieldDefinition field) {
        String coordinate = type.getName() + "." + field.getName();
        List<? extends VariableElement> parameters = method.getParameters();
        ExecutableType memberType = javaTypes.memberType(owner, method);
        List<? extends TypeMirror> parameterTypes = memberType.getParameterTypes();
        List<InputValueDefinition> arguments = field.getInputValueDefinitions();
        int required = arguments.size() + (sourceClass != null ? 1 : 0);
        long current = parameters.stream()
                .filter(it -> !javaTypes.erasedName(it.asType()).equals(DATA_FETCHING_ENVIRONMENT))
                .count();
        if (!(required == 0 && parameters.isEmpty()) && required != current) {
            problems.error(coordinate, "wrong number of parameters: " + javaTypes.describe(method) + " takes " + current
                    + " besides DataFetchingEnvironment, the field needs " + required + ": " + method.getSimpleName()
                    + suggestion(field, sourceClass) + ".", field, method);
            return;
        }

        int first = 0;
        if (sourceClass != null) {
            TypeMirror source = parameterTypes.get(0);
            if (!javaTypes.erasedName(source).equals(javaTypes.name(sourceClass))) {
                problems.error(coordinate, "the first parameter of " + javaTypes.describe(method) + " must be the "
                        + "source object, " + javaTypes.name(sourceClass) + ", not " + javaTypes.erasedName(source)
                        + ".", field, parameters.get(0));
                return;
            }
            first = 1;
        }
        for (int i = 0; i < arguments.size(); i++) {
            InputValueDefinition argument = arguments.get(i);
            checkInputType(parameterTypes.get(first + i), argument.getType(), true,
                    coordinate + "(" + argument.getName() + ":)", argument, parameters.get(first + i));
        }
        checkReturnType(memberType.getReturnType(), field.getType(), true, type.getName().equals(subscriptionType),
                coordinate, field, method);
    }

    /**
     * Checks the Java type of a field: the wrappers graphql-java resolves (as the library's {@code ReturnTypes}), lists,
     * built-in scalars, and object types, which it then binds.
     */
    private void checkReturnType(TypeMirror javaType, Type<?> graphQlType, boolean fieldLevel, boolean subscription,
                                 String coordinate, Node<?> node, Element element) {
        TypeMirror type = unwrapReturnType(javaType, graphQlType, fieldLevel, subscription, coordinate, node, element);
        if (type == null) {
            return;
        }
        Type<?> nullable = graphQlType instanceof NonNullType nonNull ? nonNull.getType() : graphQlType;

        if (nullable instanceof ListType listType) {
            if (!javaTypes.isA(type, "java.lang.Iterable") && !javaTypes.isA(type, "java.util.Iterator")) {
                problems.error(coordinate, "the field is a list (" + printType(graphQlType) + "), but "
                        + javaTypes.erasedName(type) + " is not an Iterable.", node, element);
                return;
            }
            TypeMirror elementType = javaTypes.firstTypeArgument(type);
            if (elementType == null) {
                problems.error(coordinate, javaTypes.erasedName(type) + " must declare its element type.", node,
                        element);
                return;
            }
            checkReturnType(elementType, listType.getType(), false, subscription, coordinate, node, element);
            return;
        }

        String typeName = ((TypeName) nullable).getName();
        Set<String> scalarClasses = BUILT_IN_SCALARS.get(typeName);
        if (scalarClasses != null) {
            if (!scalarClasses.contains(javaTypes.erasedName(type))) {
                problems.error(coordinate, "the field is mapped to the incorrect class: " + typeName + " accepts "
                        + new TreeSet<>(scalarClasses) + ", the Java type is " + javaTypes.erasedName(type) + ".",
                        node, element);
            }
            return;
        }

        TypeDefinition<?> definition = registry.getType(typeName).orElse(null);
        if (definition instanceof ObjectTypeDefinition object) {
            bindObjectType(object, type, coordinate, node, element);
        } else if (definition instanceof EnumTypeDefinition enumType) {
            checkEnumType(enumType, type, false, coordinate, node, element);
        } else if (definition instanceof UnionTypeDefinition || definition instanceof InterfaceTypeDefinition) {
            checkAbstractType(definition, type, coordinate, node, element);
        } else if (definition instanceof ScalarTypeDefinition) {
            skip(typeName, "the Java classes of a custom scalar are runtime beans (GraphQLScalarMapping)", false);
        }
    }

    /**
     * Removes the wrappers graphql-java resolves around the value of a field, in its order, as the library's
     * {@code ReturnTypes.unwrap}: {@code CompletionStage} (field level only), {@code Publisher} (required on fields of the
     * subscription type, allowed nowhere else), {@code DataFetcherResult}, {@code Optional} (nullable types only), each
     * at most once.
     *
     * @return the unwrapped type, or {@code null} after an error
     */
    private TypeMirror unwrapReturnType(TypeMirror javaType, Type<?> graphQlType, boolean fieldLevel,
                                        boolean subscription, String coordinate, Node<?> node, Element element) {
        TypeMirror result = javaType;
        String outer = null;

        if (javaTypes.isA(result, COMPLETION_STAGE)) {
            if (!fieldLevel) {
                problems.error(coordinate, "CompletionStage is not allowed inside a list: graphql-java does not wait "
                        + "for futures inside a list. Return a CompletionStage of the list instead.", node, element);
                return null;
            }
            outer = COMPLETION_STAGE;
            result = typeArgument(result, COMPLETION_STAGE, coordinate, node, element);
            if (result == null) {
                return null;
            }
        }

        boolean publisher = javaTypes.isA(result, PUBLISHER);
        if (fieldLevel && subscription) {
            if (!publisher) {
                problems.error(coordinate, "a field of the subscription type must return " + PUBLISHER
                        + " (optionally inside a CompletionStage), found " + javaTypes.erasedName(result) + ".", node,
                        element);
                return null;
            }
            outer = PUBLISHER;
            result = typeArgument(result, PUBLISHER, coordinate, node, element);
            if (result == null) {
                return null;
            }
        } else if (publisher) {
            problems.error(coordinate, PUBLISHER + " is only allowed on fields of the subscription type.", node,
                    element);
            return null;
        }

        if (javaTypes.isA(result, DATA_FETCHER_RESULT)) {
            outer = DATA_FETCHER_RESULT;
            result = typeArgument(result, DATA_FETCHER_RESULT, coordinate, node, element);
            if (result == null) {
                return null;
            }
        }

        if (javaTypes.is(result, OPTIONAL)) {
            if (graphQlType instanceof NonNullType) {
                problems.error(coordinate, OPTIONAL + " is only allowed for a nullable GraphQL type, the field type is "
                        + printType(graphQlType) + ".", node, element);
                return null;
            }
            outer = OPTIONAL;
            result = typeArgument(result, OPTIONAL, coordinate, node, element);
            if (result == null) {
                return null;
            }
        }

        if (outer != null) {
            for (String wrapper : RETURN_TYPE_WRAPPERS) {
                if (javaTypes.isA(result, wrapper)) {
                    problems.error(coordinate, outer + " cannot wrap " + wrapper + ". Supported wrappers, outermost "
                            + "first: CompletionStage (field level only), Publisher (subscription fields only), "
                            + "DataFetcherResult, Optional (nullable types only), each at most once.", node, element);
                    return null;
                }
            }
        }
        return result;
    }

    private TypeMirror typeArgument(TypeMirror type, String wrapper, String coordinate, Node<?> node, Element element) {
        TypeMirror result = javaTypes.firstTypeArgument(type);
        if (result == null) {
            problems.error(coordinate, wrapper + " must declare the type it wraps (a raw "
                    + javaTypes.erasedName(type) + " is not allowed).", node, element);
        }
        return result;
    }

    /**
     * Checks the Java type of an argument or an input field: {@code ArgumentValue}, lists and built-in scalars, and a
     * primitive for a nullable type (always an error for a custom scalar, in strict mode for a built-in one).
     */
    private void checkInputType(TypeMirror javaType, Type<?> graphQlType, boolean fieldLevel, String coordinate,
                                Node<?> node, Element element) {
        TypeMirror type = javaType;
        if (javaTypes.is(type, ARGUMENT_VALUE)) {
            String problem = !options.detectOmission() ? "needs " + ProcessorOptions.DETECT_OMISSION + "=true (as at startup "
                    + "micronaut.graphql.tools.input.detect-omission=true)."
                    : !fieldLevel ? "is not allowed inside a list: a list element cannot be omitted."
                    : graphQlType instanceof NonNullType ? "is only allowed for a nullable GraphQL type, the type is "
                    + printType(graphQlType) + "." : null;
            type = javaTypes.firstTypeArgument(type);
            if (problem == null && type == null) {
                problem = "must declare the type it wraps.";
            } else if (problem == null && javaTypes.is(type, ARGUMENT_VALUE)) {
                problem = "cannot wrap " + ARGUMENT_VALUE + ".";
            }
            if (problem != null) {
                problems.error(coordinate, ARGUMENT_VALUE + " " + problem, node, element);
                return;
            }
        }
        boolean nonNull = graphQlType instanceof NonNullType;
        Type<?> nullable = nonNull ? ((NonNullType) graphQlType).getType() : graphQlType;

        if (nullable instanceof ListType listType) {
            if (!javaTypes.isA(type, "java.lang.Iterable")) {
                problems.error(coordinate, "the argument or input field is a list (" + printType(graphQlType)
                        + "), but " + javaTypes.erasedName(type) + " is not an Iterable.", node, element);
                return;
            }
            TypeMirror elementType = javaTypes.firstTypeArgument(type);
            if (elementType == null) {
                problems.error(coordinate, javaTypes.erasedName(type) + " must declare its element type.", node,
                        element);
                return;
            }
            checkInputType(elementType, listType.getType(), false, coordinate, node, element);
            return;
        }

        String typeName = ((TypeName) nullable).getName();
        TypeDefinition<?> definition = registry.getType(typeName).orElse(null);
        Set<String> scalarClasses = BUILT_IN_SCALARS.get(typeName);
        if (definition instanceof InputObjectTypeDefinition input) {
            bindInputType(input, type, coordinate, node, element);
        } else if (definition instanceof EnumTypeDefinition enumType) {
            checkEnumType(enumType, type, true, coordinate, node, element);
        } else if (scalarClasses != null) {
            String javaName = javaTypes.erasedName(type);
            if (!scalarClasses.contains(javaName)) {
                problems.error(coordinate, "the argument or input field is mapped to the incorrect class: " + typeName
                        + " accepts " + new TreeSet<>(scalarClasses) + ", the Java type is " + javaName + ".", node,
                        element);
            } else if (type.getKind().isPrimitive() && !nonNull && options.strict()) {
                problems.error(coordinate, "the Java type is the primitive " + javaName + ", but the GraphQL type "
                        + typeName + " is nullable, so null cannot be passed (strict mode). Use its wrapper class or "
                        + "make the GraphQL type non-null.", node, element);
            }
        } else if (definition instanceof ScalarTypeDefinition) {
            if (type.getKind().isPrimitive() && !nonNull) {
                problems.error(coordinate, "the Java type is the primitive " + type + ", but the GraphQL type "
                        + typeName + " is nullable, so null cannot be passed. Use its wrapper class or make the GraphQL "
                        + "type non-null.", node, element);
            }
        }
    }

    // ---------------------------------------------------------------- after binding

    /**
     * Every method of a resolver bean that can bind a field must bind one: a strict-mode error, and an
     * {@code !unbound} line of the manifest without strict mode.
     */
    private void checkUnboundMethods() {
        for (Bean bean : beans.beans()) {
            for (Method method : bean.methods()) {
                if (!bean.isCandidate(method) || method.objectMethod() || boundMethods.containsKey(method)
                        || shadowedMethods.containsKey(method) || bean.unassigned().contains(method)) {
                    continue;
                }
                if (!bean.root() && !reachedModelClass(bean, method)) {
                    undecidedMembers.add(beanMethodMember(method));
                    continue;
                }
                manifest.diagnostic("unbound", beanMethodMember(method));
                if (!options.strict()) {
                    continue;
                }
                problems.error(javaTypes.describe(method.element()), "the resolver method binds no schema field "
                        + "(strict mode). Remove it, make it non-public, or annotate the methods of the bean that bind "
                        + "fields with @GraphQLSchemaCoordinate.", null, method.element());
            }
        }
    }

    /**
     * @return whether the class the type resolver method resolves was bound, or the checks covered the whole schema;
     * otherwise its type may be bound through a part the checks skip, and the method may bind there
     */
    private boolean reachedModelClass(Bean bean, Method method) {
        if (!objectTypesSkipped) {
            return true;
        }
        return bean.methodsByModelClass().entrySet().stream()
                .anyMatch(it -> it.getValue().contains(method) && traversedModelClasses.contains(it.getKey()));
    }

    /**
     * Strict mode: a root resolver method without a coordinate that binds the field of its name on several root
     * operation types.
     */
    private void checkSharedRootMethods() {
        if (!options.strict()) {
            return;
        }
        boundMethods.forEach((method, coordinates) -> {
            if (method.bean().root() && method.coordinate() == null && coordinates.size() > 1) {
                problems.error(javaTypes.describe(method.element()), "the root resolver method binds fields of several "
                        + "root operation types (" + String.join(", ", coordinates) + ") (strict mode). Give each "
                        + "operation type its own method with @GraphQLSchemaCoordinate.", null, method.element());
            }
        });
    }

    /**
     * A method with a coordinate must bind exactly the field it names.
     */
    private void checkSchemaCoordinateBindings(SchemaDefinition schemaDefinition) {
        Set<String> reachable = new SchemaChecks(registry, options, problems).reachableTypes(schemaDefinition);
        for (Bean bean : beans.beans()) {
            for (Method method : bean.methods()) {
                String coordinate = method.coordinate();
                if (coordinate == null || coordinateProblem(bean, method) != null) {
                    continue;
                }
                String typeName = coordinate.substring(0, coordinate.indexOf('.'));
                Set<String> bound = boundMethods.getOrDefault(method, Set.of());
                if (bound.equals(Set.of(coordinate))) {
                    continue;
                }
                if (bound.isEmpty() && !reachable.contains(typeName)) {
                    if (options.strict() && options.allowUnreachable().contains(typeName)) {
                        problems.error(coordinate, "@GraphQLSchemaCoordinate on " + javaTypes.describe(method.element())
                                + ": the type is unreachable and allowed, so the method binds nothing (strict mode).",
                                null, method.element());
                    }
                    continue;
                }
                if (bound.isEmpty() && (!processedTypes.containsKey(typeName) && objectTypesSkipped
                        || shadowedMethods.containsKey(method))) {
                    continue;
                }
                Set<String> others = new TreeSet<>(bound);
                others.remove(coordinate);
                problems.error(coordinate, "@GraphQLSchemaCoordinate on " + javaTypes.describe(method.element()) + ": "
                        + (others.isEmpty() ? "the method does not bind " + coordinate + describeBinding(typeName)
                        : "the method is " + (bound.contains(coordinate) ? "also " : "") + "bound to "
                        + String.join(", ", others)) + ".", null, method.element());
            }
        }
    }

    private String describeBinding(String typeName) {
        TypeElement javaType = processedTypes.get(typeName);
        return javaType == null ? "" : " (" + typeName + " is mapped to " + javaTypes.name(javaType) + ")";
    }

    // ---------------------------------------------------------------- manifest

    /**
     * What the manifest leaves out, because the startup decides it with runtime beans, as the lines of
     * {@value BindingManifestLines#OMITTED_PATH}: the line kinds the processor never writes, every type whose lines it
     * leaves out (the types it skipped, and every type that only the skipped parts reach: members of unions and
     * interfaces and what they reach), fields, and resolver methods whose {@code !unbound} line it cannot decide.
     *
     * @return the lines, sorted within each kind
     */
    List<String> omissions() {
        Set<String> types = new TreeSet<>(omittedTypes);
        Deque<String> pending = new ArrayDeque<>(omissionRoots);
        pending.addAll(omittedTypes);
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String name = pending.poll();
            if (!visited.add(name)) {
                continue;
            }
            if (!processedTypes.containsKey(name) && !operationTypes.contains(name) && !manifest.hasType(name)
                    && registry.getType(name).filter(it -> !(it instanceof ScalarTypeDefinition)).isPresent()) {
                // scalar lines are left out as a kind
                types.add(name);
            }
            if (processedTypes.containsKey(name) && !omittedTypes.contains(name)) {
                // bound here: what it reaches was bound or omitted on its own
                continue;
            }
            pending.addAll(reachedTypes(name));
        }
        List<String> result = new ArrayList<>(List.of("kind resolves", "kind scalar", "kind unusedDictionary",
                // directive wirings are runtime beans
                "kind unwiredDirective"));
        types.forEach(it -> result.add("type " + it));
        omittedFields.forEach(it -> result.add("field " + it));
        undecidedMembers.forEach(it -> result.add("unbound " + it));
        return result;
    }

    private List<String> memberTypes(TypeDefinition<?> type) {
        if (type instanceof UnionTypeDefinition union) {
            return union.getMemberTypes().stream().map(SchemaChecks::typeName).toList();
        }
        if (type instanceof InterfaceTypeDefinition iface) {
            return registry.getImplementationsOf(iface).stream().map(ObjectTypeDefinition::getName).toList();
        }
        return List.of();
    }

    private void omitField(ObjectTypeDefinition type, FieldDefinition field) {
        omittedFields.add(type.getName() + "." + field.getName());
        omissionRoots.add(SchemaChecks.typeName(field.getType()));
        field.getInputValueDefinitions().forEach(it -> omissionRoots.add(SchemaChecks.typeName(it.getType())));
    }

    /**
     * @return the types the binding of a type reaches: of its fields, arguments and input fields, its members or
     * implementations, and the interfaces it implements
     */
    private List<String> reachedTypes(String name) {
        List<String> result = new ArrayList<>();
        TypeDefinition<?> type = registry.getType(name).orElse(null);
        if (type instanceof ObjectTypeDefinition object) {
            object.getFieldDefinitions().forEach(field -> {
                result.add(SchemaChecks.typeName(field.getType()));
                field.getInputValueDefinitions().forEach(it -> result.add(SchemaChecks.typeName(it.getType())));
            });
            object.getImplements().forEach(it -> result.add(SchemaChecks.typeName(it)));
        } else if (type instanceof InterfaceTypeDefinition || type instanceof UnionTypeDefinition) {
            result.addAll(memberTypes(type));
        } else if (type instanceof InputObjectTypeDefinition input) {
            input.getInputValueDefinitions().forEach(it -> result.add(SchemaChecks.typeName(it.getType())));
        }
        return result;
    }

    /**
     * An interface that no field returns but that a bound object type implements, as the library's
     * {@code wireInterfaceTypeResolvers}. The object types bound through the {@code SchemaMappingDictionary} are not
     * known here, so an interface only they implement has no line.
     */
    private void recordImplementedInterfaces() {
        registry.getTypes(InterfaceTypeDefinition.class).stream()
                .filter(it -> !processedTypes.containsKey(it.getName()))
                .filter(it -> registry.getImplementationsOf(it).stream()
                        .anyMatch(implementation -> processedTypes.containsKey(implementation.getName())))
                .forEach(it -> manifest.type(it.getName(), "interface", null));
    }

    private void recordField(ObjectTypeDefinition type, FieldDefinition field, String kind, String member,
                             TypeMirror declaredType, boolean subscription) {
        manifest.field(type, field, kind, member, typeNames.render(declaredType),
                typeNames.converted(declaredType, subscription));
    }

    /**
     * @param property a property
     * @return its type as its getter declares it (or its setter, without a getter), as reflection gives it to the
     * library's manifest: a type variable of a generic superclass stays a type variable
     */
    private static TypeMirror declaredType(Property property) {
        ExecutableElement accessor = property.accessor();
        return accessor.getParameters().isEmpty() ? accessor.getReturnType() : accessor.getParameters().get(0).asType();
    }

    /**
     * @param method a method of a resolver bean
     * @return {@code Bean#method(generic parameter types)}: the class is the bean, also for a method it inherits, as the
     * library's manifest names a resolver method (Micronaut's executable method of the bean, AOP proxy unwrapped); the
     * parameter types are those of the declaration, as reflection gives them
     */
    private String beanMethodMember(Method method) {
        return method.bean().name() + "#" + method.element().getSimpleName() + typeNames.parameters(method.element());
    }

    /**
     * @param method a {@code @GraphQLField} method of a model
     * @return {@code Class#method(generic parameter types)}, the class being the one the method is written in, as the
     * library's manifest names a method of an introspection
     */
    private String methodMember(ExecutableElement method) {
        return javaTypes.name((TypeElement) method.getEnclosingElement()) + "#" + method.getSimpleName()
                + typeNames.parameters(method);
    }

    /**
     * @param property     a property
     * @param introspected the introspected type
     * @return {@code Class#getter()}, the class being the one that declares the getter (or record accessor); for a
     * property without one, {@code IntrospectedType#property}, as the library's manifest names a property
     */
    private String propertyMember(Property property, TypeElement introspected) {
        ExecutableElement accessor = property.accessor();
        if (accessor.getParameters().isEmpty()) {
            return javaTypes.name((TypeElement) accessor.getEnclosingElement()) + "#" + accessor.getSimpleName() + "()";
        }
        return javaTypes.name(introspected) + "#" + property.name();
    }

    // ---------------------------------------------------------------- messages

    private String describeMember(Optional<Property> property, List<ExecutableElement> fieldMethods) {
        return property.map(it -> "the property " + javaTypes.describe(it.accessor()))
                .orElseGet(() -> "the method " + javaTypes.describe(fieldMethods.get(0)));
    }

    private String describeMethods(List<Method> methods) {
        return methods.stream().map(it -> javaTypes.describe(it.element())).collect(Collectors.joining(", "));
    }

    private String suggestion(FieldDefinition field, TypeElement sourceClass) {
        List<String> parameters = new ArrayList<>();
        if (sourceClass != null) {
            parameters.add(javaTypes.name(sourceClass) + " source");
        }
        field.getInputValueDefinitions().forEach(it -> parameters.add(SchemaChecks.typeName(it.getType()) + " "
                + it.getName()));
        return "(" + String.join(", ", parameters) + ")";
    }

    /**
     * @param type a type reference
     * @return the type as the schema language writes it
     */
    static String printType(Type<?> type) {
        if (type instanceof NonNullType nonNull) {
            return printType(nonNull.getType()) + "!";
        }
        if (type instanceof ListType list) {
            return "[" + printType(list.getType()) + "]";
        }
        return ((TypeName) type).getName();
    }

}
