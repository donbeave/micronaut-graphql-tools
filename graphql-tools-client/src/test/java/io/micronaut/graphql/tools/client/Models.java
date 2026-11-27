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
package io.micronaut.graphql.tools.client;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

final class Models {

    private Models() {
    }

    @Serdeable
    record Book(String title, int pages, Long id) {
    }

    @Serdeable
    record BookQuery(Book book) {
    }

    @Serdeable
    record Filter(String name, List<String> tags) {
    }

    @Serdeable
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Cat.class, name = "Cat"),
            @JsonSubTypes.Type(value = Dog.class, name = "Dog")
    })
    interface Pet {
    }

    @Serdeable
    record Cat(String name) implements Pet {
    }

    @Serdeable
    record Dog(String name) implements Pet {
    }
}
