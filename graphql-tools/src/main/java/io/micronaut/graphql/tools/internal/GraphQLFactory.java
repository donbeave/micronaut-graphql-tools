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
package io.micronaut.graphql.tools.internal;

import graphql.GraphQL;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLSchema;
import graphql.schema.visibility.NoIntrospectionGraphqlFieldVisibility;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.manifest.BindingManifest;
import io.micronaut.graphql.tools.customizer.GraphQLBuilderCustomizer;
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration;
import io.micronaut.graphql.tools.internal.protection.DocumentCache;
import io.micronaut.graphql.tools.internal.protection.GetMutationGuard;
import io.micronaut.graphql.tools.internal.error.LibraryDataFetcherExceptionHandler;
import io.micronaut.graphql.tools.internal.manifest.BindingManifestHolder;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the {@link GraphQLSchema} bound from the application's {@link TypeDefinitionRegistry} and the
 * {@link GraphQL} bean that runs it. Each of them steps aside when the application provides an unqualified or
 * {@code @Primary} bean of that type: an application that builds its own {@code GraphQL} (instrumentation, execution
 * strategies, transport) consumes the library's {@code GraphQLSchema} bean, and an application that provides its own
 * {@code GraphQLSchema} gets no binding from the library at all. A bean with a qualifier (for example
 * {@code @Named("admin")}) does not make them step aside; the library's beans are {@code @Primary}, so an unqualified
 * injection point still gets them.
 *
 * <p>The selection is the library's own ({@code LibraryBeanSelection}) and not {@code @Secondary} with
 * {@code @Requires(missingBeans)}, because {@code missingBeans} counts a qualified bean: with one
 * {@code @Named("admin")} schema the library's schema would disappear and every unqualified injection point would get the
 * admin schema, and with {@code @EachProperty} or {@code @EachBean} beans the first injection would fail with a
 * {@code NonUniqueBeanException}. Revisit when Micronaut's {@code missingBeans} can ignore qualified beans. The
 * {@code GraphQLInvocation} needs no such rule and uses {@code @Secondary}.</p>
 *
 * <p>The {@code GraphQL} bean installs the application's {@code Instrumentation} beans: none when there is no bean, the bean
 * itself when there is one, and a {@code ChainedInstrumentation} in bean order when there are several. The
 * {@code GraphQLBuilderCustomizer} beans run after that. The one bean that the library adds itself is
 * {@code ObserverInstrumentation}, which exists when a module contributes a {@code RequestObserver} bean (the metrics and
 * the tracing modules do), however many observers there are.</p>
 *
 * <p>The bean also applies the {@code protection} settings: the parsed-document cache, the depth and complexity limits (two
 * more instrumentations) and the introspection switch.</p>
 *
 * @author Alexey Zhokhov
 */
@Internal
@Factory
public final class GraphQLFactory {

    @Singleton
    @Primary
    @Requires(condition = LibraryBeanSelection.NoCompetingSchema.class)
    public GraphQLSchema graphQLSchema(SchemaBinder schemaBinder) {
        return schemaBinder.bind();
    }

    @Singleton
    @Primary
    @Requires(condition = LibraryBeanSelection.NoCompetingGraphQL.class)
    public GraphQL graphQL(GraphQLSchema graphQLSchema, List<GraphQLBuilderCustomizer> graphQLBuilderCustomizers,
                           LibraryDataFetcherExceptionHandler exceptionHandler, List<Instrumentation> instrumentations,
                           GraphQLToolsConfiguration configuration) {
        GraphQLToolsConfiguration.ProtectionConfiguration protection = configuration.getProtection();
        if (!protection.isIntrospection()) {
            GraphQLSchema bound = graphQLSchema;
            graphQLSchema = bound.transform(schema -> schema.codeRegistry(bound.getCodeRegistry()
                    .transform(registry -> registry.fieldVisibility(NoIntrospectionGraphqlFieldVisibility.NO_INTROSPECTION_FIELD_VISIBILITY))));
        }
        GraphQL.Builder builder = GraphQL.newGraphQL(graphQLSchema)
                .defaultDataFetcherExceptionHandler(exceptionHandler);
        if (protection.getDocumentCacheSize() > 0) {
            builder.preparsedDocumentProvider(new DocumentCache(protection.getDocumentCacheSize()));
        }
        List<Instrumentation> chain = new ArrayList<>(instrumentations);
        if (protection.getMaxDepth() > 0) {
            chain.add(new MaxQueryDepthInstrumentation(protection.getMaxDepth()));
        }
        if (protection.getMaxComplexity() > 0) {
            chain.add(new MaxQueryComplexityInstrumentation(protection.getMaxComplexity()));
        }
        if (chain.size() == 1) {
            builder.instrumentation(chain.get(0));
        } else if (!chain.isEmpty()) {
            builder.instrumentation(new ChainedInstrumentation(chain));
        }
        graphQLBuilderCustomizers.forEach(customizer -> customizer.customize(builder));
        GraphQL graphQL = builder.build();
        PreparsedDocumentProvider provider = graphQL.getPreparsedDocumentProvider();
        return provider instanceof GetMutationGuard ? graphQL
                : graphQL.transform(guarded -> guarded.preparsedDocumentProvider(new GetMutationGuard(provider)));
    }

    /**
     * @param graphQLSchema         the library's schema, so that the schema is bound first
     * @param bindingManifestHolder the manifest recorded while the schema was bound
     * @return what the schema is bound to
     */
    @Singleton
    public BindingManifest bindingManifest(GraphQLSchema graphQLSchema, BindingManifestHolder bindingManifestHolder) {
        return bindingManifestHolder.get();
    }

}
