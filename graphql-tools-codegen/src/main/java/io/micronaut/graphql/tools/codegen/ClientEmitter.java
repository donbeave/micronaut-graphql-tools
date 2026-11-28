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
package io.micronaut.graphql.tools.codegen;

import io.micronaut.graphql.tools.codegen.ir.AbstractType;
import io.micronaut.graphql.tools.codegen.ir.EnumType;
import io.micronaut.graphql.tools.codegen.ir.EnumValue;
import io.micronaut.graphql.tools.codegen.ir.Field;
import io.micronaut.graphql.tools.codegen.ir.InputField;
import io.micronaut.graphql.tools.codegen.ir.InputType;
import io.micronaut.graphql.tools.codegen.ir.Ir;
import io.micronaut.graphql.tools.codegen.ir.ModelType;
import io.micronaut.graphql.tools.codegen.ir.client.FragmentDefinition;
import io.micronaut.graphql.tools.codegen.ir.client.InlineFragment;
import io.micronaut.graphql.tools.codegen.ir.client.OperationDocument;
import io.micronaut.graphql.tools.codegen.ir.client.Payload;
import io.micronaut.graphql.tools.codegen.ir.client.SelectedField;
import io.micronaut.graphql.tools.codegen.ir.client.SelectionSet;
import io.micronaut.graphql.tools.codegen.ir.client.Variable;
import io.micronaut.graphql.tools.codegen.ir.shared.Names;
import io.micronaut.graphql.tools.codegen.ir.shared.Presence;
import io.micronaut.graphql.tools.codegen.ir.shared.TypeRef;
import io.micronaut.sourcegen.javapoet.AnnotationSpec;
import io.micronaut.sourcegen.javapoet.ClassName;
import io.micronaut.sourcegen.javapoet.CodeBlock;
import io.micronaut.sourcegen.javapoet.FieldSpec;
import io.micronaut.sourcegen.javapoet.MethodSpec;
import io.micronaut.sourcegen.javapoet.ParameterSpec;
import io.micronaut.sourcegen.javapoet.ParameterizedTypeName;
import io.micronaut.sourcegen.javapoet.TypeName;
import io.micronaut.sourcegen.javapoet.TypeSpec;
import io.micronaut.sourcegen.javapoet.WildcardTypeName;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The emitter of the client models, the Java output of client mode. For each operation it writes a request class and a variables
 * record; it writes the result records, the sealed interfaces of the abstract selections, the interfaces of the fragments, and the
 * enums and input records that the operations reach. Every type is in the package that the IR names, and each package gets a
 * {@code package-info.java}. The types are serdeable, nullability is the annotation of the {@code nullableAnnotation} option, and
 * nothing in the output sends a request: the request class describes an operation and does not execute it.
 */
final class ClientEmitter implements Emitter {

    private static final String JACKSON = "com.fasterxml.jackson.annotation";
    private static final ClassName SERDEABLE = ClassName.get("io.micronaut.serde.annotation", "Serdeable");
    private static final ClassName SERIALIZABLE = ClassName.get("io.micronaut.serde.annotation", "Serdeable", "Serializable");
    private static final ClassName DESERIALIZABLE = ClassName.get("io.micronaut.serde.annotation", "Serdeable", "Deserializable");
    private static final ClassName JSON_TYPE_INFO = ClassName.get(JACKSON, "JsonTypeInfo");
    private static final ClassName JSON_TYPE_INFO_ID = ClassName.get(JACKSON, "JsonTypeInfo", "Id");
    private static final ClassName JSON_SUB_TYPES = ClassName.get(JACKSON, "JsonSubTypes");
    private static final ClassName JSON_SUB_TYPE = ClassName.get(JACKSON, "JsonSubTypes", "Type");
    private static final ClassName SINGLETON = ClassName.get("jakarta.inject", "Singleton");
    private static final ClassName DESERIALIZER = ClassName.get("io.micronaut.serde", "Deserializer");
    private static final ClassName DECODER = ClassName.get("io.micronaut.serde", "Decoder");
    private static final ClassName DECODER_CONTEXT = ClassName.get("io.micronaut.serde", "Deserializer", "DecoderContext");
    private static final ClassName JSON_ENUM_DEFAULT = ClassName.get(JACKSON, "JsonEnumDefaultValue");
    private static final ClassName ARGUMENT = ClassName.get("io.micronaut.core.type", "Argument");
    private static final ClassName OPERATION_TYPE = ClassName.get("io.micronaut.graphql.tools.client", "OperationType");
    private static final ClassName OBJECTS = ClassName.get(java.util.Objects.class);
    private static final String TYPENAME = "__typename";
    private static final String UNKNOWN = "UNKNOWN__";
    private static final String FALLBACK = "Fallback";

