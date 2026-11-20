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
package example.scalars;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// tag::resolver[]
@Requires(env = {ScalarsFixtures.DEFAULTS, ScalarsFixtures.RENAMED})
@GraphQLRootResolver
public class ScalarsQuery {

    public LocalDate bookedOn(LocalDate date) {
        return date.plusDays(1);
    }

    public OffsetDateTime bookedAt(OffsetDateTime time) {
        return time.plusHours(1);
    }

    public Duration elapsed(Duration spent) {
        return spent.plusMinutes(30);
    }

    public Object payload(Object data) {
        return data;
    }

    public UUID ticket(UUID id) {
        return id;
    }

    public URL homepage(URL url) {
        return url;
    }

}
// end::resolver[]
