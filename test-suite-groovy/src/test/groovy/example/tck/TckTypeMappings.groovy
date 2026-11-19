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
package example.tck

import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary

/**
 * The mappings of a TCK scenario between the types of its schema and their classes, for the classes the binding cannot find
 * by itself: the members of a union and the implementations of an interface. A scenario with such classes provides one bean,
 * activated by {@code @Requires(property = "tck.scenario", value = "<id>")}, and strict mode then finds no unused mapping.
 */
@CompileStatic
interface TckTypeMappings {
    void register(SchemaMappingDictionary dictionary)
}
