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
package io.micronaut.graphql.tools.benchmarks;

import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.model.JClass;
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassFindings;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources;
import io.micronaut.graphql.tools.binding.internal.schema.TypeExtensions;
import io.micronaut.graphql.tools.internal.model.RuntimeTypeModel;
import io.micronaut.graphql.tools.benchmarks.fixture.ClassElementFixture;
import io.micronaut.inject.BeanDefinition;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules give the same findings for the class element of the fixture (the compile-time form) as for the class that the same
 * sources compile to (the runtime form, read through its bean definition and introspection), for the three classes of the benchmark
 * and for a class that drifts from the schema, so that the comparison is not one of two empty lists.
 */
class BindingRulesParityTest {

    private static final List<String> CLASSES = List.of(ClassElementFixture.RESOLVER, ClassElementFixture.INPUT,
            ClassElementFixture.CONSTRUCTOR, ClassElementFixture.DRIFTED);

    @Test
    @SuppressWarnings("unchecked")
    void theFixtureAndTheCompiledClassGiveTheSameFindings() throws Exception {
        Map<String, List<String>> compileTime = new LinkedHashMap<>();
        String schemaText;
        Path classes;
        // the class files are written when the fixture releases its round, so the compiled classes are read after it is closed
        try (ClassElementFixture fixture = ClassElementFixture.open()) {
            CLASSES.forEach(className -> compileTime.put(className, codes(fixture.check(className))));
            schemaText = fixture.schemaText();
            classes = fixture.classes();
        }
        // the runtime form runs in a class loader of its own, as the corpus does: Micronaut's introspection lookup is a static that
        // only sees the class loader it was created in
        List<URL> urls = new ArrayList<>();
        urls.add(classes.toUri().toURL());
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            urls.add(new File(entry).toURI().toURL());
        }
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader())) {
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            thread.setContextClassLoader(loader);
            try {
                var method = loader.loadClass(RuntimeForm.class.getName()).getMethod("codes", String.class, String.class);
                for (String className : CLASSES) {
                    assertEquals(compileTime.get(className), (List<String>) method.invoke(null, schemaText, className), className);
                }
            } finally {
                thread.setContextClassLoader(previous);
            }
        }
        assertTrue(compileTime.get(ClassElementFixture.RESOLVER).isEmpty());
        assertTrue(compileTime.get(ClassElementFixture.INPUT).isEmpty());
        assertTrue(compileTime.get(ClassElementFixture.CONSTRUCTOR).isEmpty());
        assertFalse(compileTime.get(ClassElementFixture.DRIFTED).isEmpty(), "the drifted class must have a finding");
    }

    /** The runtime form of the rules: the class read through its bean definition and introspection. Public: it is called in another class loader. */
    public static final class RuntimeForm {

        private RuntimeForm() {
        }

        public static List<String> codes(String schemaText, String className) throws Exception {
            TypeDefinitionRegistry registry = new SchemaParser().parse(schemaText);
            SchemaSources.addImplicitRoots(registry);
            SchemaModel schema = SchemaModel.load(TypeExtensions.merge(registry)).orElseThrow();
            int dot = className.lastIndexOf('.');
            String definition = className.substring(0, dot + 1) + "$" + className.substring(dot + 1) + "$Definition";
            BeanDefinition<?> bean = (BeanDefinition<?>) Class.forName(definition).getConstructor().newInstance();
            RuntimeTypeModel model = new RuntimeTypeModel();
            JClass resolver = model.of(bean);
            return BindingRulesParityTest.codes(ResolverClassFindings.check(schema, model, resolver, false));
        }
    }

    static List<String> codes(List<Finding> findings) {
        return findings.stream().map(it -> it.code() + " " + it.coordinate()).toList();
    }
}
