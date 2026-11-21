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

import io.micronaut.graphql.tools.visitor.ClassElementFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bytes that the per-class binding rules allocate for each class of {@code BindingRulesBenchmark}, against the baselines. */
class BindingRulesAllocationTest {

    private static final double TOLERANCE_FACTOR = 1.05;
    private static final String LEG = System.getProperty("allocation.graphqlJavaLeg", "default");
    private static final String JDK = String.valueOf(Runtime.version().feature());
    private static final boolean UPDATE = Boolean.getBoolean("allocation.update");

    private static ClassElementFixture fixture;
    private static Baselines baselines;

    @BeforeAll
    static void start() throws Exception {
        baselines = new Baselines(Path.of(System.getProperty("allocation.baselines")));
        fixture = ClassElementFixture.open();
    }

    @AfterAll
    static void stop() throws Exception {
        fixture.close();
        if (UPDATE) {
            baselines.write();
        }
    }

    @Test
    void resolverClass() throws Exception {
        check(BindingRulesCases.RESOLVER, ClassElementFixture.RESOLVER);
    }

    @Test
    void inputClass() throws Exception {
        check(BindingRulesCases.INPUT, ClassElementFixture.INPUT);
    }

    @Test
    void constructorClass() throws Exception {
        check(BindingRulesCases.CONSTRUCTOR, ClassElementFixture.CONSTRUCTOR);
    }

    private static void check(String name, String className) throws Exception {
        double measured = AllocationMeter.bytesPerOperation(() -> fixture.check(className), 20_000, 5_000, 5);
        String key = Baselines.key(name, LEG, JDK);
        System.out.printf("allocation %s = %.2f B/op%n", key, measured);
        Assumptions.assumeTrue(UPDATE || !"latest".equals(LEG), "No allocation baselines are recorded for the latest leg");
        if (UPDATE) {
            baselines.put(key, measured);
            return;
        }
        Assumptions.assumeTrue(baselines.hasJdk(JDK), "No allocation baselines are recorded for JDK " + JDK);
        Double baseline = baselines.get(key);
        assertNotNull(baseline, "No allocation baseline for " + key + ". Record it with "
                + "./gradlew :benchmarks:allocationTest -Pallocation.update");
        double limit = baseline * TOLERANCE_FACTOR;
        assertTrue(measured <= limit, String.format(java.util.Locale.ROOT,
                "%s allocates %.2f B/op, over its baseline %.2f B/op x %.2f = %.2f. Remove the allocation, or if it is intended run "
                        + "./gradlew :benchmarks:allocationTest -Pallocation.update and commit the new baseline.",
                key, measured, baseline, TOLERANCE_FACTOR, limit));
    }
}
