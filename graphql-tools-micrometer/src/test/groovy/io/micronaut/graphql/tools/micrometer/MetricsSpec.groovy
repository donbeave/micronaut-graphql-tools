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
package io.micronaut.graphql.tools.micrometer

import io.micrometer.core.instrument.Meter

class MetricsSpec extends AbstractMicrometerSpec {

    void "a timer exists for the request, tagged by operation name and outcome"() {
        given:
        start('MicrometerMetrics')

        when:
        def result = run('query Hello { hello }', 'Hello')

        then:
        result.errors.isEmpty()
        def timer = meters.find(GraphQLMetrics.REQUEST_TIMER).tags('operation', 'Hello', 'outcome', 'success').timer()
        timer != null
        timer.count() == 1
    }

    void "the operation name comes from the document when the request names none"() {
        given:
        start('MicrometerMetrics')

        when:
        run('query FromDocument { hello }')
        run('{ hello }')

        then:
        meters.find(GraphQLMetrics.REQUEST_TIMER).tags('operation', 'FromDocument').timer().count() == 1
        meters.find(GraphQLMetrics.REQUEST_TIMER).tags('operation', 'anonymous').timer().count() == 1
    }

    void "a result with an error is recorded with the error outcome"() {
        given:
        start('MicrometerMetrics')

        when:
        def result = run('query Broken { broken }')

        then:
        !result.errors.isEmpty()
        meters.find(GraphQLMetrics.REQUEST_TIMER).tags('operation', 'Broken', 'outcome', 'error').timer().count() == 1
        meters.find(GraphQLMetrics.REQUEST_TIMER).tags('outcome', 'success').timer() == null
    }

    void "a request that fails validation is recorded with the error outcome"() {
        given:
        start('MicrometerMetrics')

        when:
        def result = run('query Invalid { nothing }')

        then:
        !result.errors.isEmpty()
        meters.find(GraphQLMetrics.REQUEST_TIMER).tags('operation', 'anonymous', 'outcome', 'error').timer().count() == 1
    }

    void "the cardinality cap holds: names beyond it share one value"() {
        given:
        start('MicrometerMetrics', ['micronaut.metrics.binders.graphql.max-operations': 3])

        when:
        (1..20).each { run("query Op$it { hello }") }
        run('query Op1 { hello }')

        then: 'three names, and the rest in other'
        def operations = meters.find(GraphQLMetrics.REQUEST_TIMER).timers()*.id*.getTag('operation') as Set
        operations == ['Op1', 'Op2', 'Op3', 'other'] as Set
        meters.find(GraphQLMetrics.REQUEST_TIMER).tags('operation', 'Op1').timers()*.count().sum() == 2
        meters.find(GraphQLMetrics.REQUEST_TIMER).tags('operation', 'other').timers()*.count().sum() == 17
    }

    void "the cap holds under concurrent requests"() {
        given:
        start('MicrometerMetrics', ['micronaut.metrics.binders.graphql.max-operations': 5])

        when:
        def threads = (1..8).collect { t ->
            Thread.start { (1..50).each { run("query T${t}Op$it { hello }") } }
        }
        threads*.join()

        then:
        (meters.find(GraphQLMetrics.REQUEST_TIMER).timers()*.id*.getTag('operation') as Set).size() == 6
    }

    void "a field creates no timer of its own"() {
        given:
        start('MicrometerMetrics')

        when:
        run('query Q { hello book { title } }')

        then:
        meters.meters.findAll { it.id.name.startsWith('graphql.') }*.id*.name as Set == ['graphql.request'] as Set
    }

    void "DataLoader batches are counted when the statistics are on"() {
        given:
        start('MicrometerMetrics', ['micronaut.graphql.tools.execution.data-loader-statistics': true])

        when:
        def result = run('query Loaded { loaded }')

        then:
        result.data == [loaded: 'title-a']
        meters.find(GraphQLMetrics.DATA_LOADER_BATCHES).tag('loader', 'titles').counter().count() == 1d
        meters.find(GraphQLMetrics.DATA_LOADER_BATCH_KEYS).tag('loader', 'titles').counter().count() == 1d
    }

    void "no DataLoader meter exists when the statistics are off"() {
        given:
        start('MicrometerMetrics')

        when:
        run('query Loaded { loaded }')

        then:
        meters.meters.findAll { it.id.name.startsWith('graphql.dataloader') }.isEmpty()
    }

    void "the binder key turns the metrics off"() {
        given:
        start('MicrometerMetrics', ['micronaut.metrics.binders.graphql.enabled': false])

        when:
        run('query Hello { hello }')

        then:
        !hasBean('MicrometerObserver')
        meters.meters.collect { it.id.name }.every { !it.startsWith('graphql.') }
    }

    void "the Micronaut metrics switch turns the metrics off"() {
        given:
        start('MicrometerMetrics', [:], false)

        when:
        run('query Hello { hello }')

        then:
        !hasBean('MicrometerObserver')
        meters.meters.collect { it.id.name }.every { !it.startsWith('graphql.') }
    }

    void "the metrics are on by default, with the cap at 100 operation names"() {
        given:
        start('MicrometerMetrics')

        expect:
        hasBean('MicrometerObserver')
        context.getBean(Class.forName('io.micronaut.graphql.tools.micrometer.internal.GraphQLMetricsConfiguration')).with {
            it.enabled && it.maxOperations == 100
        }
    }

    void "a @Timed resolver has a timer"() {
        given:
        start('MicrometerMetrics')

        when:
        run('query T { timed }')

        then:
        meters.find('resolver.timed').timer().count() == 1
    }

}
