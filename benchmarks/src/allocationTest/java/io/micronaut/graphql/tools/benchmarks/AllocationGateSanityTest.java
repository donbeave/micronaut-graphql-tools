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
package io.micronaut.graphql.tools.benchmarks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AllocationGateSanityTest {

    @Test
    void aMonoThatWasNotSubscribedToFails() {
        // the figures a local run printed before the benchmark subscribed: 56 B/op invoked, 109760 B/op direct
        assertThrows(AssertionError.class, () -> AbstractAllocationGateTest.requireExecuted("10 fields invoked", 56));
        assertThrows(AssertionError.class, () -> AbstractAllocationGateTest.requireNotBelow("The invocation", 56, "the direct execution", 109_760));
    }

    @Test
    void aQueryThatRanPasses() {
        assertDoesNotThrow(() -> AbstractAllocationGateTest.requireExecuted("10 fields invoked", 110_000));
        assertDoesNotThrow(() -> AbstractAllocationGateTest.requireNotBelow("The invocation", 110_000, "the direct execution", 109_760));
    }
}
