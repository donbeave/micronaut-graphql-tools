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

import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SourceLocation;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.finding.SchemaLocation;
import io.micronaut.graphql.tools.binding.internal.model.JClass;
import io.micronaut.graphql.tools.binding.internal.model.JProperty;
import io.micronaut.graphql.tools.binding.internal.model.TypeModel;
import io.micronaut.graphql.tools.binding.internal.model.TypeRef;
import io.micronaut.graphql.tools.binding.internal.rules.InputTypeRules.ArgumentValueMisuse;
import io.micronaut.graphql.tools.binding.internal.rules.InputTypeRules.ClassProblem;
import io.micronaut.graphql.tools.binding.internal.rules.InputTypeRules.FieldBinding;
import io.micronaut.graphql.tools.binding.internal.rules.InputTypeRules.InputFields;
import io.micronaut.graphql.tools.binding.internal.schema.ScalarTable;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaTypes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The input and enum rules for one resolver class, in the order and with the codes and coordinates of the startup. It walks
 * the type of each argument (and the enum that a return type ends in) down to the input classes, enums and built-in
 * scalars it reaches, and stops where the startup stops: the startup reports the first problem of a field and binds each
 * input type and enum once, so this walk does too.
 *
 * <p>It reports what the class and the schema decide. It stops without a finding where the startup reports a code that is
 * not a compile-time code (a wrong class for a scalar, a list that is not a collection, a field without a property), where
 * the answer depends on a bean (a custom scalar) and where the type is bound to a different class by another resolver. A class problem of an input type or an enum is
 * reported at the schema type, so that the coordinate does not depend on which field the startup reaches the type from.</p>
 *
 * <p>The findings are two lists, because the startup reports them in two phases: {@link #failures()} are binding
 * failures, thrown while the types are bound ({@code nullable-primitive} among them), and {@link #checks()} are the strict
 * checks that run after a binding that failed nowhere ({@code unmapped-input-property}, {@code unbound-enum-constant}).</p>
 *
 * <p>The walk down a chain of input types runs from a work stack of frames, one per input type that is being bound, so the
 * depth of the Java stack does not grow with the length of the chain (the startup binds with a work stack too). The calls
 * that stay recursive are bounded by the nesting of one declared type, lists and wrappers, not by the chain.</p>
 */
@Internal
public final class InputBindingFindings {

    private static final String FUTURE = "java.util.concurrent.Future";
    private static final String COMPLETION_STAGE = "java.util.concurrent.CompletionStage";
    private static final String ITERABLE = "java.lang.Iterable";

    private final SchemaModel schema;
    private final TypeModel model;
    // the Java class of each input type and enum bound so far: the startup binds a type once, and a second class for it is
    // a mapping conflict, which only the startup reports
    private final Map<String, String> boundClass = new HashMap<>();
    // the stop of an input type whose binding stopped: the startup reports it again for every later use
    private final Map<String, Halt> inputHalts = new HashMap<>();
    private final Map<String, Finding> failures = new LinkedHashMap<>();
    private final Map<String, Finding> checks = new LinkedHashMap<>();
    private final InputClassCoverage coverage = new InputClassCoverage();
    private final Map<String, String> coverageMembers = new HashMap<>();
    private boolean halted;

    /**
     * @param schema the schema
     * @param model  the type model
     */
    public InputBindingFindings(SchemaModel schema, TypeModel model) {
        this.schema = schema;
        this.model = model;
    }

    /**
     * @return whether the startup throws while it binds a type of this class: a failure, or a stop that has no finding of
     * its own here (a wrong class for a scalar, a custom scalar)
     */
    public boolean halted() {
        return halted;
    }

    /**
     * @return the binding failures found so far, without repeats
     */
    public List<Finding> failures() {
        return List.copyOf(failures.values());
    }

    /**
     * @return the strict findings found so far, without repeats
     */
    public List<Finding> checks() {
        Map<String, Finding> all = new LinkedHashMap<>(checks);
        unmappedProperties().forEach(it -> all.putIfAbsent(it.code() + " " + it.coordinate(), it));
        return List.copyOf(all.values());
    }

    /**
     * Binds the Java type of one argument of a field to the type of the argument in the schema.
     *
     * @param member     the Java member that declares the parameter, as {@code binary.Name#member(parameter types)}
     * @param declared   the declared type of the parameter
     * @param argument   the argument in the schema
     * @param coordinate the coordinate of the argument, {@code Type.field(argument:)}
     * @return whether the startup binds the argument without a failure and goes on to the next one
     */
    public boolean argument(String member, TypeRef declared, InputValueDefinition argument, String coordinate) {
        return finish(drive(walk(new Site(member, coordinate, argument.getSourceLocation()), declared, argument.getType(), true)));
    }

    /**
     * Binds the Java type that a field returns, where the field type ends in an enum.
     *
     * @param member     the Java member that returns it
     * @param leaf       the declared type of the element after the wrappers and lists of the return type are peeled
     * @param schemaType the element type of the field in the schema
     * @param coordinate the coordinate of the field
     * @param location   where the schema defines the field
     */
    public void returned(String member, TypeRef leaf, Type<?> schemaType, String coordinate, @Nullable SourceLocation location) {
        Type<?> unwrapped = unwrap(schemaType);
        if (!(unwrapped instanceof TypeName name)) {
            return;
        }
        if (ScalarTable.isGraphQlBuiltInType(name.getName())) {
            Set<String> supported = ScalarTable.getSupportedClasses(name).stream().map(Class::getName)
                    .collect(Collectors.toSet());
            if (leaf.array() || !supported.contains(leaf.rawName())) {
                // a wrong class for a built-in scalar is a class mapping failure, which only the startup reports
                halted = true;
            }
            return;
        }
        TypeDefinition<?> definition = schema.registry().getType(name.getName()).orElse(null);
        if (definition instanceof EnumTypeDefinition enumType) {
            finish(enumType(new Site(member, coordinate, location), enumType, leaf));
        } else if (definition instanceof ObjectTypeDefinition objectType) {
            finish(objectType(new Site(member, coordinate, location), objectType, leaf));
        }
    }

    // the startup reads the bean introspection of the class that backs an object type, and names the field that reaches it
    @Nullable
    private Halt objectType(Site site, ObjectTypeDefinition definition, TypeRef declared) {
        if (declared.array() || declared.primitive() || isPlatformClass(declared.rawName())) {
            return null;
        }
        JClass objectClass = model.resolve(declared);
        if (objectClass.kind() == JClass.Kind.INTERFACE
                || objectClass.introspected()) {
            return null;
        }
        return failed(site, BindingCode.CLASS_NOT_INTROSPECTED,
                "the class " + objectClass.name() + " that backs " + definition.getName() + " is not introspected",
                "annotate the class with @GraphQLType, or list it or its package in @GraphQLModels on a class of this module");
    }

    private static boolean isPlatformClass(String name) {
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("kotlin.") || name.startsWith("groovy.");
    }

    private boolean finish(@Nullable Halt halt) {
        if (halt == null) {
            return true;
        }
        halted = true;
        if (halt.finding != null && !halt.repeated) {
            failures.putIfAbsent(halt.finding.code() + " " + halt.finding.coordinate(), halt.finding);
        }
        return false;
    }

    @Nullable
    private Halt walk(Site site, TypeRef declared, Type<?> schemaType, boolean fieldLevel) {
        if (!declared.array() && !declared.primitive() && (isAssignable(declared, FUTURE)
                || isAssignable(declared, COMPLETION_STAGE))) {
            // the startup binds what an asynchronous argument wraps, once, before it unwraps an ArgumentValue
            if (declared.typeArguments().isEmpty()) {
                return Halt.SILENT;
            }
            return walkType(site, declared.typeArguments().get(0), schemaType, fieldLevel);
        }
        return walkType(site, declared, schemaType, fieldLevel);
    }

    @Nullable
    private Halt walkType(Site site, TypeRef declared, Type<?> schemaType, boolean fieldLevel) {
        boolean nonNull = schemaType instanceof NonNullType;
        if (!declared.array() && InputTypeRules.ARGUMENT_VALUE.equals(declared.rawName())) {
            ArgumentValueMisuse misuse = InputTypeRules.argumentValueMisuse(declared, fieldLevel, nonNull);
            if (misuse == null) {
                // the startup binds what the wrapper wraps
                return walkType(site, declared.typeArguments().get(0), schemaType, fieldLevel);
            }
            return failed(site, BindingCode.UNSUPPORTED_INPUT_TYPE,
                    "the Java type is not supported for this argument or input field: "
                            + InputTypeRules.argumentValueReason(misuse, SchemaTypes.print(schemaType)),
                    null);
        }
        Type<?> unwrapped = unwrap(schemaType);
        if (unwrapped instanceof ListType list) {
            return list(site, declared, list);
        }
        if (!(unwrapped instanceof TypeName typeName)) {
            return Halt.SILENT;
        }
        if (ScalarTable.isGraphQlBuiltInType(typeName.getName())) {
            Set<String> supported = ScalarTable.getSupportedClasses(typeName).stream().map(Class::getName)
                    .collect(Collectors.toSet());
            if (declared.array() || !supported.contains(declared.rawName())) {
                return Halt.SILENT;
            }
            if (ResolverClassRules.nullablePrimitive(declared, nonNull)) {
                return failed(site, BindingCode.NULLABLE_PRIMITIVE, "the "
                                + (site.coordinate.endsWith(":)") ? "argument" : "input field") + " is mapped to the primitive "
                                + declared.rawName() + ", but the GraphQL type " + typeName.getName()
                                + " is nullable, so null cannot be passed",
                        "use the wrapper type or make the schema type non-null");
            }
            return null;
        }
        TypeDefinition<?> definition = schema.registry().getType(typeName.getName()).orElse(null);
        if (definition instanceof InputObjectTypeDefinition input) {
            return input(site, input, declared);
        }
        if (definition instanceof EnumTypeDefinition enumType) {
            return enumType(site, enumType, declared);
        }
        // a custom scalar needs the GraphQLScalarMapping beans, which the startup reads
        return Halt.SILENT;
    }

    @Nullable
    private Halt list(Site site, TypeRef declared, ListType list) {
        if (declared.array() || declared.primitive() || !isAssignable(declared, ITERABLE)) {
            return Halt.SILENT;
        }
        if (!InputTypeRules.supportedList(declared.rawName())) {
            return failed(site, BindingCode.UNSUPPORTED_INPUT_TYPE,
                    "the Java type is not supported for this argument or input field: "
                            + InputTypeRules.unsupportedListReason(declared.rawName()), null);
        }
        if (declared.typeArguments().isEmpty()) {
            return Halt.SILENT;
        }
        return walk(site, declared.typeArguments().get(0), list.getType(), false);
    }

    @Nullable
    private Halt input(Site site, InputObjectTypeDefinition definition, TypeRef declared) {
        String name = definition.getName();
        String bound = boundClass.get(name);
        if (bound != null) {
            if (!bound.equals(declared.rawName())) {
                return Halt.SILENT;
            }
            Halt before = inputHalts.get(name);
            return before == null ? null : new Halt(before.finding, true);
        }
        boundClass.put(name, declared.rawName());
        Halt halt = open(site, definition, declared);
        if (halt != null && halt.frame == null) {
            inputHalts.put(name, halt);
        }
        return halt;
    }

    /**
     * Starts the binding of an input type: a stop, or the frame that binds its fields.
     */
    @Nullable
    private Halt open(Site site, InputObjectTypeDefinition definition, TypeRef declared) {
        if (declared.array() || declared.primitive()) {
            return Halt.SILENT;
        }
        JClass inputClass = model.resolve(declared);
        ClassProblem problem = InputTypeRules.classProblem(inputClass);
        if (problem == ClassProblem.NOT_A_CLASS) {
            return Halt.SILENT;
        }
        if (problem == ClassProblem.NOT_INTROSPECTED) {
            return notIntrospected(site, definition, inputClass, "annotate it with @GraphQLInput or @Introspected");
        }
        Map<String, JProperty> properties = new HashMap<>();
        for (JProperty property : inputClass.readableProperties()) {
            properties.put(property.name(), property);
        }
        for (JProperty property : inputClass.writableProperties()) {
            properties.putIfAbsent(property.name(), property);
        }
        return Halt.enter(new InputFrame(site, definition, inputClass, InputTypeRules.fields(inputClass), properties));
    }

    /**
     * Binds the input types that a type needs, depth first and in the order of the fields, from a stack of frames: the
     * stop of a frame is the stop of the field that reached it, and ends that frame and the frames below it in turn.
     */
    @Nullable
    private Halt drive(@Nullable Halt first) {
        Deque<InputFrame> stack = new ArrayDeque<>();
        Halt outcome = first;
        while (true) {
            if (outcome != null && outcome.frame != null) {
                stack.push(outcome.frame);
                outcome = advance(outcome.frame, null);
                continue;
            }
            if (stack.isEmpty()) {
                return outcome;
            }
            InputFrame done = stack.pop();
            if (outcome != null) {
                inputHalts.put(done.definition.getName(), outcome);
            }
            if (stack.isEmpty()) {
                return outcome;
            }
            outcome = advance(stack.peek(), outcome);
        }
    }

    /**
     * Goes on with the fields of an input type from where the frame stopped.
     *
     * @param frame  the frame
     * @param result how the field that the frame waited for was bound: {@code null} for bound
     * @return the next input type to bind (a pending stop), where the frame waits for it, or how the frame ends
     */
    @Nullable
    private Halt advance(InputFrame frame, @Nullable Halt result) {
        InputValueDefinition waiting = frame.waiting;
        if (waiting != null) {
            frame.waiting = null;
            if (result != null) {
                return result;
            }
            Halt after = afterField(frame, waiting, frame.waitingSite, frame.waitingBinding);
            if (after != null) {
                return after;
            }
        }
        List<InputValueDefinition> definitions = frame.definition.getInputValueDefinitions();
        while (frame.next < definitions.size()) {
            InputValueDefinition field = definitions.get(frame.next++);
            FieldBinding binding = frame.fields.binding(field.getName());
            if (binding == FieldBinding.MISSING) {
                return Halt.SILENT;
            }
            Site fieldSite = new Site(frame.site.member, frame.definition.getName() + "." + field.getName(),
                    field.getSourceLocation());
            Halt halt = walk(fieldSite, frame.properties.get(field.getName()).type(), field.getType(), true);
            if (halt != null && halt.frame != null) {
                frame.waiting = field;
                frame.waitingSite = fieldSite;
                frame.waitingBinding = binding;
                return halt;
            }
            if (halt != null) {
                return halt;
            }
            Halt after = afterField(frame, field, fieldSite, binding);
            if (after != null) {
                return after;
            }
        }
        coverage.add(frame.definition.getName(), definitions.stream().map(InputValueDefinition::getName).toList(),
                frame.inputClass);
        coverageMembers.putIfAbsent(frame.inputClass.name(), frame.site.member);
        return null;
    }

    // An input type of the schema that this walk did not reach may be bound to a class of this walk by another resolver, so
    // its fields cover a property here when the class can take them all: a finding is only what no input type of the schema can
    // cover. When such a type could
    // sort before the types of this walk, the startup names the finding after it, and this walk reports nothing.
    private List<Finding> unmappedProperties() {
        Set<String> reached = coverage.inputTypes();
        Map<String, List<String>> others = new HashMap<>();
        for (InputObjectTypeDefinition other : schema.registry().getTypes(InputObjectTypeDefinition.class)) {
            if (!reached.contains(other.getName())) {
                others.put(other.getName(), other.getInputValueDefinitions().stream().map(InputValueDefinition::getName).toList());
            }
        }
        List<Finding> result = new ArrayList<>();
        for (InputClassCoverage.ClassCoverage entry : coverage.verdicts(others)) {
            if (!entry.named()) {
                continue;
            }
            for (String property : entry.verdict().unmappedProperties()) {
                String coordinate = entry.inputType() + "." + property;
                result.add(new Finding(BindingCode.UNMAPPED_INPUT_PROPERTY.code(), coordinate,
                        "the settable property " + property + " of " + entry.inputClass().name()
                                + " has no field in " + (entry.inputTypes().size() == 1
                                ? "the input type " + entry.inputType()
                                : "any of the input types " + String.join(", ", entry.inputTypes()))
                                + ", so no request can set it",
                        coverageMembers.get(entry.inputClass().name()), null, null,
                        "add the field to an input type, or annotate the property with @GraphQLIgnore"));
            }
        }
        return result;
    }

    @Nullable
    private Halt afterField(InputFrame frame, InputValueDefinition field, Site fieldSite, FieldBinding binding) {
        if (binding == FieldBinding.NOT_SETTABLE
                && InputTypeRules.ARGUMENT_VALUE.equals(frame.properties.get(field.getName()).type().rawName())) {
            return failed(fieldSite, BindingCode.UNSUPPORTED_INPUT_TYPE,
                    "the Java type is not supported for this argument or input field: " + InputTypeRules.ARGUMENT_VALUE
                            + " needs a writable property: it is set to omitted() when the client leaves it out.",
                    null);
        }
        if (binding == FieldBinding.NOT_SETTABLE) {
            return failed(fieldSite, BindingCode.INPUT_FIELD_NOT_SETTABLE,
                    "the input field " + field.getName() + " maps to a property of " + frame.inputClass.name()
                            + " that cannot be set: it has no setter and no constructor parameter",
                    "add a setter or a constructor parameter for it, or remove the field from the input type");
        }
        return null;
    }

    @Nullable
    private Halt enumType(Site site, EnumTypeDefinition definition, TypeRef declared) {
        String name = definition.getName();
        String bound = boundClass.get(name);
        if (bound != null) {
            // an enum is bound once and a failure is not reported again; another class for it is a mapping conflict, which
            // only the startup reports
            return bound.equals(declared.rawName()) ? null : Halt.SILENT;
        }
        boundClass.put(name, declared.rawName());
        if (declared.array() || declared.primitive()) {
            return Halt.SILENT;
        }
        JClass enumClass = model.resolve(declared);
        if (enumClass.kind() != JClass.Kind.ENUM) {
            return Halt.SILENT;
        }
        if (!enumClass.introspected()) {
            return notIntrospected(site, definition, enumClass, "annotate the enum with @Introspected");
        }
        EnumVerdict verdict = EnumTypeRules.check(definition.getEnumValueDefinitions().stream()
                .map(EnumValueDefinition::getName).toList(), enumClass);
        if (!verdict.missingSchemaValues().isEmpty()) {
            return Halt.SILENT;
        }
        for (String constant : verdict.unboundConstants()) {
            String member = enumClass.name() + "#" + constant;
            checks.putIfAbsent(BindingCode.UNBOUND_ENUM_CONSTANT.code() + " " + member,
                    new Finding(BindingCode.UNBOUND_ENUM_CONSTANT.code(), member,
                            "the enum constant " + member + " has no value in the schema enum " + name,
                            site.member, null, null,
                            "add the value to the schema or remove the constant, or annotate the constant with @GraphQLIgnore"));
        }
        return null;
    }

    private Halt notIntrospected(Site site, TypeDefinition<?> definition, JClass javaClass, String fix) {
        // the type, not the argument or field that reaches it: the startup reports the first use, which a class cannot know
        Site where = new Site(site.member, definition.getName(), definition.getSourceLocation());
        return failed(where, BindingCode.CLASS_NOT_INTROSPECTED,
                "the class " + javaClass.name() + " that backs " + definition.getName() + " is not introspected", fix);
    }

    private Halt failed(Site site, BindingCode code, String sentence, @Nullable String fix) {
        SchemaLocation location = null;
        if (site.location != null && site.location.getLine() > 0) {
            location = new SchemaLocation(site.location.getSourceName() == null ? "schema" : site.location.getSourceName(),
                    site.location.getLine(), site.location.getColumn());
        }
        return new Halt(new Finding(code.code(), site.coordinate, sentence, site.member, null, location, fix), false);
    }

    private boolean isAssignable(TypeRef type, String binaryName) {
        return type.rawName().equals(binaryName) || model.resolve(type).isAssignableTo(binaryName);
    }

    private static Type<?> unwrap(Type<?> type) {
        return type instanceof NonNullType nonNull ? unwrap(nonNull.getType()) : type;
    }

    /**
     * The type that a field return ends in: the declared return type after the wrappers that
     * {@link ResolverClassRules#returnAdaptation} peeled, and the element type of each list.
     *
     * @param declared   the declared return type
     * @param fieldType  the type of the field in the schema
     * @param verdict    the return adaptation of the field, level by level
     * @return the declared type and the schema type of the innermost element, or {@code null} when a level does not
     * adapt
     */
    @Nullable
    public static Leaf leaf(TypeRef declared, Type<?> fieldType, ReturnVerdict verdict) {
        if (verdict.failure() != null || verdict.primitiveOptional()) {
            return null;
        }
        TypeRef adapted = declared;
        for (int i = 0; i < verdict.depth(); i++) {
            adapted = adapted.typeArguments().get(0);
        }
        Type<?> unwrapped = unwrap(fieldType);
        if (!(unwrapped instanceof ListType list)) {
            return new Leaf(adapted, unwrapped);
        }
        if (verdict.reactive() == ReturnVerdict.Reactive.MULTI) {
            return verdict.element() == null ? null : leaf(adapted, list.getType(), verdict.element());
        }
        if (verdict.element() == null || adapted.typeArguments().isEmpty()) {
            return null;
        }
        return leaf(adapted.typeArguments().get(0), list.getType(), verdict.element());
    }

    /**
     * @param type       the declared type of the element
     * @param schemaType the schema type of the element
     */
    public record Leaf(TypeRef type, Type<?> schemaType) {
    }

    /**
     * Where the walk is: the member for the message, the coordinate and location of the startup's mapping context.
     *
     * @param member     the Java member
     * @param coordinate the coordinate
     * @param location   where the schema defines it
     */
    private record Site(String member, String coordinate, @Nullable SourceLocation location) {
    }

    /**
     * The walk stops here: with a finding, silently, or with a finding that was reported before.
     *
     * @param finding  the finding, or {@code null} for a silent stop
     * @param repeated whether the finding was reported before
     * @param frame    not a stop: the input type that the walk reached and that the work stack binds next, or {@code null}
     */
    private record Halt(@Nullable Finding finding, boolean repeated, @Nullable InputFrame frame) {

        static final Halt SILENT = new Halt(null, false, null);

        Halt(@Nullable Finding finding, boolean repeated) {
            this(finding, repeated, null);
        }

        /**
         * Not a stop: the walk reached an input type that has to be bound, which the work stack does.
         *
         * @param frame the input type to bind
         * @return the pending step
         */
        static Halt enter(InputFrame frame) {
            return new Halt(null, false, frame);
        }
    }

    /**
     * An input type that is being bound: its fields are walked one at a time, and the frame waits while the input type
     * that one field reaches is bound.
     */
    private static final class InputFrame {

        final Site site;
        final InputObjectTypeDefinition definition;
        final JClass inputClass;
        final InputFields fields;
        final Map<String, JProperty> properties;
        int next;
        @Nullable
        InputValueDefinition waiting;
        @Nullable
        Site waitingSite;
        @Nullable
        FieldBinding waitingBinding;

        InputFrame(Site site, InputObjectTypeDefinition definition, JClass inputClass, InputFields fields,
                   Map<String, JProperty> properties) {
            this.site = site;
            this.definition = definition;
            this.inputClass = inputClass;
            this.fields = fields;
            this.properties = properties;
        }
    }
}
