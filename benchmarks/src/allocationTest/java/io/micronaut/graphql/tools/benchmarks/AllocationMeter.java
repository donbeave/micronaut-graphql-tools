package io.micronaut.graphql.tools.benchmarks;

import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;

/**
 * Bytes allocated per operation by the calling thread. It reads the thread's own allocation counter, not JMH's
 * process-wide one, so other threads (the compiler, the garbage collector) do not add to the figure.
 *
 * <p>Round 0 is discarded (it still includes compilation and cache fills the warmup missed). The result is the minimum
 * of rounds 1..n: allocation is deterministic once warm, and a noisy round can only add bytes.
 */
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
