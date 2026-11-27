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

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The schema, the model classes, two feature modules with resolvers and the application are five modules. Each feature module
 * applies the processor and reads the schema of the schema module; what a module cannot see (a field that no resolver of any
 * module binds) fails the application when it starts.
 */
class MultiModuleGradleSampleTest extends GradleSampleContract {

    private static final String BOOKS = ":books:compileJava";
    private static final String AUTHORS = ":authors:compileJava";
    private static final String BOOK_QUERY = "books/src/main/java/example/books/BookQuery.java";
    private static final String AUTHOR_QUERY = "authors/src/main/java/example/authors/AuthorQuery.java";
    private static final String GENRE = "model/src/main/java/example/model/Genre.java";

    @Override
    boolean hasClient() {
        return false;
    }

    @Override
    String sample() {
        return "multi-module-gradle";
    }

    @Override
    String checkedTask() {
        return BOOKS;
    }

    @Override
    String buildTask() {
        return ":app:classes";
    }

    @Override
    String schemaFile() {
        return "schema/src/main/resources/graphql/schema.graphqls";
    }

    @Override
    String buildFile() {
        return "books/build.gradle.kts";
    }

    @Override
    boolean relocatable() {
        return true;
    }

    /**
     * Only the module that lost the option is built. Micronaut copies the options of a compilation into system properties and a
     * later compilation of the same process reads them when it sets none, so a module compiled after another one would be checked
     * against that module's schema directory instead of failing: every module that applies the processor must set the option.
     */
    @Override
    @Test
    void aMissingOptionFails() {
        GradleSample sample = GradleSample.copyOf(sample(), "missing-option-module");
        Files.removeTagged(sample.file(buildFile()), "schema-dirs");

        BuildResult failed = sample.buildAndFail(BOOKS);

        assertEquals(TaskOutcome.FAILED, GradleSample.outcome(failed, BOOKS));
        Expect.diagnostic(failed.getOutput(), "processor-schema-not-configured", "-");
    }

