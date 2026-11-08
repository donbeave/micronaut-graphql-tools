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

import graphql.schema.idl.TypeDefinitionRegistry;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks the Java bindings of a compilation against the GraphQL schema files at compile time and fails javac with an
 * error naming the schema coordinate and the Java element for each drift the startup would report, so that it fails
 * the build instead of the startup.
 *
 * <p>The processor is aggregating: the checks need every resolver bean and model class of the compilation, so it
 * collects the types of every round and checks once, in the last one. It claims the annotations of the library and the
 * Micronaut annotations that make a class a bean, never {@code *}. The schema files are read from the directory
 * {@code -Amicronaut.graphql.tools.sdlDir}, which the build must declare as an input of the compilation. The other
 * options have the meaning and the default of the runtime options of the same name.</p>
 */
public final class GraphQLSchemaBindingProcessor extends AbstractProcessor {

    private final Set<String> typeNames = new LinkedHashSet<>();
    private int round;

    /**
     * Creates the processor; javac does, through {@code META-INF/services/javax.annotation.processing.Processor}.
     */
    public GraphQLSchemaBindingProcessor() {
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Annotations.CLAIMED;
    }

    @Override
    public Set<String> getSupportedOptions() {
        return ProcessorOptions.NAMES;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        round++;
        for (TypeElement type : ElementFilter.typesIn(roundEnv.getRootElements())) {
            if (round > 1 && type.getSimpleName().toString().startsWith("$")) {
                // generated in this compilation by another processor, under the $ prefix that Micronaut gives its bean
                // definitions, introspections and AOP proxies: not a class of the application
                continue;
            }
            collect(type);
        }
        if (roundEnv.processingOver()) {
            check();
        }
        return false;
    }

    private void collect(TypeElement type) {
        typeNames.add(type.getQualifiedName().toString());
        for (TypeElement nested : ElementFilter.typesIn(type.getEnclosedElements())) {
            collect(nested);
        }
    }

    private void check() {
        Problems problems = new Problems();
        ProcessorOptions options = ProcessorOptions.read(processingEnv.getOptions(), problems);

        List<TypeElement> types = new ArrayList<>();
        for (String name : typeNames) {
            TypeElement type = processingEnv.getElementUtils().getTypeElement(name);
            if (type != null) {
                types.add(type);
            }
        }
        JavaTypes javaTypes = new JavaTypes(processingEnv);

        if (options.sdlDir() == null) {
            if (types.stream().anyMatch(javaTypes::isGraphQLType)) {
                problems.error(ProcessorOptions.SDL_DIR + " is not set. The compilation has GraphQL resolvers or "
                        + "types to check against the schema: pass the directory of the schema files with "
                        + "-A" + ProcessorOptions.SDL_DIR + "=<dir> and declare it as an input of the compilation.");
            }
            problems.report(processingEnv.getMessager());
            return;
        }

        TypeDefinitionRegistry registry = SchemaFiles.read(options.sdlDir(), problems);
        if (registry == null) {
            problems.report(processingEnv.getMessager());
            return;
        }
        // the checks see each type with the fields of its extensions
        TypeDefinitionRegistry checked = TypeExtensions.merge(registry);
        if (new SchemaChecks(checked, options, problems).check()) {
            if (types.stream().anyMatch(javaTypes::isResolver)) {
                BindingChecks bindingChecks = new BindingChecks(checked, options, problems, javaTypes, types);
                bindingChecks.check(checked.schemaDefinition().orElseThrow());
                bindingChecks.skipped().forEach(problems::note);
            } else {
                problems.error(ProcessorOptions.SDL_DIR + " is set, but the compilation has no root or type resolver to "
                        + "check against the schema. Apply the processor in the module that holds the resolver beans, or "
                        + "do not set " + ProcessorOptions.SDL_DIR + " here.");
            }
        }
        problems.report(processingEnv.getMessager());
    }

}
