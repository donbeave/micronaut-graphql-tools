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
import io.micronaut.graphql.tools.binding.internal.rules.PrecedenceRules;
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

final class BindingChecks {

    static final String DATA_FETCHING_ENVIRONMENT = "graphql.schema.DataFetchingEnvironment";
    static final String COMPLETION_STAGE = "java.util.concurrent.CompletionStage";
    static final String PUBLISHER = "org.reactivestreams.Publisher";
    static final String DATA_FETCHER_RESULT = "graphql.execution.DataFetcherResult";
    static final String OPTIONAL = "java.util.Optional";
    static final String ARGUMENT_VALUE = "io.micronaut.graphql.tools.ArgumentValue";

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

    static final Map<String, String> PRIMITIVE_OPTIONALS = Map.of(
            "java.util.OptionalInt", "java.lang.Integer", "java.util.OptionalLong", "java.lang.Long",
            "java.util.OptionalDouble", "java.lang.Double");

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
    private final Set<String> omittedTypes = new TreeSet<>();
    private final Set<String> omissionRoots = new TreeSet<>();
    private final Set<String> omittedFields = new TreeSet<>();
    private final Set<String> undecidedMembers = new TreeSet<>();

    BindingChecks(TypeDefinitionRegistry registry, ProcessorOptions options, Problems problems, JavaTypes javaTypes,
                  Collection<TypeElement> types, BindingManifestLines manifest) {
        this.registry = registry;
        this.options = options;
        this.problems = problems;
        this.javaTypes = javaTypes;
        this.types = types;
        this.beans = new ResolverBeans(javaTypes, types, options.resolverAccessorPrefixes());
        this.models = new ModelMembers(javaTypes, types);
        this.manifest = manifest;
        this.typeNames = new TypeNames(javaTypes);
    }

    Map<String, String> skipped() {
        return skipped;
    }