    @Override
    public List<GeneratedFile> emit(Ir ir, Options options) {
        Emission emission = new Emission(ir, options);
        return emission.run();
    }

    private static final class Emission {

        private final Ir ir;
        private final Options options;
        private final JavaTypes types;
        private final List<GeneratedFile> files = new ArrayList<>();
        private final Set<String> packages = new TreeSet<>();
        private final Set<String> permitted = new HashSet<>();
        private final Set<String> others = new HashSet<>();
        private final Map<String, FragmentDefinition> fragments = new TreeMap<>();

        Emission(Ir ir, Options options) {
            this.ir = ir;
            this.options = options;
            this.types = new JavaTypes(options.nullableAnnotation());
        }

        List<GeneratedFile> run() {
            for (AbstractType type : ir.abstractTypes()) {
                if (type.sealed()) {
                    for (Names member : type.members()) {
                        permitted.add(member.qualifiedName());
                        if (member.sdlName().equals(type.names().sdlName())) {
                            others.add(member.qualifiedName());
                        }
                    }
                }
            }
            for (FragmentDefinition fragment : ir.fragments()) {
                fragments.put(fragment.names().sdlName(), fragment);
            }
            ir.enums().forEach(this::enumeration);
            ir.inputs().forEach(this::input);
            ir.abstractTypes().forEach(this::abstractType);
            ir.models().forEach(this::model);
            ir.operations().forEach(this::operation);
            for (String packageName : packages) {
                files.add(new GeneratedFile(JavaEmitter.path(packageName, "package-info"), JavaEmitter.packageInfo(packageName, true, options)));
            }
            files.sort(Comparator.comparing(GeneratedFile::path));
            return files;
        }

        private void add(Names names, TypeSpec.Builder spec) {
            files.add(new GeneratedFile(JavaEmitter.path(names.packageName(), names.simpleName()),
                    JavaEmitter.write(names.packageName(), spec)));
            packages.add(names.packageName());
        }

        // ----- enums, inputs

        // Micronaut Serialization reads a name that no constant has as an error and has no default constant of its own, so the enum
        // names its deserializer, which reads such a name as the constant for the values that the client does not know
        private void enumeration(EnumType type) {
            ClassName self = ClassName.get(type.names().packageName(), type.names().simpleName());
            TypeSpec.Builder spec = TypeSpec.enumBuilder(type.names().simpleName()).addModifiers(Modifier.PUBLIC);
            JavaEmitter.documentation(spec, type.docs());
            JavaEmitter.generated(spec, options);
            spec.addAnnotation(SERIALIZABLE);
            Set<String> taken = new HashSet<>();
            for (EnumValue value : type.values()) {
                taken.add(value.name());
                TypeSpec.Builder constant = TypeSpec.anonymousClassBuilder("");
                JavaEmitter.documentation(constant, value.docs());
                spec.addEnumConstant(value.name(), constant.build());
            }
            String unknown = UNKNOWN;
            while (taken.contains(unknown)) {
                unknown += "_";
            }
            spec.addEnumConstant(unknown, TypeSpec.anonymousClassBuilder("")
                    .addJavadoc("A value that this client does not know, such as one that the schema added after the code was generated.\n")
                    .addAnnotation(JSON_ENUM_DEFAULT).build());
            ClassName fallback = self.nestedClass(FALLBACK);
            spec.addAnnotation(AnnotationSpec.builder(DESERIALIZABLE).addMember("using", "$T.class", fallback).build());
            TypeName argument = ParameterizedTypeName.get(ARGUMENT, WildcardTypeName.supertypeOf(self));
            spec.addType(TypeSpec.classBuilder(FALLBACK).addModifiers(Modifier.STATIC, Modifier.FINAL)
                    .addJavadoc("Reads a name that no constant has as {@link #$L}.\n", unknown)
                    .addAnnotation(SINGLETON)
                    .addSuperinterface(ParameterizedTypeName.get(DESERIALIZER, self))
                    .addMethod(MethodSpec.methodBuilder("deserialize").addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                            .returns(self).addException(java.io.IOException.class)
                            .addParameter(DECODER, "decoder").addParameter(DECODER_CONTEXT, "context").addParameter(argument, "type")
                            .addStatement("$T name = decoder.decodeString()", String.class)
                            .beginControlFlow("for ($T constant : values())", self)
                            .beginControlFlow("if (constant.name().equals(name))")
                            .addStatement("return constant")
                            .endControlFlow()
                            .endControlFlow()
                            .addStatement("return $L", unknown)
                            .build())
                    .build());
            add(type.names(), spec);
        }

