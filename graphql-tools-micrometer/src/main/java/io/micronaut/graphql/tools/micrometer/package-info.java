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
/**
 * The names that the Micrometer module uses for its meters and tags:
 * {@link io.micronaut.graphql.tools.micrometer.GraphQLMetrics} lists them, so that a dashboard, an alert or a test refers to a
 * constant. The module records one timer for each request (tagged by operation name and outcome) and DataLoader batch
 * counters; the keys under {@code micronaut.metrics.binders.graphql} configure them.
 *
 * <p><b>Audience.</b> Public API. An application reads the constants; it never calls into the implementation.</p>
 */
package io.micronaut.graphql.tools.micrometer;
