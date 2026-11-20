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
 * The marker interfaces of resolver contracts: {@link io.micronaut.graphql.tools.resolver.QueryResolver},
 * {@link io.micronaut.graphql.tools.resolver.MutationResolver}, {@link io.micronaut.graphql.tools.resolver.SubscriptionResolver}
 * and {@link io.micronaut.graphql.tools.resolver.TypeResolver}.
 *
 * <p><b>Audience.</b> Public API. Application code, and the code generators that emit one contract per field or type,
 * import every type of this package. The interfaces hold no method and no bean stereotype: an implementation is a bean
 * only when it carries a scope annotation such as {@code @Singleton}.</p>
 */
package io.micronaut.graphql.tools.resolver;