    private void skip(String coordinate, String reason, boolean mayReachObjectTypes) {
        skipped.merge(coordinate, reason, (first, second) -> first.equals(second) ? first : first + "; " + second);
        objectTypesSkipped |= mayReachObjectTypes;
    }

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
            Method reported = candidates.stream().filter(it -> types.contains(it.bean().type())).findFirst()
                    .orElse(candidates.get(0));
            problems.error(coordinate, "several root resolver methods can bind the field: "
                    + candidates.stream().map(it -> javaTypes.describe(it.element())).collect(Collectors.joining(", "))
                    + ". Keep one, or scope each to its root operation type with @GraphQLSchemaCoordinate.",
                    field, reported.element());
            candidates.forEach(it -> shadowedMethods.put(it, true));
            return;
        }
        Method method = candidates.get(0);
        bound(method, coordinate);
        recordField(type, field, "root", beanMethodMember(method), resolverReturnType(method),
                type.getName().equals(subscriptionType));
        checkMethod(method.element(), method.bean().type(), null, type, field);
    }

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

    private boolean isChecked(TypeElement javaClass) {
        return types.contains(javaClass) || javaClass.getQualifiedName().toString().startsWith("java.")
                || javaTypes.isInClassOutput(javaClass);
    }

    private static boolean isBuiltInJavaClass(String javaName) {
        return BUILT_IN_SCALARS.values().stream().anyMatch(it -> it.contains(javaName));
    }

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
                    property.name(), typeNames.render(property.type()),
                    typeNames.converted(property.type(), false, false));
            if (javaTypes.is(property.type(), ARGUMENT_VALUE)) {
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
        List<Property> properties = models.properties(introspected, field.getName());
        List<ExecutableElement> fieldMethods = models.fieldMethods(introspected, field.getName());

        if (properties.size() + fieldMethods.size() > 1) {
            List<String> members = new ArrayList<>();
            properties.forEach(it -> members.add(javaTypes.describe(it.accessor())));
            fieldMethods.forEach(it -> members.add(javaTypes.describe(it)));
            problems.error(coordinate, "several members of " + javaTypes.name(introspected) + " can bind the field: "
                    + String.join(", ", members) + ".", field,
                    fieldMethods.isEmpty() ? properties.get(0).accessor() : fieldMethods.get(0));
            return;
        }
        Optional<Property> property = properties.stream().findFirst();

        boolean declaredByInterface = isDeclaredByInterface(type, field.getName());
        List<Method> resolvers = typeResolverMethods(sourceClass, coordinate, field.getName());
        boolean member = property.isPresent() || !fieldMethods.isEmpty();
        String directive = resolverDirective(field);

        if (member) {
            Element memberElement = property.<Element>map(Property::accessor).orElseGet(() -> fieldMethods.get(0));
            if (!resolvers.isEmpty()) {
                boolean intent = directive != null || resolvers.stream().anyMatch(it -> it.coordinate() != null);
                PrecedenceRules.Outcome outcome = PrecedenceRules.decide(
                        PrecedenceRules.Setting.valueOf(options.precedence().name()), intent);
                if (outcome == PrecedenceRules.Outcome.FAIL) {
                    problems.error(coordinate, "ambiguous binding: " + describeMember(property, fieldMethods)
                            + " and the type resolver method " + describeMethods(resolvers) + " can both bind the "
                            + "field. Remove one, mark the field with a directive listed in "
                            + ProcessorOptions.RESOLVER_DIRECTIVES + ", declare @GraphQLSchemaCoordinate on the "
                            + "resolver method, annotate the member with @GraphQLIgnore, or set "
                            + ProcessorOptions.PRECEDENCE + " (strict does not change it).",
                            field, resolvers.get(0).element());
                    resolvers.forEach(it -> shadowedMethods.put(it, true));
                    return;
                }
                if (!intent) {
                    manifest.diagnostic("ambiguous", coordinate, Stream.concat(
                            Stream.of(property.map(it -> propertyMember(it, introspected))
                                    .orElseGet(() -> methodMember(fieldMethods.get(0), introspected))),
                            resolvers.stream().map(this::beanMethodMember).sorted())
                            .collect(Collectors.joining(" | ")));
                }
                if (outcome == PrecedenceRules.Outcome.MEMBER) {
                    resolvers.forEach(it -> shadowedMethods.put(it, true));
                    bindMember(type, field, introspected, property, fieldMethods);
                    return;
                }
                bindResolver(type, field, sourceClass, resolvers);
                return;
            }
            if (declaredByInterface && (directive != null
                    || options.precedence() == ProcessorOptions.Precedence.RESOLVER_FIRST
                    || hasInterfaceResolver(introspected, field.getName(), true))
                    && hasInterfaceResolver(introspected, field.getName(), false)) {
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
            if (declaredByInterface && hasInterfaceResolver(introspected, field.getName(), false)) {
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
                    property.get().type(), false);
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
        recordField(type, field, "property", methodMember(method, introspected),
                javaTypes.memberType(introspected, method).getReturnType(), false);
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
        recordField(type, field, "resolver", beanMethodMember(method), resolverReturnType(method), false);
        checkMethod(method.element(), method.bean().type(), sourceClass, type, field);
    }

    private List<Method> typeResolverMethods(TypeElement sourceClass, String coordinate, String fieldName) {
        List<Method> result = new ArrayList<>();
        for (Bean bean : beans.typeBeans(sourceClass)) {
            result.addAll(bean.find(bean.methodsByModelClass().get(javaTypes.name(sourceClass)), fieldName, coordinate));
        }
        return result;
    }

    private boolean hasInterfaceResolver(TypeElement introspected, String fieldName, boolean declaredCoordinate) {
        for (Bean bean : beans.beans()) {
            for (TypeElement modelClass : bean.contracts().values()) {
                if (modelClass.getKind() == ElementKind.INTERFACE && !modelClass.equals(introspected)
                        && javaTypes.isA(introspected.asType(), modelClass.getQualifiedName().toString())
                        && bean.methodsByModelClass().getOrDefault(javaTypes.name(modelClass), List.of()).stream()
                        .anyMatch(it -> bean.mayBind(it, fieldName, declaredCoordinate))) {
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

        String primitiveOptional = PRIMITIVE_OPTIONALS.get(javaTypes.erasedName(result));
        if (primitiveOptional != null) {
            if (graphQlType instanceof NonNullType) {
                problems.error(coordinate, javaTypes.erasedName(result) + " is only allowed for a nullable GraphQL "
                        + "type, the field type is " + printType(graphQlType) + ".", node, element);
                return null;
            }
            if (OPTIONAL.equals(outer)) {
                problems.error(coordinate, OPTIONAL + " cannot wrap " + javaTypes.erasedName(result) + ".", node,
                        element);
                return null;
            }
            result = javaTypes.classType(primitiveOptional);
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

    private void checkInputType(TypeMirror javaType, Type<?> graphQlType, boolean fieldLevel, String coordinate,
                                Node<?> node, Element element) {
        TypeMirror type = javaType;
        if (javaTypes.is(type, ARGUMENT_VALUE)) {
            String problem = !fieldLevel ? "is not allowed inside a list: a list element cannot be omitted."
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

    private boolean reachedModelClass(Bean bean, Method method) {
        if (!objectTypesSkipped) {
            return true;
        }
        return bean.methodsByModelClass().entrySet().stream()
                .anyMatch(it -> it.getValue().contains(method) && traversedModelClasses.contains(it.getKey()));
    }

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

    List<String> accessors() {
        Set<String> lines = new TreeSet<>();
        for (TypeElement type : types) {
            if (!models.isIntrospected(type) || models.usesFieldAccess(type)) {
                continue;
            }
            String typeName = javaTypes.name(type);
            models.properties(type).forEach((name, property) ->
                    lines.add(typeName + "#" + name + " → " + propertyMember(property, type)));
            Map<String, List<ExecutableElement>> byArity = new TreeMap<>();
            models.fieldMethods(type).forEach(it -> byArity.computeIfAbsent(
                    it.getSimpleName() + "/" + it.getParameters().size(), key -> new ArrayList<>()).add(it));
            byArity.forEach((key, methods) -> {
                if (methods.size() == 1) {
                    lines.add(typeName + "#" + key + " → " + methodMember(methods.get(0), type));
                }
            });
        }
        return List.copyOf(lines);
    }

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
                types.add(name);
            }
            if (processedTypes.containsKey(name) && !omittedTypes.contains(name)) {
                continue;
            }
            pending.addAll(reachedTypes(name));
        }
        List<String> result = new ArrayList<>(List.of("kind resolves", "kind scalar", "kind unusedDictionary",
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
                typeNames.converted(declaredType, subscription, true));
    }

    private TypeMirror resolverReturnType(Method method) {
        return javaTypes.memberType(method.bean().type(), method.element()).getReturnType();
    }

    private String beanMethodMember(Method method) {
        return method.bean().name() + "#" + method.element().getSimpleName() + typeNames.parameters(method.bean().type(), method.element());
    }

    private String methodMember(ExecutableElement method, TypeElement introspected) {
        return javaTypes.name((TypeElement) method.getEnclosingElement()) + "#" + method.getSimpleName()
                + typeNames.parameters(introspected, method);
    }

    private String propertyMember(Property property, TypeElement introspected) {
        ExecutableElement accessor = property.accessor();
        if (accessor.getParameters().isEmpty()) {
            return javaTypes.name((TypeElement) accessor.getEnclosingElement()) + "#" + accessor.getSimpleName() + "()";
        }
        return javaTypes.name(introspected) + "#" + property.name();
    }

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