    /**
     * The module that forgot the option is built after a module that set it, in the same Gradle daemon: it must fail with its own
     * name and not be checked against the schema directory of the module before it.
     */
    @Test
    void aMissingOptionIsNotFilledInByAnEarlierModuleOfTheSameBuild() {
        GradleSample sample = GradleSample.copyOf(sample(), "missing-option-after-module");
        Files.removeTagged(sample.file(buildFile()), "schema-dirs");

        BuildResult failed = sample.buildAndFail(AUTHORS, BOOKS);

        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(failed, AUTHORS), "the module that set the option is checked");
        assertEquals(TaskOutcome.FAILED, GradleSample.outcome(failed, BOOKS));
        Expect.diagnostic(failed.getOutput(), "processor-schema-not-configured", "-");
        assertTrue(failed.getOutput().contains("books"), "the error names the module:\n" + failed.getOutput());
    }

    @Test
    void everyModuleCompilesAndTheApplicationStarts() {
        GradleSample sample = GradleSample.copyOf(sample(), "start");

        BuildResult started = sample.build(":app:run");

        Path schemaDirectory = sample.file(schemaFile()).getParent();
        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(started, BOOKS));
        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(started, AUTHORS));
        assertTrue(started.getOutput().contains("checked 1 resolver class against 1 schema file in [" + schemaDirectory + "]; 0 findings."),
                "the books module is checked against the schema of the schema module:\n" + started.getOutput());
        assertTrue(started.getOutput().contains("checked 2 resolver classes against 1 schema file in [" + schemaDirectory + "]; 0 findings."),
                "the authors module is checked against the same schema:\n" + started.getOutput());
        assertTrue(started.getOutput().contains("errors: []"), started.getOutput());
        assertTrue(started.getOutput().contains("data: {book={id=7, title=Title 7, author={name=Frank}}, books=[{id=1}], "
                + "authors=[{name=Frank, genre=FICTION}]}"), "a query reaches the resolvers of both modules:\n" + started.getOutput());
    }

    @Test
    void aSchemaEditRerunsOnlyTheModulesThatReadTheSchema() {
        GradleSample sample = GradleSample.copyOf(sample(), "schema-edit-modules");
        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(sample.build(buildTask()), BOOKS));

        Files.replace(sample.file(schemaFile()), "type Book {", "# a comment that changes the file\ntype Book {");
        BuildResult edited = sample.build(buildTask());

        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(edited, BOOKS));
        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(edited, AUTHORS));
        assertEquals(TaskOutcome.UP_TO_DATE, GradleSample.outcome(edited, ":model:compileJava"),
                "the model module does not apply the processor, so a schema edit leaves it alone");
        assertEquals(TaskOutcome.UP_TO_DATE, GradleSample.outcome(edited, ":app:compileJava"),
                "the application does not apply the processor, so a schema edit leaves it alone");
    }

    @Test
    void aWrongArgumentTypeInOneFeatureModuleFailsThatModuleAndNotTheOther() {
        GradleSample sample = GradleSample.copyOf(sample(), "wrong-type-books");
        Files.replace(sample.file(BOOK_QUERY), "books(Integer limit)", "books(int limit)");

        BuildResult failed = sample.buildAndFail("--continue", buildTask());

        assertEquals(TaskOutcome.FAILED, GradleSample.outcome(failed, BOOKS));
        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(failed, AUTHORS), "the other feature module is checked on its own");
        Expect.diagnostic(failed.getOutput(), "nullable-primitive", "Query.books(limit:)");
        assertTrue(failed.getOutput().contains("BookQuery.java"), "the error points at the source file of the module:\n" + failed.getOutput());
        assertNull(failed.task(":app:compileJava"), "the application is not compiled when a module it depends on fails");
    }

    @Test
    void aParameterThatBindsNoArgumentInTheOtherFeatureModuleFailsThatModule() {
        GradleSample sample = GradleSample.copyOf(sample(), "wrong-name-authors");
        Files.replace(sample.file(AUTHOR_QUERY), "Genre genre, AuthorFilter filter", "Genre kind, AuthorFilter filter");

        BuildResult failed = sample.buildAndFail("--continue", buildTask());

        assertEquals(TaskOutcome.FAILED, GradleSample.outcome(failed, AUTHORS));
        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(failed, BOOKS));
        Expect.diagnostic(failed.getOutput(), "argument-mismatch", "Query.authors");
    }

    @Test
    void aModelEditThatDriftsFromTheSchemaFailsTheModuleWhoseResolverTakesIt() {
        GradleSample sample = GradleSample.copyOf(sample(), "model-drift");
        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(sample.build(buildTask()), AUTHORS));

        Files.replace(sample.file(GENRE), "FICTION, POETRY", "FICTION, POETRY, DRAMA");
        BuildResult failed = sample.buildAndFail("--continue", buildTask());

        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(failed, ":model:compileJava"),
                "the model module has no resolver, so nothing checks it there");
        assertEquals(TaskOutcome.FAILED, GradleSample.outcome(failed, AUTHORS));
        assertNotEquals(TaskOutcome.FAILED, GradleSample.outcome(failed, BOOKS), "the books module takes no genre");
        Expect.diagnostic(failed.getOutput(), "unbound-enum-constant", "example.model.Genre#DRAMA");
    }

    @Test
    void aFieldWithoutAResolverInAnyModuleCompilesEverywhereAndFailsTheStartup() {
        GradleSample sample = GradleSample.copyOf(sample(), "no-resolver");
        Files.replace(sample.file(schemaFile()), "  books(limit: Int): [Book!]!", "  books(limit: Int): [Book!]!\n  stray: String");

        BuildResult failed = sample.buildAndFail(":app:run");

        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(failed, BOOKS),
                "a module never reports a field as unbound because the resolver is not in it");
        assertEquals(TaskOutcome.SUCCESS, GradleSample.outcome(failed, AUTHORS));
        assertEquals(TaskOutcome.FAILED, GradleSample.outcome(failed, ":app:run"));
        assertTrue(failed.getOutput().contains("Query.stray"), "the startup names the coordinate:\n" + failed.getOutput());
    }
}
