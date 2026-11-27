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
package io.micronaut.graphql.tools.gradle;

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Passes {@code graphqlTools.schemaDirs} to the compile-time processor for the compiler that the project uses: {@code javac},
 * KSP, kapt and the Groovy compiler. A compiler is wired only when the processor is declared for it, so a project that only
 * generates code gets no unused compiler option. Kotlin's own plugins are reached by name and reflection, so the plugin has no
 * dependency on them.
 */
final class CompileTimeWiring {

    static final String SCHEMA_DIRS = "micronaut.graphql.tools.processor.schema.dirs";
    static final String MODULE_DIR = "micronaut.graphql.tools.processor.module.dir";

    private static final String PROCESSOR = "micronaut-graphql-tools-processor";

    private CompileTimeWiring() {
    }

    static void wire(Project project, GraphQLToolsExtension extension) {
        project.afterEvaluate(evaluated -> {
            FileCollection dirs = extension.getSchemaDirs();
            if (declares(evaluated, "annotationProcessor")) {
                evaluated.getTasks().matching(task -> task.getName().equals("compileJava")).configureEach(task -> {
                    SchemaDirsArguments arguments = evaluated.getObjects().newInstance(SchemaDirsArguments.class);
                    arguments.getDirs().from(dirs);
                    arguments.getModuleDir().set(evaluated.getLayout().getProjectDirectory());
                    ((JavaCompile) task).getOptions().getCompilerArgumentProviders().add(arguments);
                });
            }
            if (declares(evaluated, "compileOnly")) {
                evaluated.getTasks().withType(GroovyCompile.class).matching(task -> task.getName().equals("compileGroovy")).configureEach(task -> {
                    task.getOptions().getForkOptions().getJvmArgs().add("-D" + SCHEMA_DIRS + "=" + joined(dirs.getFiles()));
                    task.getOptions().getForkOptions().getJvmArgs().add("-D" + MODULE_DIR + "=" + evaluated.getProjectDir().getAbsolutePath());
                    track(task, dirs);
                });
            }
            if (declares(evaluated, "ksp")) {
                Object ksp = evaluated.getExtensions().findByName("ksp");
                if (ksp != null) {
                    invoke(ksp, "arg", SCHEMA_DIRS, joined(dirs.getFiles()));
                    invoke(ksp, "arg", MODULE_DIR, evaluated.getProjectDir().getAbsolutePath());
                    evaluated.getTasks().matching(task -> task.getName().startsWith("ksp") && task.getName().endsWith("Kotlin"))
                            .configureEach(task -> track(task, dirs));
                }
            }
            if (declares(evaluated, "kapt")) {
                Object kapt = evaluated.getExtensions().findByName("kapt");
                if (kapt != null) {
                    arguments(kapt, SCHEMA_DIRS, joined(dirs.getFiles()));
                    arguments(kapt, MODULE_DIR, evaluated.getProjectDir().getAbsolutePath());
                    evaluated.getTasks().matching(task -> task.getName().equals("kaptKotlin") || task.getName().equals("kaptGenerateStubsKotlin"))
                            .configureEach(task -> track(task, dirs));
                }
            }
        });
    }

    static String joined(Collection<File> files) {
        return files.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
    }

    private static boolean declares(Project project, String configuration) {
        var found = project.getConfigurations().findByName(configuration);
        return found != null && found.getAllDependencies().stream().anyMatch(dependency -> PROCESSOR.equals(dependency.getName()));
    }

    private static void track(org.gradle.api.Task task, FileCollection dirs) {
        task.getInputs().files(dirs).withPropertyName("graphqlSchemaDirs").withPathSensitivity(PathSensitivity.RELATIVE);
    }

    private static void arguments(Object kapt, String key, String value) {
        invoke(kapt, "arguments", new Closure<Object>(kapt) {
            @SuppressWarnings("unused")
            public Object doCall(Object... ignored) {
                invoke(getDelegate(), "arg", key, new Object[] {value});
                return null;
            }
        });
    }

    private static void invoke(Object target, String name, Object... arguments) {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arguments.length && accepts(method, arguments)) {
                try {
                    method.invoke(target, arguments);
                    return;
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("Cannot call " + name + " on " + target.getClass().getName(), e);
                }
            }
        }
        throw new IllegalStateException(target.getClass().getName() + " has no method " + name + " for the plugin to call");
    }

    private static boolean accepts(Method method, Object[] arguments) {
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (!types[i].isInstance(arguments[i])) {
                return false;
            }
        }
        return true;
    }
}
