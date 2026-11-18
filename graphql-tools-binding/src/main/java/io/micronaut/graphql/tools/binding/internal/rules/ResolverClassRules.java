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
package io.micronaut.graphql.tools.binding.internal.rules;

import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames;
import io.micronaut.graphql.tools.binding.internal.model.JClass;
import io.micronaut.graphql.tools.binding.internal.model.JMethod;
import io.micronaut.graphql.tools.binding.internal.model.JParameter;
import io.micronaut.graphql.tools.binding.internal.model.TypeModel;
import io.micronaut.graphql.tools.binding.internal.model.TypeRef;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.CountMismatch;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.DuplicateParameter;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.InvalidSource;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.ParameterWithoutArgument;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.Slot;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.SlotKind;
import io.micronaut.graphql.tools.binding.internal.rules.ClassVerdict.FieldVerdict;
import io.micronaut.graphql.tools.binding.internal.rules.ClassVerdict.MethodVerdict;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaTypes;
import io.micronaut.graphql.tools.binding.internal.schema.WrapperRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The rule bodies for one resolver method. Both adapters call them.
 */
@Internal
public final class ResolverClassRules {

    private static final String DATA_FETCHING_ENVIRONMENT = "graphql.schema.DataFetchingEnvironment";
    private static final String COMPLETION_STAGE = "java.util.concurrent.CompletionStage";
    private static final String PUBLISHER = "org.reactivestreams.Publisher";
    private static final String DATA_FETCHER_RESULT = "graphql.execution.DataFetcherResult";
    private static final String OPTIONAL = "java.util.Optional";
    private static final String ITERABLE = "java.lang.Iterable";
    private static final String ITERATOR = "java.util.Iterator";
    // the wrappers a return type may not nest, outermost first
    private static final List<String> WRAPPERS = List.of(COMPLETION_STAGE, PUBLISHER, DATA_FETCHER_RESULT, OPTIONAL);
    // graphql-java's DefaultValueUnboxer unwraps these at runtime, like Optional
    private static final Set<String> PRIMITIVE_OPTIONALS =
            Set.of("java.util.OptionalInt", "java.util.OptionalLong", "java.util.OptionalDouble");
    private static final List<String> LIFECYCLE_ANNOTATIONS = List.of(
            "io.micronaut.runtime.event.annotation.EventListener",
            "io.micronaut.scheduling.annotation.Scheduled",
            // Scheduled is repeatable: Micronaut keeps its metadata under the container
            "io.micronaut.scheduling.annotation.Schedules",
            "jakarta.annotation.PreDestroy");

    private ResolverClassRules() {
    }

    /**
     * Matches the parameters of a resolver method with the arguments of the field it binds: the source parameter first
     * (type resolver methods only), the {@code DataFetchingEnvironment} last, and every other parameter by its name
     * or its {@code @GraphQLArgument} value.
     *
     * @param sourceType      the binary name of the source type, or {@code null} for a root resolver method
     * @param parameters      the parameters of the method, in declaration order
     * @param argumentNames   the names of the arguments of the field, in schema order
     * @return the verdict
     */
    public static ArgumentVerdict arguments(@Nullable String sourceType, List<JParameter> parameters,
                                            List<String> argumentNames) {
        int required = argumentNames.size() + (sourceType == null ? 0 : 1);
        if (required == 0 && parameters.isEmpty()) {
            return new ArgumentVerdict(List.of(), null);
        }

        int found = (int) parameters.stream().filter(it -> !isEnvironment(it.type())).count();
        if (found != required) {
            return new ArgumentVerdict(List.of(), new CountMismatch(found > required, found, required));
        }

        List<Slot> slots = new ArrayList<>();
        int first = 0;
        int end = parameters.size();
        if (sourceType != null) {
            if (!isType(parameters.get(0).type(), sourceType)) {
                return new ArgumentVerdict(slots, new InvalidSource(0));
            }
            slots.add(new Slot(SlotKind.SOURCE, 0, null));
            first = 1;
        }
        boolean environment = end > first && isEnvironment(parameters.get(end - 1).type());
        if (environment) {
            end--;
        }

        List<String> available = List.copyOf(argumentNames);
        Set<String> bound = new HashSet<>();
        for (int i = first; i < end; i++) {
            JParameter parameter = parameters.get(i);
            String declared = parameter.annotations().stringValue(AnnotationNames.ARGUMENT, "value").orElse(null);
            String name = declared != null ? declared : parameter.name();
            if (!available.contains(name)) {
                return new ArgumentVerdict(slots, new ParameterWithoutArgument(i, name, available,
                        declared == null && parameter.nameIsSynthetic()));
            }
            if (!bound.add(name)) {
                return new ArgumentVerdict(slots, new DuplicateParameter(name));
            }
            slots.add(new Slot(SlotKind.ARGUMENT, i, name));
        }
        if (environment) {
            slots.add(new Slot(SlotKind.ENVIRONMENT, end, null));
        }
        return new ArgumentVerdict(slots, null);
    }