        private void input(InputType type) {
            TypeSpec.Builder spec = recordSpec(type.names());
            List<Field> documented = new ArrayList<>();
            for (InputField field : type.fields()) {
                documented.add(new Field(field.sdlName(), field.name(), field.type(), false, List.of(), field.docs(), field.directives(),
                        field.origin()));
            }
            JavaEmitter.recordDocumentation(spec, type.docs(), documented);
            for (InputField field : type.fields()) {
                ParameterSpec.Builder component = ParameterSpec.builder(valueType(field.type(), field.presence()), field.name());
                if (field.docs().deprecation() != null) {
                    component.addAnnotation(JavaEmitter.DEPRECATED);
                }
                spec.addRecordComponent(component.build());
            }
            add(type.names(), spec);
        }

        // a value that the caller may leave out is a reference that can be null, whatever its GraphQL type says
        private TypeName valueType(TypeRef type, Presence presence) {
            return presence == Presence.REQUIRED ? types.component(type, false) : types.omittable(type);
        }

        private TypeSpec.Builder recordSpec(Names names) {
            TypeSpec.Builder spec = TypeSpec.recordBuilder(names.simpleName()).addModifiers(Modifier.PUBLIC);
            JavaEmitter.generated(spec, options);
            spec.addAnnotation(SERDEABLE);
            return spec;
        }

        // ----- result models

        private void model(ModelType type) {
            Names names = type.names();
            TypeSpec.Builder spec = recordSpec(names);
            List<Field> components = type.fields().stream().filter(field -> field.arguments().isEmpty()).toList();
            JavaEmitter.recordDocumentation(spec, type.docs(), components);
            if (others.contains(names.qualifiedName())) {
                spec.addJavadoc("$L\n", "The object types that the selection does not name; the field {@code __typename} holds the name of the type.");
            }
            for (Names implemented : type.implemented()) {
                spec.addSuperinterface(ClassName.get(implemented.packageName(), implemented.simpleName()));
            }
            for (Names member : type.memberOf()) {
                spec.addSuperinterface(ClassName.get(member.packageName(), member.simpleName()));
            }
            for (Field field : components) {
                ParameterSpec.Builder component = ParameterSpec.builder(types.component(field), field.name());
                if (field.docs().deprecation() != null) {
                    component.addAnnotation(JavaEmitter.DEPRECATED);
                }
                spec.addRecordComponent(component.build());
            }
            add(names, spec);
        }

        private void abstractType(AbstractType type) {
            Names names = type.names();
            TypeSpec.Builder spec = TypeSpec.interfaceBuilder(names.simpleName()).addModifiers(Modifier.PUBLIC);
            if (type.sealed()) {
                spec.addModifiers(Modifier.SEALED);
                for (Names member : type.members()) {
                    spec.addPermittedSubclass(ClassName.get(member.packageName(), member.simpleName()));
                }
            } else if (permitted.contains(names.qualifiedName())) {
                spec.addModifiers(Modifier.NON_SEALED);
            }
            JavaEmitter.documentation(spec, type.docs());
            JavaEmitter.generated(spec, options);
            if (!type.members().isEmpty()) {
                polymorphic(spec, type);
            }
            for (Names implemented : type.implemented()) {
                spec.addSuperinterface(ClassName.get(implemented.packageName(), implemented.simpleName()));
            }
            for (Field field : type.fields()) {
                if (field.arguments().isEmpty()) {
                    spec.addMethod(MethodSpec.methodBuilder(field.name()).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .returns(types.component(field)).build());
                }
            }
            add(names, spec);
        }

