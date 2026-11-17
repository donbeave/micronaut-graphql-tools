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
    private static final String DEFAULT_LEG = "21.3";

    private static BenchmarkCases cases;
    private static Baselines baselines;
    private static final String LEG = System.getProperty("allocation.graphqlJavaVersion", DEFAULT_LEG);
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

    private void check(String name) throws Exception {
        boolean wholeQuery = BenchmarkCases.isWholeQuery(name);
        BenchmarkCases.Op op = cases.op(name);

        double measured = wholeQuery
                ? AllocationMeter.bytesPerOperation(op, 1_000, 100, 5)
                : AllocationMeter.bytesPerOperation(op, 3_000_000, 1_000_000, 5);
        String key = Baselines.key(name, LEG, JDK);
        System.out.printf("allocation %s = %.2f B/op%n", key, measured);

        if (name.equals(BenchmarkCases.GRAPHQL_JAVA_PROPERTY_FETCH)) {
            return;
        }
        if (UPDATE) {
            baselines.put(key, measured);
            return;
        }
        Assumptions.assumeTrue(baselines.hasJdk(JDK), "No allocation baselines are recorded for JDK " + JDK);
        Double baseline = baselines.get(key);
        assertNotNull(baseline, "No allocation baseline for " + key + ". Record it with "
                + "./gradlew :benchmarks:allocationTest -Pallocation.update");

        if (wholeQuery) {
            if (!LEG.equals(DEFAULT_LEG)) {
                return;
            }
            double limit = baseline * QUERY_TOLERANCE_FACTOR;
            assertTrue(measured <= limit, String.format(java.util.Locale.ROOT,
                    "%s allocates %.2f B/op, over its baseline %.2f B/op x %.2f = %.2f. Remove the allocation, or if "
                            + "it is intended run ./gradlew :benchmarks:allocationTest -Pallocation.update and "
                            + "commit the new baseline.", key, measured, baseline, QUERY_TOLERANCE_FACTOR, limit));
        } else {
            double limit = baseline + LIBRARY_TOLERANCE_BYTES;
            assertTrue(measured <= limit, String.format(java.util.Locale.ROOT,
                    "%s allocates %.2f B/op, over its baseline %.2f B/op + %.1f = %.2f. Remove the allocation, or if "
                            + "it is intended run ./gradlew :benchmarks:allocationTest -Pallocation.update and "
                            + "commit the new baseline.", key, measured, baseline, LIBRARY_TOLERANCE_BYTES, limit));
        }
    }
}
