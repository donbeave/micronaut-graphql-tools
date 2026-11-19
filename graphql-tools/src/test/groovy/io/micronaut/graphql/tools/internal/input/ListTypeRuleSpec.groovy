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
package io.micronaut.graphql.tools.internal.input

import io.micronaut.graphql.tools.binding.internal.rules.InputTypeRules
import spock.lang.Specification

/** The list types that the startup converts into and the ones that the compile-time check accepts are the same. */
class ListTypeRuleSpec extends Specification {

    void "#type.name: the startup and the shared rule agree on whether a list can be converted into it"() {
        expect:
        ValueConverters.isSupportedList(type) == InputTypeRules.supportedList(type.name)

        where:
        type << [Iterable, Collection, List, Set, SortedSet, NavigableSet, Queue, Deque, AbstractCollection, AbstractList,
                 AbstractSequentialList, AbstractSet, ArrayList, LinkedList, HashSet, LinkedHashSet, TreeSet, ArrayDeque,
                 Vector, Stack, PriorityQueue, java.util.concurrent.ConcurrentLinkedQueue, java.util.concurrent.CopyOnWriteArrayList,
                 java.util.concurrent.LinkedBlockingDeque, java.util.concurrent.ConcurrentSkipListSet, EnumSet,
                 AbstractQueue, java.nio.file.Path]
    }
}