        // the member is chosen by the field __typename, which the document always selects on an abstract type; a type that the
        // selection does not name is read as the member that has the name of the abstract type
        private void polymorphic(TypeSpec.Builder spec, AbstractType type) {
            spec.addAnnotation(SERDEABLE);
            AnnotationSpec.Builder info = AnnotationSpec.builder(JSON_TYPE_INFO)
                    .addMember("use", "$T.$L", JSON_TYPE_INFO_ID, "NAME")
                    .addMember("property", "$S", TYPENAME)
                    .addMember("visible", "$L", true);
            Names other = type.members().stream().filter(member -> member.sdlName().equals(type.names().sdlName())).findFirst().orElse(null);
            if (other != null) {
                info.addMember("defaultImpl", "$T.class", ClassName.get(other.packageName(), other.simpleName()));
            }
            spec.addAnnotation(info.build());
            AnnotationSpec.Builder subtypes = AnnotationSpec.builder(JSON_SUB_TYPES);
            boolean any = false;
            for (Names member : type.members()) {
                if (!member.equals(other)) {
                    subtypes.addMember("value", "$L", AnnotationSpec.builder(JSON_SUB_TYPE)
                            .addMember("value", "$T.class", ClassName.get(member.packageName(), member.simpleName()))
                            .addMember("name", "$S", member.sdlName()).build());
                    any = true;
                }
            }
            if (any) {
                spec.addAnnotation(subtypes.build());
            }
        }

        // ----- operations

        private void operation(OperationDocument operation) {
            Names variablesNames = operation.variablesType();
            TypeSpec.Builder variables = recordSpec(variablesNames);
            variables.addJavadoc("The variables of the operation {@code $L}.\n", operation.names().sdlName());
            for (Variable variable : operation.variables()) {
                variables.addRecordComponent(ParameterSpec.builder(valueType(variable.type(), variable.presence()), variable.name()).build());
            }
            add(variablesNames, variables);
            add(operation.names(), request(operation, ClassName.get(variablesNames.packageName(), variablesNames.simpleName())));
        }

