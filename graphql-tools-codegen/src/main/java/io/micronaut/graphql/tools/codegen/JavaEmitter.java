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

import io.micronaut.graphql.tools.codegen.ir.EnumType;
import io.micronaut.graphql.tools.codegen.ir.EnumValue;
import io.micronaut.graphql.tools.codegen.ir.Field;
import io.micronaut.graphql.tools.codegen.ir.Ir;
import io.micronaut.graphql.tools.codegen.ir.ModelType;
import io.micronaut.graphql.tools.codegen.ir.NameClass;
import io.micronaut.graphql.tools.codegen.ir.NameConstant;
import io.micronaut.graphql.tools.codegen.ir.shared.Docs;
import io.micronaut.graphql.tools.codegen.ir.shared.Names;
import io.micronaut.sourcegen.javapoet.AnnotationSpec;
import io.micronaut.sourcegen.javapoet.ClassName;
import io.micronaut.sourcegen.javapoet.FieldSpec;
import io.micronaut.sourcegen.javapoet.JavaFile;
import io.micronaut.sourcegen.javapoet.MethodSpec;
import io.micronaut.sourcegen.javapoet.ParameterSpec;
import io.micronaut.sourcegen.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * The Java emitter: writes the enums, the object models (as records) and the name classes of the IR with the JavaPoet fork of
 * sourcegen, and one {@code package-info.java} for each package that holds a generated type. Output is Java 17 source, UTF-8,
 * {@code \n} line endings and one newline at the end.
 */
final class JavaEmitter implements Emitter {

    private static final String GENERATOR = "io.micronaut.graphql.tools.codegen";
    private static final ClassName DEPRECATED = ClassName.get(Deprecated.class);
    private static final ClassName STRING = ClassName.get(String.class);
    private static final ClassName INTROSPECTED = ClassName.get("io.micronaut.core.annotation", "Introspected");
    private static final ClassName GRAPHQL_TYPE = ClassName.get("io.micronaut.graphql.tools.annotation", "GraphQLType");
    private static final String JSPECIFY = "org.jspecify.annotations";

    @Override
    public List<GeneratedFile> emit(Ir ir, Options options) {
        List<GeneratedFile> files = new ArrayList<>();
        TreeSet<String> packages = new TreeSet<>();
        TreeSet<String> modelPackages = new TreeSet<>();
        for (EnumType type : ir.enums()) {
            Names names = type.names();
            files.add(new GeneratedFile(path(names.packageName(), names.simpleName()), enumFile(type, options)));
            packages.add(names.packageName());
        }
        // the models of a client are result models, which the emitter of the client models writes
        if (options.modelStyle() == ModelStyle.RECORD && options.mode() == Mode.SERVER) {
            for (ModelType type : ir.models()) {
                Names names = type.names();
                files.add(new GeneratedFile(path(names.packageName(), names.simpleName()), recordFile(type, options)));
                packages.add(names.packageName());
                modelPackages.add(names.packageName());
            }
        }
        for (NameClass nameClass : ir.nameClasses()) {
            files.add(new GeneratedFile(path(nameClass.packageName(), nameClass.simpleName()), nameClassFile(nameClass, options)));
            packages.add(nameClass.packageName());
        }
        for (String packageName : packages) {
            files.add(new GeneratedFile(path(packageName, "package-info"), packageInfo(packageName, modelPackages.contains(packageName), options)));
        }
        files.sort(Comparator.comparing(GeneratedFile::path));
        return files;
    }

    private static String path(String packageName, String simpleName) {
        return packageName.replace('.', '/') + "/" + simpleName + ".java";
    }

    private static String enumFile(EnumType type, Options options) {
        TypeSpec.Builder spec = TypeSpec.enumBuilder(type.names().simpleName()).addModifiers(Modifier.PUBLIC);
        documentation(spec, type.docs());
        generated(spec, options);
        for (EnumValue value : type.values()) {
            TypeSpec.Builder constant = TypeSpec.anonymousClassBuilder("");
            documentation(constant, value.docs());
            spec.addEnumConstant(value.name(), constant.build());
        }
        return write(type.names().packageName(), spec);
    }