    /**
     * @param methodName      the method name
     * @param objectTypeName  the schema type of the field
     * @param sourceType      the binary name of the source type, or {@code null}
     * @param schemaArguments the arguments of the field
     * @return a signature that fits the field, or {@code null} when the field needs no parameter
     */
    @Nullable
    public static String suggestedMethod(String methodName, String objectTypeName, @Nullable String sourceType,
                                         List<SchemaArgument> schemaArguments) {
        List<String> parameters = new ArrayList<>();
        if (sourceType != null) {
            parameters.add(sourceType + " " + Character.toLowerCase(objectTypeName.charAt(0))
                    + objectTypeName.substring(1));
        }
        schemaArguments.forEach(it -> parameters.add(it.typeName() + " " + it.name()));
        return parameters.isEmpty() ? null : methodName + "(" + String.join(", ", parameters) + ")";
    }

    /**
     * @param method a method of a resolver class
     * @return whether {@code @GraphQLIgnore} excludes the method from binding
     */
    public static boolean isIgnored(JMethod method) {
        return method.annotations().has(AnnotationNames.IGNORE);
    }

    /**
     * @param method a method of a resolver class
     * @return whether the method is a lifecycle callback (an event listener, a scheduled method or a pre-destroy
     * method), which never binds a field
     */
    public static boolean isLifecycle(JMethod method) {
        return LIFECYCLE_ANNOTATIONS.stream().anyMatch(method.annotations()::has);
    }

    /**
     * @param method a method of a resolver class
     * @return the coordinate in {@code @GraphQLSchemaCoordinate}, or an empty string when the method has none
     */
    public static String declaredCoordinate(JMethod method) {
        return method.annotations().stringValue(AnnotationNames.SCHEMA_COORDINATE, "value").orElse("");
    }

    /**
     * Whether a method can bind a field of the schema at all. In a class where some method declares a coordinate, only
     * the methods that do are candidates.
     *
     * @param method   a method of a resolver class
     * @param declared the declared coordinate by method signature for the class, or {@code null} when no method of the
     *                 class declares one
     * @return the answer
     */
    public static boolean isCandidate(JMethod method, @Nullable Map<String, String> declared) {
        return !isIgnored(method) && (declared == null || declared.containsKey(method.signature()));
    }

    /**
     * Method-to-field matching: a method binds a field when it has the field's name, is not ignored and, in a class
     * where methods declare coordinates, declares one of the coordinates that the field can have.
     *
     * @param method      a method of a resolver class
     * @param fieldName   the name of the field
     * @param coordinates the coordinates the field answers to ({@code Type.field}, and the same field of an interface
     *                    that the type implements)
     * @param declared    the declared coordinate by method signature for the class, or {@code null} when no method of
     *                    the class declares one
     * @return whether the method binds the field
     */
    public static boolean bindsField(JMethod method, String fieldName, Set<String> coordinates,
                                     @Nullable Map<String, String> declared) {
        if (!method.name().equals(fieldName) || isIgnored(method)) {
            return false;
        }
        if (declared == null) {
            return true;
        }
        String coordinate = declared.get(method.signature());
        return coordinate != null && coordinates.contains(coordinate);
    }

    /**
     * @param methods the methods of a resolver class
     * @return the declared coordinate by method signature, without the ignored methods, or {@code null} when no method
     * declares one
     */
    @Nullable
    public static Map<String, String> declaredCoordinates(List<JMethod> methods) {
        Map<String, String> declared = null;
        for (JMethod method : methods) {
            if (!isIgnored(method) && method.annotations().has(AnnotationNames.SCHEMA_COORDINATE)) {
                if (declared == null) {
                    declared = new HashMap<>();
                }
                declared.put(method.signature(), declaredCoordinate(method));
            }
        }
        return declared;
    }

