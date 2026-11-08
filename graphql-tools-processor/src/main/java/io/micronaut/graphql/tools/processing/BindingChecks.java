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
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayList;
import java.util.Collection;
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
    private final Set<String> traversedModelClasses = new TreeSet<>();
    private final Map<Method, Set<String>> boundMethods = new IdentityHashMap<>();
    private final Map<Method, Boolean> shadowedMethods = new IdentityHashMap<>();
    private final List<Binding> bindings = new ArrayList<>();
    private final Map<String, String> skipped = new TreeMap<>();
    private boolean objectTypesSkipped;

    /**
     * @param registry  the schema
     * @param options   the options
     * @param problems  where errors are reported
     * @param javaTypes the type helpers
     * @param types     the types of the compilation
     */
    BindingChecks(TypeDefinitionRegistry registry, ProcessorOptions options, Problems problems, JavaTypes javaTypes,
                  Collection<TypeElement> types) {
        this.registry = registry;
        this.options = options;
        this.problems = problems;
        this.javaTypes = javaTypes;
        this.types = types;
        this.beans = new ResolverBeans(javaTypes, types);
        this.models = new ModelMembers(javaTypes, types);
    }

    /**
     * @return what each checked field is bound to
     */
    List<Binding> bindings() {
        return bindings;
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
                for (FieldDefinition field : type.getFieldDefinitions()) {
                    bindRootField(type, field);
                }
            });
        }

        checkUnboundMethods();
        checkSharedRootMethods();
        checkSchemaCoordinateBindings(schemaDefinition);
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
        bindings.add(new Binding(coordinate, Binding.ROOT, method.element(), method.element().getReturnType(),
                type.getName().equals(subscriptionType)));
        checkMethod(method.element(), null, type, field);
    }

    private void bindObjectType(ObjectTypeDefinition type, TypeElement javaType) {
        if (processedTypes.containsKey(type.getName())) {
            return;
        }
        processedTypes.put(type.getName(), javaType);

        Optional<TypeElement> introspected = models.introspectedType(javaType);
        if (introspected.isEmpty()) {
            skip(type.getName(), "the Java interface " + javaTypes.name(javaType) + " has no introspection of its own "
                    + "and not exactly one implementation in this compilation", true);
            return;
        }
        TypeElement sourceClass = models.sourceClass(introspected.get());
        traversedModelClasses.add(javaTypes.name(sourceClass));

        for (FieldDefinition field : type.getFieldDefinitions()) {
            bindField(type, field, introspected.get(), sourceClass);
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
                if (directive == null && options.precedence() == ProcessorOptions.Precedence.PROPERTY_FIRST) {
                    resolvers.forEach(it -> shadowedMethods.put(it, true));
                    bindMember(type, field, property, fieldMethods);
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
                return;
            }
            if (directive != null) {
                problems.error(coordinate, "the field carries @" + directive + ", which needs a type resolver method, "
                        + "but only " + describeMember(property, fieldMethods) + " can bind it. Add a type resolver "
                        + "method for " + javaTypes.name(sourceClass) + ".", field, memberElement);
                return;
            }
            bindMember(type, field, property, fieldMethods);
            return;
        }

        if (resolvers.isEmpty()) {
            if (declaredByInterface && hasInterfaceResolver(introspected, field.getName())) {
                skip(coordinate, "a type resolver of a Java interface of " + javaTypes.name(introspected)
                        + " has a method " + field.getName() + " and may bind it", true);
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

    private void bindMember(ObjectTypeDefinition type, FieldDefinition field, Optional<Property> property,
                            List<ExecutableElement> fieldMethods) {
        String coordinate = type.getName() + "." + field.getName();
        if (property.isPresent()) {
            ExecutableElement accessor = property.get().accessor();
            bindings.add(new Binding(coordinate, Binding.PROPERTY, accessor, property.get().type(), false));
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
        bindings.add(new Binding(coordinate, Binding.PROPERTY, method, method.getReturnType(), false));
        checkMethod(method, null, type, field);
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
        bindings.add(new Binding(coordinate, Binding.RESOLVER, method.element(), method.element().getReturnType(),
                false));
        checkMethod(method.element(), sourceClass, type, field);
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
     * the order of the schema.
     */
    private void checkMethod(ExecutableElement method, TypeElement sourceClass, ObjectTypeDefinition type,
                             FieldDefinition field) {
        String coordinate = type.getName() + "." + field.getName();
        List<? extends VariableElement> parameters = method.getParameters();
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

        List<VariableElement> remaining = new ArrayList<>(parameters);
        if (sourceClass != null) {
            VariableElement source = remaining.remove(0);
            if (!javaTypes.erasedName(source.asType()).equals(javaTypes.name(sourceClass))) {
                problems.error(coordinate, "the first parameter of " + javaTypes.describe(method) + " must be the "
                        + "source object, " + javaTypes.name(sourceClass) + ", not "
                        + javaTypes.erasedName(source.asType()) + ".", field, source);
                return;
            }
        }
        if (!remaining.isEmpty() && javaTypes.erasedName(remaining.get(remaining.size() - 1).asType())
                .equals(DATA_FETCHING_ENVIRONMENT)) {
            remaining.remove(remaining.size() - 1);
        }
        for (int i = 0; i < arguments.size(); i++) {
            InputValueDefinition argument = arguments.get(i);
            checkInputType(remaining.get(i).asType(), argument.getType(), true,
                    coordinate + "(" + argument.getName() + ":)", argument, remaining.get(i));
        }
        checkReturnType(method.getReturnType(), field.getType(), true, type.getName().equals(subscriptionType),
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
            TypeElement javaClass = javaTypes.element(type);
            if (javaClass != null) {
                bindObjectType(object, javaClass);
            }
        } else if (definition instanceof EnumTypeDefinition || definition instanceof UnionTypeDefinition
                || definition instanceof InterfaceTypeDefinition) {
            skip(typeName, SchemaChecks.kind(definition) + " types are not checked yet", true);
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
        if (definition instanceof InputObjectTypeDefinition || definition instanceof EnumTypeDefinition) {
            skip(typeName, SchemaChecks.kind(definition) + " types are not checked yet", false);
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
     * Strict mode: every method of a resolver bean that can bind a field must bind one.
     */
    private void checkUnboundMethods() {
        if (!options.strict()) {
            return;
        }
        for (Bean bean : beans.beans()) {
            for (Method method : bean.methods()) {
                if (!bean.isCandidate(method) || method.objectMethod() || boundMethods.containsKey(method)
                        || shadowedMethods.containsKey(method) || bean.unassigned().contains(method)) {
                    continue;
                }
                if (!bean.root() && !reachedModelClass(bean, method)) {
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

    /**
     * What a field is bound to.
     *
     * @param coordinate   the field
     * @param kind         {@link #ROOT}, {@link #RESOLVER} or {@link #PROPERTY}
     * @param member       the method or property accessor
     * @param declaredType the declared Java type of the value
     * @param subscription whether the field belongs to the subscription type
     */
    record Binding(String coordinate, String kind, ExecutableElement member, TypeMirror declaredType,
                   boolean subscription) {

        static final String ROOT = "root";
        static final String RESOLVER = "resolver";
        static final String PROPERTY = "property";

    }

}