    private static String packageInfo(String packageName, boolean holdsModels, Options options) {
        if (holdsModels && options.nullableAnnotation().startsWith(JSPECIFY + ".")) {
            return "@NullMarked\npackage " + packageName + ";\n\nimport " + JSPECIFY + ".NullMarked;\n";
        }
        return "package " + packageName + ";\n";
    }

    private static String recordFile(ModelType type, Options options) {
        Names names = type.names();
        TypeSpec.Builder spec = TypeSpec.recordBuilder(names.simpleName()).addModifiers(Modifier.PUBLIC);
        generated(spec, options);
        spec.addAnnotation(INTROSPECTED);
        if (!names.sdlName().equals(names.simpleName())) {
            spec.addAnnotation(AnnotationSpec.builder(GRAPHQL_TYPE).addMember("name", "$S", names.sdlName()).build());
        }
        List<Field> components = type.fields().stream().filter(field -> field.arguments().isEmpty()).toList();
        recordDocumentation(spec, type.docs(), components);
        for (Names implemented : type.implemented()) {
            spec.addSuperinterface(ClassName.get(implemented.packageName(), implemented.simpleName()));
        }
        for (Names member : type.memberOf()) {
            spec.addSuperinterface(ClassName.get(member.packageName(), member.simpleName()));
        }
        JavaTypes types = new JavaTypes(options.nullableAnnotation());
        for (Field field : components) {
            ParameterSpec.Builder component = ParameterSpec.builder(types.component(field), field.name());
            if (field.docs().deprecation() != null) {
                component.addAnnotation(DEPRECATED);
            }
            spec.addRecordComponent(component.build());
        }
        return write(names.packageName(), spec);
    }

    private static void recordDocumentation(TypeSpec.Builder spec, Docs docs, List<Field> components) {
        if (docs.description() != null) {
            spec.addJavadoc("$L\n", docs.description());
        }
        boolean separated = docs.description() == null;
        for (Field field : components) {
            String text = componentText(field.docs());
            if (text != null) {
                if (!separated) {
                    spec.addJavadoc("\n");
                    separated = true;
                }
                spec.addJavadoc("@param $L $L\n", field.name(), text);
            }
        }
        if (docs.deprecation() != null) {
            spec.addAnnotation(DEPRECATED);
            if (!separated) {
                spec.addJavadoc("\n");
            }
            spec.addJavadoc("@deprecated $L\n", docs.deprecation().reason());
        }
    }

    private static String componentText(Docs docs) {
        String reason = docs.deprecation() == null ? null : "Deprecated: " + docs.deprecation().reason();
        if (docs.description() == null) {
            return reason;
        }
        return reason == null ? docs.description() : docs.description() + "\n" + reason;
    }

    private static String nameClassFile(NameClass nameClass, Options options) {
        TypeSpec.Builder spec = TypeSpec.classBuilder(nameClass.simpleName()).addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        generated(spec, options);
        for (NameConstant constant : nameClass.constants()) {
            spec.addField(FieldSpec.builder(STRING, constant.name(), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$S", constant.value()).build());
        }
        spec.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());
        return write(nameClass.packageName(), spec);
    }

    private static String write(String packageName, TypeSpec.Builder spec) {
        return JavaFile.builder(packageName, spec.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build()
                .toString();
    }

    private static void generated(TypeSpec.Builder spec, Options options) {
        if (!options.generatedAnnotation().isEmpty()) {
            spec.addAnnotation(AnnotationSpec.builder(ClassName.bestGuess(options.generatedAnnotation()))
                    .addMember("value", "$S", GENERATOR).build());
        }
    }

    private static void documentation(TypeSpec.Builder builder, Docs docs) {
        if (docs.description() != null) {
            builder.addJavadoc("$L\n", docs.description());
        }
        if (docs.deprecation() != null) {
            builder.addAnnotation(DEPRECATED);
            builder.addJavadoc("@deprecated $L\n", docs.deprecation().reason());
        }
    }
}