    /**
     * Checks the wrappers of the return type of a method against the type of the field it binds. They are peeled in a
     * fixed order, each at most once: {@code CompletionStage} (field level only), {@code Publisher} (a subscription
     * field must have it, no other field may), {@code DataFetcherResult} and {@code Optional} or a primitive optional
     * (nullable types only).
     *
     * @param model        the type model
     * @param declared     the declared return type, or the type argument of a list at a deeper level
     * @param fieldType    the type of the field, or the element type of a list at a deeper level
     * @param fieldLevel   whether this is the type of the field itself, not an element of a list
     * @param subscription whether the field belongs to the subscription type
     * @return the verdict
     */
    public static ReturnVerdict returnAdaptation(TypeModel model, TypeRef declared, Type<?> fieldType,
                                                 boolean fieldLevel, boolean subscription) {
        TypeRef result = declared;
        int depth = 0;
        String outer = null;

        if (isAssignableTo(model, result, COMPLETION_STAGE)) {
            if (!fieldLevel) {
                return ReturnVerdict.failed(WrapperRules.completionStageInList());
            }
            outer = COMPLETION_STAGE;
            if (result.typeArguments().isEmpty()) {
                return ReturnVerdict.failed(WrapperRules.missingTypeArgument(COMPLETION_STAGE));
            }
            result = result.typeArguments().get(0);
            depth++;
        }

        boolean publisher = isAssignableTo(model, result, PUBLISHER);

        if (fieldLevel && subscription) {
            if (!publisher) {
                return ReturnVerdict.failed(WrapperRules.subscriptionNeedsPublisher(className(result)));
            }
            outer = PUBLISHER;
            if (result.typeArguments().isEmpty()) {
                return ReturnVerdict.failed(WrapperRules.missingTypeArgument(PUBLISHER));
            }
            result = result.typeArguments().get(0);
            depth++;
        } else if (publisher) {
            return ReturnVerdict.failed(WrapperRules.publisherOutsideSubscription());
        }

        if (isAssignableTo(model, result, DATA_FETCHER_RESULT)) {
            outer = DATA_FETCHER_RESULT;
            if (result.typeArguments().isEmpty()) {
                return ReturnVerdict.failed(WrapperRules.missingTypeArgument(DATA_FETCHER_RESULT));
            }
            result = result.typeArguments().get(0);
            depth++;
        }

        if (isType(result, OPTIONAL)) {
            if (fieldType instanceof NonNullType) {
                return ReturnVerdict.failed(WrapperRules.optionalAtNonNull(SchemaTypes.print(fieldType)));
            }
            outer = OPTIONAL;
            if (result.typeArguments().isEmpty()) {
                return ReturnVerdict.failed(WrapperRules.missingTypeArgument(OPTIONAL));
            }
            result = result.typeArguments().get(0);
            depth++;
        }

        boolean primitiveOptional = !result.array() && PRIMITIVE_OPTIONALS.contains(result.rawName());
        if (primitiveOptional) {
            if (fieldType instanceof NonNullType) {
                return ReturnVerdict.failed(
                        WrapperRules.primitiveOptionalAtNonNull(result.rawName(), SchemaTypes.print(fieldType)));
            }
            if (OPTIONAL.equals(outer)) {
                return ReturnVerdict.failed(WrapperRules.optionalCannotWrapPrimitiveOptional(result.rawName()));
            }
        }

        if (outer != null) {
            for (String wrapper : WRAPPERS) {
                if (isAssignableTo(model, result, wrapper)) {
                    return ReturnVerdict.failed(WrapperRules.cannotWrap(outer, wrapper));
                }
            }
        }

        return new ReturnVerdict(depth, primitiveOptional, null, null);
    }

    /**
     * A primitive at a nullable position cannot hold the {@code null} that the schema allows there. Whether that is a
     * failure at the severity in force is the caller's decision.
     *
     * @param type    the declared type of the parameter or input property (after its wrappers are peeled)
     * @param nonNull whether the schema position is non-null
     * @return whether a primitive is at a nullable position
     */
    public static boolean nullablePrimitive(TypeRef type, boolean nonNull) {
        return type.primitive() && !type.array() && !nonNull;
    }

