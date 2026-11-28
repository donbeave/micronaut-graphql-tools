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
package example.graphql.query;

import example.AbstractTest;
import example.client.ExampleClient.User;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two clients send the X-User header with different values at the same moment, over and over: each response must carry only
 * its own user. A singleton that kept the header value in a field would hand one request the user of the other.
 */
@MicronautTest
class ConcurrentCurrentUserTest extends AbstractTest {

    private static final int ROUNDS = 200;

    @Test
    void concurrentRequestsEachSeeOnlyTheirOwnUser() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<String>> testUsers = pool.submit(users("test", start));
            Future<List<String>> bobUsers = pool.submit(users("bob", start));

            assertEquals(ROUNDS, testUsers.get(60, TimeUnit.SECONDS).stream().filter("test"::equals).count());
            assertEquals(ROUNDS, bobUsers.get(60, TimeUnit.SECONDS).stream().filter("bob"::equals).count());
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<List<String>> users(String user, CyclicBarrier start) {
        return () -> {
            List<String> seen = new ArrayList<>();
            for (int round = 0; round < ROUNDS; round++) {
                start.await(30, TimeUnit.SECONDS);
                GraphQLResponse<User> response = client.currentUser(user);
                assertTrue(response.errors().isEmpty());
                assertNotNull(response.data());
                seen.add(response.data().username());
            }
            return seen;
        };
    }

}
