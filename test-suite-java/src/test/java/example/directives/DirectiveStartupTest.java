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
package example.directives;

import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.graphql.tools.BindingManifest;
import io.micronaut.graphql.tools.exceptions.DuplicateDirectiveWiringException;
import io.micronaut.graphql.tools.exceptions.UnwiredDirectivesException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectiveStartupTest {

    private static final String UNWIRED_DIRECTIVES = "micronaut.graphql.tools.checks.unwired-directive";
    private static final String STRICT = "micronaut.graphql.tools.strict";
    private static final String INERT_DIRECTIVES = "micronaut.graphql.tools.allow.inert-directives";
    private static final String SCHEMA_CUSTOMIZER_DIRECTIVES = "micronaut.graphql.tools.allow.schema-customizer-directives";

    @Test
    void aDirectiveWiredByANamedAndAnEveryElementSchemaDirectiveFailsTheStartup() {
        Throwable cause = rootFailure(Map.of(), DirectiveFixtures.CONFLICT);

        DuplicateDirectiveWiringException e = assertInstanceOf(DuplicateDirectiveWiringException.class, cause);
        assertEquals(List.of("upper"), e.getDirectiveNames());
        assertTrue(e.getMessage().startsWith("[duplicate-directive-wiring] Directive @upper is wired by both a named and an every-element SchemaDirective bean"), e.getMessage());
    }

    @Test
    void anAppliedDirectiveWithoutAWiringFailsTheStartupByDefault() {
        UnwiredDirectivesException e = assertInstanceOf(UnwiredDirectivesException.class,
                rootFailure(Map.of(), DirectiveFixtures.UNWIRED));

        assertEquals(List.of("legacy"), e.getUnwiredDirectives());
        assertTrue(e.getMessage().startsWith("[unwired-directive] The schema applies directives that have no wiring: @legacy (Book)"), e.getMessage());
    }

    @Test
    void unwiredDirectivesIgnoreSkipsTheCheck() {
        try (ApplicationContext context = start(Map.of(UNWIRED_DIRECTIVES, "ignore"), DirectiveFixtures.UNWIRED)) {
            assertNotNull(context.getBean(GraphQLSchema.class));
            assertFalse(manifestLines(context).contains("!unwired-directive legacy"), manifestLines(context).toString());
        }
    }

    @Test
    void unwiredDirectivesWarnStartsAndRecordsTheDirectiveInTheManifest() {
        try (ApplicationContext context = start(Map.of(UNWIRED_DIRECTIVES, "warn"), DirectiveFixtures.UNWIRED)) {
            assertNotNull(context.getBean(GraphQLSchema.class));
            assertTrue(manifestLines(context).contains("!unwired-directive legacy"), manifestLines(context).toString());
        }
    }

    @Test
    void unwiredDirectivesFailFailsTheStartupEvenWithStrictOff() {
        Map<String, Object> properties = Map.of(UNWIRED_DIRECTIVES, "fail", STRICT, false);

        assertInstanceOf(UnwiredDirectivesException.class, rootFailure(properties, DirectiveFixtures.UNWIRED));
    }

    @Test
    void strictOffAloneStartsAndRecordsTheDirectiveInTheManifest() {
        try (ApplicationContext context = start(Map.of(STRICT, false), DirectiveFixtures.UNWIRED)) {
            assertTrue(manifestLines(context).contains("!unwired-directive legacy"), manifestLines(context).toString());
        }
    }

    @Test
    void anInertDirectiveStartsWithoutAWiring() {
        try (ApplicationContext context = start(Map.of(INERT_DIRECTIVES, List.of("legacy")), DirectiveFixtures.UNWIRED)) {
            assertNotNull(context.getBean(GraphQLSchema.class));
            assertFalse(manifestLines(context).contains("!unwired-directive legacy"), manifestLines(context).toString());
        }
    }

    @Test
    void aSchemaLevelDirectiveThatNoSchemaCustomizerIsListedForFailsTheStartup() {
        Map<String, Object> properties = Map.of(SCHEMA_CUSTOMIZER_DIRECTIVES, List.of("other"));

        UnwiredDirectivesException e = assertInstanceOf(UnwiredDirectivesException.class,
                rootFailure(properties, DirectiveFixtures.SCHEMA_UNLISTED));

        assertEquals(List.of("audit"), e.getUnwiredDirectives());
        assertTrue(e.getMessage().contains("@audit (schema definition)"), e.getMessage());
    }

    @Test
    void everyDirectiveOfASchemaLevelListStartsTheServer() {
        Map<String, Object> properties = Map.of(SCHEMA_CUSTOMIZER_DIRECTIVES, List.of("audit", "other"));

        try (ApplicationContext context = start(properties, DirectiveFixtures.SCHEMA_UNLISTED)) {
            assertNotNull(context.getBean(GraphQLSchema.class).getSchemaAppliedDirective("other"));
        }
    }

    private static Throwable rootFailure(Map<String, Object> properties, String environment) {
        BeanInstantiationException e = assertThrows(BeanInstantiationException.class, () -> {
            start(properties, environment).close();
        });
        Throwable cause = e;
        while (!(cause instanceof DuplicateDirectiveWiringException || cause instanceof UnwiredDirectivesException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static ApplicationContext start(Map<String, Object> properties, String environment) {
        Map<String, Object> all = new HashMap<>(properties);
        all.put("micronaut.server.port", -1);
        return ApplicationContext.run(EmbeddedServer.class, all, environment).getApplicationContext();
    }

    private static List<String> manifestLines(ApplicationContext context) {
        return context.getBean(BindingManifest.class).getLines();
    }

}
