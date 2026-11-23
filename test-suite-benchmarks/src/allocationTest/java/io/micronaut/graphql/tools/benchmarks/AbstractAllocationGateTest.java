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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AbstractAllocationGateTest {

    private static final double LIBRARY_TOLERANCE_BYTES = 0.5;
    private static final double QUERY_TOLERANCE_FACTOR = 1.05;
    private static final String DEFAULT_LEG = "default";
    // the first budget of the constant per-request overhead of the invocation; the recorded baseline holds after that
    private static final double INVOCATION_OVERHEAD_BUDGET_BYTES = 1024;
    // a registered but unused loader costs nothing per request: at most this much for each loader beyond the first
    private static final double UNUSED_LOADER_BYTES = 0.5;
    private static final int UNUSED_LOADERS = 50;
    // what the async boundary adds to a field whose future completes on another thread (#428)
    private static final double ASYNC_BOUNDARY_BUDGET_BYTES = 128;
    // a request of 10 root fields allocates over 100 KB through graphql-java; a Mono that is built and not subscribed
    // allocates tens of bytes, so a figure below this means that the query did not run
    static final double MINIMUM_EXECUTED_QUERY_BYTES = 8192;

    private static BenchmarkCases cases;
    private static Baselines baselines;
    private static final String LEG = System.getProperty("allocation.graphqlJavaLeg", DEFAULT_LEG);
    private static final String JDK = String.valueOf(Runtime.version().feature());
    private static final boolean UPDATE = Boolean.getBoolean("allocation.update");

    @BeforeAll
    static void start() throws Exception {
        baselines = new Baselines(Path.of(System.getProperty("allocation.baselines")));
        cases = new BenchmarkCases();
    }

    @AfterAll
    static void stop() throws Exception {
        cases.close();
        if (UPDATE) {
            baselines.write();
        }
    }

    abstract String operation();

    @Test
    void allocationStaysWithinBaseline() throws Exception {
        check(operation());
    }

    /** The bytes the library's invocation allocates on the calling thread beyond a direct execution of the same query. */
    private static double invocationOverhead(int fields) throws Exception {
        double invoked = AllocationMeter.bytesPerOperation(cases.op(BenchmarkCases.invokeOp(fields)), 200_000 / Math.max(1, fields / 10), 20_000 / Math.max(1, fields / 10), 5);
        double direct = AllocationMeter.bytesPerOperation(cases.op(BenchmarkCases.directOp(fields)), 200_000 / Math.max(1, fields / 10), 20_000 / Math.max(1, fields / 10), 5);
        System.out.printf("allocation invoke %d fields = %.2f B/op, direct = %.2f B/op%n", fields, invoked, direct);
        requireExecuted(fields + " fields invoked", invoked);
        requireExecuted(fields + " fields direct", direct);
        requireNotBelow("The invocation of " + fields + " fields", invoked, "the direct execution it wraps", direct);
        return invoked - direct;
    }

    /** Fails when a measured query allocated too little to have run. */
    static void requireExecuted(String what, double bytes) {
        assertTrue(bytes >= MINIMUM_EXECUTED_QUERY_BYTES, String.format(java.util.Locale.ROOT,
                "The query of %s allocates %.2f B/op, below the %.0f B/op that a query that ran needs: the benchmarked operation "
                        + "does not run the query (a Mono that is not subscribed to does no work).", what, bytes,
                MINIMUM_EXECUTED_QUERY_BYTES));
    }

    /** Fails when the wrapping measure is below what it wraps: a negative overhead is not a measurement. */
    static void requireNotBelow(String what, double bytes, String floorWhat, double floor) {
        assertTrue(bytes >= floor, String.format(java.util.Locale.ROOT,
                "%s allocates %.2f B/op, below %s at %.2f B/op: a negative overhead of %.2f B/op.", what, bytes, floorWhat,
                floor, bytes - floor));
    }

    /**
     * The bytes that a request allocates on the calling thread with 50 loader beans that it does not use, minus those with
     * one: the cost of a registered but unused loader.
     */
    private static double loaderOverhead() throws Exception {
        try (BenchmarkCases fifty = new BenchmarkCases(50); BenchmarkCases one = new BenchmarkCases(1)) {
            double many = AllocationMeter.bytesPerOperation(fifty.op(BenchmarkCases.invokeOp(BenchmarkCases.SMALL_QUERY_FIELDS)), 200_000, 20_000, 5);
            double single = AllocationMeter.bytesPerOperation(one.op(BenchmarkCases.invokeOp(BenchmarkCases.SMALL_QUERY_FIELDS)), 200_000, 20_000, 5);
            System.out.printf("allocation with 50 loader beans = %.2f B/op, with 1 = %.2f B/op%n", many, single);
            return many - single;
        }
    }

    /**
     * The bytes per field that a request allocates with the metrics and tracing modules on and no field traced, beyond the same request
     * with an instrumentation that implements no hook: the overhead of a query of 1,000 fields minus that of 10 fields. The empty
     * instrumentation is the control, because graphql-java 21 chains any instrumentation with its own and that costs each field
     * whatever the instrumentation is. A slope of 0 B per field leaves 0.
     */
    private static double observedSlope() throws Exception {
        try (BenchmarkCases observed = new BenchmarkCases(0, BenchmarkCases.Instrumented.OBSERVABILITY);
             BenchmarkCases empty = new BenchmarkCases(0, BenchmarkCases.Instrumented.EMPTY)) {
            double large = observedOverhead(observed, empty, BenchmarkCases.LARGE_QUERY_FIELDS);
            double small = observedOverhead(observed, empty, BenchmarkCases.SMALL_QUERY_FIELDS);
            return large - small;
        }
    }

    private static double observedOverhead(BenchmarkCases observed, BenchmarkCases empty, int fields) throws Exception {
        String op = BenchmarkCases.invokeOp(fields);
        int measured = 200_000 / Math.max(1, fields / 10);
        int warmup = 20_000 / Math.max(1, fields / 10);
        double with = AllocationMeter.bytesPerOperation(observed.op(op), measured, warmup, 5);
        double control = AllocationMeter.bytesPerOperation(empty.op(op), measured, warmup, 5);
        System.out.printf("allocation %d fields: observed = %.2f B/op, empty instrumentation = %.2f B/op%n", fields, with, control);
        requireExecuted(fields + " fields with observability", with);
        requireExecuted(fields + " fields with the empty instrumentation", control);
        return with - control;
    }

    /**
     * The bytes that a field allocates with the security module installed. An unsecured field fails the gate when it is above what
     * the same field allocates without the module, the root method of the base case. A secured field that passes has a baseline of
     * its own: the role check iterates the view that {@code Authentication.getRoles()} of Micronaut Security returns, which allocates.
     */
    private static double withSecurityModule(String name) throws Exception {
        try (BenchmarkCases secured = new BenchmarkCases(0, BenchmarkCases.Instrumented.NONE, BenchmarkCases.Decorators.UNSELECTIVE, true)) {
            double with = AllocationMeter.bytesPerOperation(secured.op(name), 3_000_000, 1_000_000, 5);
            double without = AllocationMeter.bytesPerOperation(cases.op(BenchmarkCases.ROOT_METHOD), 3_000_000, 1_000_000, 5);
            System.out.printf("allocation %s with the security module = %.2f B/op, the same field without it = %.2f B/op%n", name, with, without);
            assertTrue(!name.equals(BenchmarkCases.SECURITY_UNSECURED_FIELD) || with <= without + LIBRARY_TOLERANCE_BYTES, String.format(java.util.Locale.ROOT,
                    "%s allocates %.2f B/op with the security module installed, over the %.2f B/op of the same field without it.",
                    name, with, without));
            return with;
        }
    }

    private void check(String name) throws Exception {
        boolean wholeQuery = BenchmarkCases.isWholeQuery(name);

        double measured = switch (name) {
            case BenchmarkCases.INVOCATION_OVERHEAD -> invocationOverhead(BenchmarkCases.SMALL_QUERY_FIELDS);
            // the overhead of a query of 1,000 fields minus that of 10 fields: a slope of 0 B per field leaves 0
            case BenchmarkCases.INVOCATION_SLOPE -> invocationOverhead(BenchmarkCases.LARGE_QUERY_FIELDS)
                    - invocationOverhead(BenchmarkCases.SMALL_QUERY_FIELDS);
            case BenchmarkCases.LOADER_OVERHEAD -> loaderOverhead();
            case BenchmarkCases.OBSERVED_INVOCATION_SLOPE -> observedSlope();
            case BenchmarkCases.SECURITY_UNSECURED_FIELD, BenchmarkCases.SECURITY_SECURED_FIELD_PASSES -> withSecurityModule(name);
            // the fetcher of an async field with a pending future, less the future that the resolver itself creates
            case BenchmarkCases.ASYNC_BOUNDARY_OTHER_THREAD -> AllocationMeter.bytesPerOperation(cases.op(name), 3_000_000, 1_000_000, 5)
                    - AllocationMeter.bytesPerOperation(cases.op(BenchmarkCases.ASYNC_PENDING_SOURCE), 3_000_000, 1_000_000, 5);
            default -> wholeQuery
                    ? AllocationMeter.bytesPerOperation(cases.op(name), 1_000, 100, 5)
                    : AllocationMeter.bytesPerOperation(cases.op(name), 3_000_000, 1_000_000, 5);
        };
        String key = Baselines.key(name, LEG, JDK);
        System.out.printf("allocation %s = %.2f B/op%n", key, measured);

        if (name.equals(BenchmarkCases.GRAPHQL_JAVA_PROPERTY_FETCH)) {
            return;
        }
        if (name.equals(BenchmarkCases.INVOCATION_OVERHEAD)) {
            assertTrue(measured <= INVOCATION_OVERHEAD_BUDGET_BYTES, String.format(java.util.Locale.ROOT,
                    "The invocation adds %.2f B/op to a direct execution, over its budget of %.0f B/op.", measured,
                    INVOCATION_OVERHEAD_BUDGET_BYTES));
        }
        if (name.equals(BenchmarkCases.LOADER_OVERHEAD)) {
            assertTrue(measured <= UNUSED_LOADER_BYTES * (UNUSED_LOADERS - 1), String.format(java.util.Locale.ROOT,
                    "50 registered but unused loader beans add %.2f B/op to a request, over %.1f B x %d.", measured,
                    UNUSED_LOADER_BYTES, UNUSED_LOADERS - 1));
        }
        if (name.equals(BenchmarkCases.ASYNC_BOUNDARY_OTHER_THREAD)) {
            assertTrue(measured <= ASYNC_BOUNDARY_BUDGET_BYTES, String.format(java.util.Locale.ROOT,
                    "The async boundary adds %.2f B/op to a field whose future completes on another thread, over its budget of "
                            + "%.0f B/op.", measured, ASYNC_BOUNDARY_BUDGET_BYTES));
        }
        Assumptions.assumeTrue(UPDATE || !"latest".equals(LEG), "No allocation baselines are recorded for the latest leg");
        if (UPDATE) {
            baselines.put(key, measured);
            return;
        }
        Assumptions.assumeTrue(baselines.hasJdk(JDK), "No allocation baselines are recorded for JDK " + JDK);
        Double baseline = baselines.get(key);
        assertNotNull(baseline, "No allocation baseline for " + key + ". Record it with "
                + "./gradlew :test-suite-benchmarks:allocationTest -Pallocation.update");

        if (wholeQuery) {
            if (!LEG.equals(DEFAULT_LEG)) {
                return;
            }
            double limit = baseline * QUERY_TOLERANCE_FACTOR;
            assertTrue(measured <= limit, String.format(java.util.Locale.ROOT,
                    "%s allocates %.2f B/op, over its baseline %.2f B/op x %.2f = %.2f. Remove the allocation, or if "
                            + "it is intended run ./gradlew :test-suite-benchmarks:allocationTest -Pallocation.update and "
                            + "commit the new baseline.", key, measured, baseline, QUERY_TOLERANCE_FACTOR, limit));
        } else {
            double limit = baseline + LIBRARY_TOLERANCE_BYTES;
            assertTrue(measured <= limit, String.format(java.util.Locale.ROOT,
                    "%s allocates %.2f B/op, over its baseline %.2f B/op + %.1f = %.2f. Remove the allocation, or if "
                            + "it is intended run ./gradlew :test-suite-benchmarks:allocationTest -Pallocation.update and "
                            + "commit the new baseline.", key, measured, baseline, LIBRARY_TOLERANCE_BYTES, limit));
        }
    }
}
