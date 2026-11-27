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
package io.micronaut.graphql.tools.client.call

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.graphql.tools.client.GraphQLRequest
import io.micronaut.graphql.tools.client.GraphQLSession
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.function.Consumer

class GraphQLSessionSpec extends Specification {

    @Shared int port = SocketUtils.findAvailableTcpPort()
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(EmbeddedServer, [
            'micronaut.server.port'            : port,
            'micronaut.http.services.stub.url' : "http://localhost:$port",
    ]).applicationContext

    @Shared Clients clients = context.getBean(Clients)
    @Shared StubController server = context.getBean(StubController)

    private static Consumer setCookies(String... cookies) {
        { req -> cookies.each { req.header('X-Set-Cookie', it) } } as Consumer
    }

    private static GraphQLRequest<Map<String, Object>> query() {
        GraphQLRequest.builder().document('{ a }').build()
    }

    private String cookieSent(GraphQLSession session) {
        session.executeBlocking(query())
        server.last().headers().get('cookie')?.join('|')
    }

    void "a cookie that one response sets is sent on the next call"() {
        given:
        def session = clients.stub.newSession()

        when:
        session.executeBlocking(query(), setCookies('sid=abc; Path=/'))

        then:
        cookieSent(session) == 'sid=abc'
        session.cookies()*.name() == ['sid']
    }

    void "the async, reactive and blocking variants all store and replay cookies"() {
        given:
        def session = clients.stub.newSession()

        when:
        session.executeAsync(query(), setCookies('a=1')).join()
        Mono.from(session.execute(query(), setCookies('b=2'))).block()

        then:
        cookieSent(session) == 'a=1; b=2'
    }

    void "a cookie is stored before the future completes, so a continuation sees it"() {
        given:
        def session = clients.stub.newSession()

        expect:
        session.executeAsync(query(), setCookies('sid=abc')).thenApply { session.cookies().size() }.join() == 1
    }

    void "the shared client never keeps cookies, and two sessions do not see each other's"() {
        given:
        def first = clients.stub.newSession()
        def second = clients.stub.newSession()

        when:
        clients.stub.executeBlocking(query(), setCookies('shared=1'))
        first.executeBlocking(query(), setCookies('mine=1'))

        then:
        cookieSent(first) == 'mine=1'
        cookieSent(second) == null
        clients.stub.executeBlocking(query())
        server.last().headers().get('cookie') == null
    }

    void "a session started from a session is independent"() {
        given:
        def first = clients.stub.newSession()
        first.executeBlocking(query(), setCookies('sid=abc'))

        expect:
        cookieSent(first.newSession()) == null
    }

    void "the path of a cookie is honoured against the endpoint path"() {
        given:
        def session = clients.stub.newSession()

        when:
        session.executeBlocking(query(), setCookies('root=1; Path=/', 'exact=2; Path=/graphql', 'other=3; Path=/other', 'prefix=4; Path=/graph'))

        then:
        cookieSent(session) == 'exact=2; root=1'
    }

    void "a cookie without a path takes the directory of the endpoint"() {
        given:
        def session = clients.stub.newSession()

        when:
        session.executeBlocking(query(), setCookies('sid=abc'))

        then:
        session.cookies()*.path() == ['/']
    }

    void "a cookie that a response expires is removed"() {
        given:
        def session = clients.stub.newSession()
        session.executeBlocking(query(), setCookies('a=1', 'b=2', 'c=3'))

        when:
        session.executeBlocking(query(), setCookies('a=; Max-Age=0', 'b=x; Expires=Thu, 01 Jan 1970 00:00:00 GMT'))

        then:
        cookieSent(session) == 'c=3'
    }

    void "a cookie of the same name and path is replaced"() {
        given:
        def session = clients.stub.newSession()
        session.executeBlocking(query(), setCookies('sid=old'))
        session.executeBlocking(query(), setCookies('sid=new'))

        expect:
        cookieSent(session) == 'sid=new'
    }

    void "a cookie expires by its Max-Age"() {
        given:
        def session = clients.stub.newSession()
        session.executeBlocking(query(), setCookies('sid=abc; Max-Age=1'))

        expect:
        cookieSent(session) == 'sid=abc'
        sleep(1200)
        cookieSent(session) == null
    }

    void "the fixed headers are sent on every call, and a customizer can override them"() {
        given:
        def session = clients.stub.newSession([ 'X-Tenant': 'one', 'X-Other': 'a' ])

        when:
        session.executeBlocking(query())

        then:
        server.last().headers().get('x-tenant') == ['one']

        when:
        session.executeBlocking(query(), { it.headers.set('X-Tenant', 'two') } as Consumer)

        then:
        server.last().headers().get('x-tenant') == ['two']
        server.last().headers().get('x-other') == ['a']
    }

    void "the cookies of a failed call are stored too, and clearCookies forgets them"() {
        given:
        def session = clients.stub.newSession()

        when:
        session.executeBlocking(query(), { it.header('X-Status', '500').header('X-Set-Cookie', 'sid=abc') } as Consumer)

        then:
        session.cookies()*.name() == ['sid']

        when:
        session.clearCookies()

        then:
        session.cookies().empty
    }
}
