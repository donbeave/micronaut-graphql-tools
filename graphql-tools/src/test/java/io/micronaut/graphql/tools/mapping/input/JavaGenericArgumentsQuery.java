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
package io.micronaut.graphql.tools.mapping.input;

import jakarta.inject.Singleton;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Requires(property = "spec.name", value = "InputGenericArgumentsSpecJava")
@Singleton
@GraphQLRootResolver
public class JavaGenericArgumentsQuery {

    public String countries(List<JavaPriceSelection> selections) {
        List<String> result = new ArrayList<>();
        for (JavaPriceSelection selection : selections) {
            result.add(selection.getCountry());
        }
        return String.join(" ", result);
    }

    public String regions(List<? extends JavaPriceSelection> selections) {
        List<String> result = new ArrayList<>();
        for (JavaPriceSelection selection : selections) {
            result.add(selection.getClass().getSimpleName() + ":" + selection.getCountry());
        }
        return String.join(" ", result);
    }

    public String supers(List<? super JavaPriceSelection> selections) {
        List<String> result = new ArrayList<>();
        for (Object selection : selections) {
            result.add(selection.getClass().getSimpleName() + ":" + ((JavaPriceSelection) selection).getCountry());
        }
        return String.join(" ", result);
    }

    public String markets(Collection<JavaPriceSelection> selections) {
        List<String> result = new ArrayList<>();
        for (JavaPriceSelection selection : selections) {
            result.add(selection.getCountry());
        }
        return selections.getClass().getSimpleName() + ":" + String.join(" ", result);
    }

}
