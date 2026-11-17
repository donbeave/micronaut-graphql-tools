package io.micronaut.graphql.tools.benchmarks;

import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;

final class AllocationMeter {

    private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    // written by every operation, so that the JIT cannot prove the result unused and drop the allocation
    private static volatile Object sink;

    private AllocationMeter() {
    }

    static double bytesPerOperation(BenchmarkCases.Op op, int warmup, int operations, int rounds) throws Exception {
        for (int i = 0; i < warmup; i++) {
            sink = op.run();
        }
        double minimum = Double.MAX_VALUE;
        for (int round = 0; round <= rounds; round++) {
            long before = THREADS.getCurrentThreadAllocatedBytes();
            for (int i = 0; i < operations; i++) {
                sink = op.run();
            }
            long after = THREADS.getCurrentThreadAllocatedBytes();
            if (round > 0) {
                minimum = Math.min(minimum, (double) (after - before) / operations);
            }
        }
        return minimum;
    }
}
