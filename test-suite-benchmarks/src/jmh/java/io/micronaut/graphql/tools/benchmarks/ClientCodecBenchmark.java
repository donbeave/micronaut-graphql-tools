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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeUnit;

/**
 * The codec of the GraphQL client: a request encoded, a response decoded with data, with its single root field unwrapped and with
 * errors (#684).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ClientCodecBenchmark extends CaseState {

    private BenchmarkCases.Op encodeRequest;
    private BenchmarkCases.Op decodeData;
    private BenchmarkCases.Op decodeUnwrap;
    private BenchmarkCases.Op decodeErrors;

    @Setup
    public void prepare() {
        encodeRequest = op(BenchmarkCases.CLIENT_ENCODE_REQUEST);
        decodeData = op(BenchmarkCases.CLIENT_DECODE_DATA);
        decodeUnwrap = op(BenchmarkCases.CLIENT_DECODE_UNWRAP);
        decodeErrors = op(BenchmarkCases.CLIENT_DECODE_ERRORS);
    }

    @Benchmark
    public Object clientEncodeRequest() throws Exception {
        return encodeRequest.run();
    }

    @Benchmark
    public Object clientDecodeData() throws Exception {
        return decodeData.run();
    }

    @Benchmark
    public Object clientDecodeUnwrap() throws Exception {
        return decodeUnwrap.run();
    }

    @Benchmark
    public Object clientDecodeErrors() throws Exception {
        return decodeErrors.run();
    }
}
