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
package io.micronaut.graphql.tools.buildtools;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every sample of the Maven plugin proves, with the real {@code mvn} and the plugin published to the build repository: the
 * documented {@code <plugin>} snippet runs the {@code generate} goal at {@code generate-sources}, the directory of the language is
 * compiled with the project, an unchanged run is skipped, a changed input runs again, a plugin property reaches the configuration, and
 * a missing option fails with its diagnostic.
 *
 * <p>The generator emits no file in this version, so each sample puts one source file of its language into the generated directory
 * with the resources plugin before the goal runs. The goal leaves it alone (it is not in the manifest) and the compiler has to
 * find it through the registered source root.</p>
 */
abstract class MavenPluginSampleContract {

    private static final String GENERATED = "target/generated-sources/graphql-tools";
    private static final String SCHEMA = "src/main/resources/graphql/schema.graphqls";
    private static final String UP_TO_DATE = "The generated GraphQL sources in";
    private static final String GENERATED_NOTE = "Generated GraphQL sources in";

    /** The directory name of the sample under {@code samples}. */
    abstract String sample();

    @Test
    void theDocumentedSnippetGeneratesAndCompiles() {
        MavenSample sample = MavenSample.copyOf(sample(), "compile");

        MavenSample.Result built = sample.run("compile");

        assertEquals(0, built.exitCode(), built.output());
        assertTrue(built.output().contains(GENERATED_NOTE), "the goal ran at generate-sources:\n" + built.output());
        assertTrue(java.nio.file.Files.isRegularFile(sample.file(GENERATED + "/.graphql-tools-generated")), "the manifest is written");
        assertTrue(java.nio.file.Files.isRegularFile(sample.file("target/classes/placed/Placed.class")),
                "the source file in the generated directory was compiled:\n" + built.output());
        assertTrue(java.nio.file.Files.isRegularFile(sample.file("target/classes/example/Main.class")), built.output());
    }

    @Test
    void anUnchangedRunIsSkippedAndAChangedSchemaRunsAgain() {
        MavenSample sample = MavenSample.copyOf(sample(), "incremental");
        Path manifest = sample.file(GENERATED + "/.graphql-tools-generated");

        assertEquals(0, sample.run("compile").exitCode());
        String first = Files.read(manifest);

        MavenSample.Result unchanged = sample.run("compile");
        assertEquals(0, unchanged.exitCode(), unchanged.output());
        assertTrue(unchanged.output().contains(UP_TO_DATE) && unchanged.output().contains("are up to date"), unchanged.output());
        assertFalse(unchanged.output().contains(GENERATED_NOTE), unchanged.output());
        assertEquals(first, Files.read(manifest));

        Files.replace(sample.file(SCHEMA), "hello: String", "hello: String\n  other: Int");
        MavenSample.Result changed = sample.run("compile");
        assertEquals(0, changed.exitCode(), changed.output());
        assertTrue(changed.output().contains(GENERATED_NOTE), changed.output());
        assertNotEquals(first, Files.read(manifest));
    }

    @Test
    void aPropertyOfTheGoalReachesTheConfiguration() {
        MavenSample sample = MavenSample.copyOf(sample(), "properties");
        Files.replace(sample.file("pom.xml"), "<packageName>example.api</packageName>", "");

        MavenSample.Result withoutPackage = sample.run("compile");
        assertNotEquals(0, withoutPackage.exitCode(), withoutPackage.output());
        assertTrue(withoutPackage.output().contains("[missing-option]"), withoutPackage.output());
        assertTrue(withoutPackage.output().contains("packageName"), withoutPackage.output());

        MavenSample.Result withProperty = sample.run("compile", "-Dmicronaut.graphql.tools.codegen.package.name=example.api");
        assertEquals(0, withProperty.exitCode(), withProperty.output());
    }

    @Test
    void skipRunsNothingAndRegistersNothing() {
        MavenSample sample = MavenSample.copyOf(sample(), "skip");

        MavenSample.Result skipped = sample.run("compile", "-Dmicronaut.graphql.tools.codegen.skip=true");

        assertTrue(skipped.output().contains("GraphQL code generation is skipped."), skipped.output());
        assertFalse(java.nio.file.Files.exists(sample.file(GENERATED + "/.graphql-tools-generated")));
    }

    @Test
    void aSyntaxErrorFailsTheBuildWithItsLocation() {
        MavenSample sample = MavenSample.copyOf(sample(), "syntax-error");
        Files.write(sample.file("src/main/resources/graphql/broken.graphqls"), "type Broken {\n");

        MavenSample.Result failed = sample.run("compile");

        assertNotEquals(0, failed.exitCode(), failed.output());
        assertTrue(failed.output().contains("[schema-syntax-error]"), failed.output());
        assertTrue(failed.output().contains("broken.graphqls"), failed.output());
    }

    private static final String MAPPINGS = "META-INF/micronaut-graphql-tools/type-mappings.properties";
    private static final String MAPPED_SCHEMA = "hello: String\n  pet: Pet\n  price: Money\n}\n\nscalar Money\n";

    /** Adds a jar with a type mappings file as a system-scope dependency, so that it is on the compile class path, and uses its types. */
    private static void dependOnMappingsJar(MavenSample sample) throws java.io.IOException {
        Path jar = sample.file("libs/types.jar");
        java.nio.file.Files.createDirectories(jar.getParent());
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(jar))) {
            zip.putNextEntry(new java.util.zip.ZipEntry(MAPPINGS));
            zip.write("Pet=org.lib.Pet\nMoney=org.lib.Money\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        String dependency = "<dependency><groupId>org.lib</groupId><artifactId>types</artifactId><version>1.0</version>"
                + "<scope>system</scope><systemPath>${project.basedir}/libs/types.jar</systemPath></dependency>";
        String pom = Files.read(sample.file("pom.xml"));
        Files.write(sample.file("pom.xml"), pom.contains("<dependencies>")
                ? pom.replace("<dependencies>", "<dependencies>" + dependency)
                : pom.replace("  <build>", "  <dependencies>" + dependency + "</dependencies>\n  <build>"));
        Files.replace(sample.file(SCHEMA), "hello: String\n}\n", MAPPED_SCHEMA);
    }

    @Test
    void aTypeThatADependencyMapsNeedsNoTypeMappingsEntry() throws java.io.IOException {
        MavenSample sample = MavenSample.copyOf(sample(), "dependency-mappings");
        dependOnMappingsJar(sample);

        MavenSample.Result built = sample.run("compile");

        assertEquals(0, built.exitCode(), built.output());
        assertTrue(Files.read(sample.file(GENERATED + "/resources/" + MAPPINGS)).contains("Money=org.lib.Money"), built.output());
        assertTrue(java.nio.file.Files.isRegularFile(sample.file("target/classes/" + MAPPINGS)),
                "the generated resource root is on the resources of the project:\n" + built.output());
    }

    @Test
    void withTypeMappingsFromDependenciesOffTheDependenciesAreNotRead() throws java.io.IOException {
        MavenSample sample = MavenSample.copyOf(sample(), "dependency-mappings-off");
        dependOnMappingsJar(sample);

        MavenSample.Result failed = sample.run("compile", "-Dmicronaut.graphql.tools.codegen.type.mappings.from.dependencies=false");

        assertNotEquals(0, failed.exitCode(), failed.output());
        assertTrue(failed.output().contains("[unknown-type]"), failed.output());
    }
}