        private TypeSpec.Builder request(OperationDocument operation, ClassName variablesType) {
            Names names = operation.names();
            ClassName self = ClassName.get(names.packageName(), names.simpleName());
            TypeSpec.Builder spec = TypeSpec.classBuilder(names.simpleName()).addModifiers(Modifier.PUBLIC, Modifier.FINAL);
            spec.addJavadoc("The request of the $L {@code $L}: its document, its variables and the types of its result. It sends nothing.\n",
                    operation.kind().name().toLowerCase(java.util.Locale.ROOT), names.sdlName());
            JavaEmitter.generated(spec, options);
            spec.addField(constant(String.class, "NAME", "$S", operation.names().sdlName(), "The name of the operation."));
            spec.addField(constant(String.class, "DOCUMENT", "$S", operation.documentText(),
                    "The document that is sent: the operation and the fragments it uses, with the field {@code __typename} added where a result needs it."));
            spec.addField(constant(String.class, "HASH", "$S", operation.documentHash(), "The SHA-256 of the UTF-8 bytes of the document, as 64 hexadecimal digits."));
            spec.addField(constant(OPERATION_TYPE, "TYPE", "$T.$L", OPERATION_TYPE, operation.kind().name(), "The kind of the operation."));
            TypeRef payload = operation.result();
            String field = operation.payload() == Payload.FIELD ? rootField(operation) : null;
            if (field != null) {
                spec.addField(constant(String.class, "FIELD", "$S", field, "The response key of the one root field that the operation selects."));
            }
            TypeName payloadType = types.plain(payload);
            spec.addField(FieldSpec.builder(ParameterizedTypeName.get(ARGUMENT, payloadType), "RESULT", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .addJavadoc("The type of the payload: the value of the root field when the operation selects one, and the {@code data} object otherwise.\n")
                    .initializer(argument(payload)).build());
            if (field == null) {
                spec.addField(FieldSpec.builder(ParameterizedTypeName.get(ARGUMENT, payloadType), "DATA", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addJavadoc("The type that the {@code data} object of the response is read as.\n")
                        .initializer("RESULT").build());
            } else {
                TypeName map = ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(String.class), payloadType);
                spec.addField(FieldSpec.builder(ParameterizedTypeName.get(ARGUMENT, map), "DATA", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addJavadoc("The type that the {@code data} object of the response is read as: a map from the response key of the root field to its value.\n")
                        .initializer("$T.mapOf($T.STRING, RESULT)", ARGUMENT, ARGUMENT).build());
            }
            spec.addField(FieldSpec.builder(variablesType, "variables", Modifier.PRIVATE, Modifier.FINAL).build());
            spec.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
                    .addParameter(variablesType, "variables")
                    .addStatement("this.variables = $T.requireNonNull(variables, $S)", OBJECTS, "variables").build());
            if (operation.variables().isEmpty()) {
                spec.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).addStatement("this(new $T())", variablesType).build());
            }
            spec.addMethod(accessor("variables", variablesType, "variables", "The variables of this request."));
            spec.addMethod(accessor("name", ClassName.get(String.class), "NAME", "The name of the operation."));
            spec.addMethod(accessor("document", ClassName.get(String.class), "DOCUMENT", "The document of the operation."));
            spec.addMethod(accessor("hash", ClassName.get(String.class), "HASH", "The hash of the document."));
            spec.addMethod(accessor("kind", OPERATION_TYPE, "TYPE", "The kind of the operation."));
            spec.addMethod(MethodSpec.methodBuilder("equals").addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(TypeName.BOOLEAN)
                    .addParameter(Object.class, "other")
                    .addStatement("return this == other || other instanceof $T that && variables.equals(that.variables)", self).build());
            spec.addMethod(MethodSpec.methodBuilder("hashCode").addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(TypeName.INT)
                    .addStatement("return variables.hashCode()").build());
            spec.addMethod(MethodSpec.methodBuilder("toString").addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                    .returns(ClassName.get(String.class)).addStatement("return NAME + variables").build());
            return spec;
        }

        private static FieldSpec constant(java.lang.reflect.Type type, String name, String format, Object value, String doc) {
            return FieldSpec.builder(type, name, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL).addJavadoc(doc + "\n")
                    .initializer(format, value).build();
        }

        private static FieldSpec constant(ClassName type, String name, String format, Object first, Object second, String doc) {
            return FieldSpec.builder(type, name, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL).addJavadoc(doc + "\n")
                    .initializer(format, first, second).build();
        }

        private static MethodSpec accessor(String name, TypeName type, String field, String doc) {
            return MethodSpec.methodBuilder(name).addModifiers(Modifier.PUBLIC).returns(type).addJavadoc("$L\n", doc)
                    .addStatement("return $L", field).build();
        }

        // the Micronaut argument of a type: of a class, or a list of an argument
        private CodeBlock argument(TypeRef type) {
            if (type instanceof TypeRef.ListOf list) {
                return list.element() instanceof TypeRef.ListOf ? CodeBlock.of("$T.listOf($L)", ARGUMENT, argument(list.element()))
                        : CodeBlock.of("$T.listOf($T.class)", ARGUMENT, types.plain(list.element()));
            }
            return CodeBlock.of("$T.of($T.class)", ARGUMENT, types.plain(type));
        }

        // the response key of the one root field, found through the fragments and inline fragments of the selection
        private String rootField(OperationDocument operation) {
            Set<String> keys = new LinkedHashSet<>();
            collect(operation.selection(), keys, new HashSet<>());
            return keys.size() == 1 ? keys.iterator().next() : null;
        }

        private void collect(SelectionSet selection, Set<String> keys, Set<String> visited) {
            for (SelectedField field : selection.fields()) {
                keys.add(field.responseKey());
            }
            for (InlineFragment inline : selection.inlineFragments()) {
                collect(inline.selection(), keys, visited);
            }
            selection.fragmentSpreads().forEach(spread -> {
                FragmentDefinition fragment = fragments.get(spread.name());
                if (fragment != null && visited.add(spread.name())) {
                    collect(fragment.selection(), keys, visited);
                }
            });
        }
    }
}