    /**
     * The per-class rules for one resolver class: which methods can bind a field, which coordinates they bind, and for
     * each the argument plan and the return adaptation. A root resolver binds the root fields that carry the name of a
     * method; a method that declares a coordinate binds that coordinate. The fields of the other types cannot be told
     * from one class (the mapping of a type resolver is a whole-program fact), so only a declared coordinate binds
     * them.
     *
     * @param schema     the schema
     * @param model      the type model
     * @param resolver   the resolver class
     * @param sourceType the binary name of the source type of a type resolver, or {@code null}
     * @return the verdict
     */
    public static ClassVerdict check(SchemaModel schema, TypeModel model, JClass resolver,
                                     @Nullable String sourceType) {
        List<JMethod> bindable = resolver.bindableMethods();
        Map<String, String> declared = declaredCoordinates(bindable);
        boolean root = resolver.annotations().has(AnnotationNames.ROOT_RESOLVER);
        Set<String> rootTypes = schema.rootTypeNames();

        List<MethodVerdict> methods = new ArrayList<>();
        for (JMethod method : bindable) {
            if (!isCandidate(method, declared) || isLifecycle(method)) {
                continue;
            }
            List<String> coordinates = new ArrayList<>();
            if (declared != null) {
                String coordinate = declared.get(method.signature());
                if (bindsFieldNamed(coordinate, method.name())) {
                    coordinates.add(coordinate);
                }
            } else if (root) {
                new TreeSet<>(rootTypes).forEach(type -> {
                    if (field(schema, type, method.name()) != null) {
                        coordinates.add(type + "." + method.name());
                    }
                });
            }

            List<FieldVerdict> fields = new ArrayList<>();
            for (String coordinate : coordinates) {
                int dot = coordinate.indexOf('.');
                if (dot < 0) {
                    continue;
                }
                String typeName = coordinate.substring(0, dot);
                FieldDefinition field = field(schema, typeName, coordinate.substring(dot + 1));
                if (field == null) {
                    continue;
                }
                boolean rootField = rootTypes.contains(typeName);
                List<String> argumentNames = field.getInputValueDefinitions().stream()
                        .map(InputValueDefinition::getName)
                        .toList();
                boolean subscription = schema.subscriptionType().filter(typeName::equals).isPresent();
                fields.add(new FieldVerdict(coordinate,
                        arguments(rootField ? null : sourceType, method.parameters(), argumentNames),
                        fieldReturn(model, method.genericReturnType(), field.getType(), true, subscription)));
            }
            methods.add(new MethodVerdict(method.signature(), coordinates, fields));
        }
        return new ClassVerdict(resolver.name(), methods);
    }

    private static boolean bindsFieldNamed(String coordinate, String methodName) {
        int dot = coordinate.indexOf('.');
        return dot > 0 && coordinate.substring(dot + 1).equals(methodName);
    }

    /**
     * The return adaptation of a method for a field, level by level as the runtime checks it: the wrappers of the
     * declared type, then, where the field type is a list, the wrappers of the element type with {@code fieldLevel}
     * false, down to the innermost element.
     */
    private static ReturnVerdict fieldReturn(TypeModel model, TypeRef declared, Type<?> fieldType, boolean fieldLevel,
                                             boolean subscription) {
        ReturnVerdict verdict = returnAdaptation(model, declared, fieldType, fieldLevel, subscription);
        Type<?> unwrapped = fieldType instanceof NonNullType nonNull ? nonNull.getType() : fieldType;
        if (verdict.failure() != null || verdict.primitiveOptional() || !(unwrapped instanceof ListType list)) {
            return verdict;
        }
        TypeRef adapted = declared;
        for (int i = 0; i < verdict.depth(); i++) {
            adapted = adapted.typeArguments().get(0);
        }
        if (!isAssignableTo(model, adapted, ITERABLE) && !isAssignableTo(model, adapted, ITERATOR)
                || adapted.typeArguments().isEmpty()) {
            return verdict;
        }
        return new ReturnVerdict(verdict.depth(), false, null,
                fieldReturn(model, adapted.typeArguments().get(0), list.getType(), false, subscription));
    }

    @Nullable
    private static FieldDefinition field(SchemaModel schema, String typeName, String fieldName) {
        List<FieldDefinition> fields = schema.registry().getType(typeName)
                .map(type -> type instanceof ObjectTypeDefinition object ? object.getFieldDefinitions()
                        : type instanceof InterfaceTypeDefinition iface ? iface.getFieldDefinitions()
                        : List.<FieldDefinition>of())
                .orElse(List.of());
        return fields.stream().filter(it -> it.getName().equals(fieldName)).findFirst().orElse(null);
    }

    private static boolean isAssignableTo(TypeModel model, TypeRef type, String binaryName) {
        return !type.array() && !type.primitive()
                && (type.rawName().equals(binaryName) || model.resolve(type).isAssignableTo(binaryName));
    }

    // Class#getName: the descriptor form for an array, which is how the messages have always named one
    private static String className(TypeRef type) {
        if (!type.array()) {
            return type.rawName();
        }
        if (type.rawName().startsWith("[")) {
            return "[" + type.rawName();
        }
        if (type.primitive()) {
            return "[" + switch (type.rawName()) {
                case "boolean" -> "Z";
                case "byte" -> "B";
                case "char" -> "C";
                case "double" -> "D";
                case "float" -> "F";
                case "int" -> "I";
                case "long" -> "J";
                default -> "S";
            };
        }
        return "[L" + type.rawName() + ";";
    }

    private static boolean isEnvironment(TypeRef type) {
        return isType(type, DATA_FETCHING_ENVIRONMENT);
    }

    private static boolean isType(TypeRef type, String binaryName) {
        return !type.array() && type.rawName().equals(binaryName);
    }

    /**
     * An argument of a schema field.
     *
     * @param name     the argument name
     * @param typeName the name of the argument type, without wrappers
     */
    public record SchemaArgument(String name, String typeName) {
    }
}
