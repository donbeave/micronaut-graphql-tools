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
package io.micronaut.graphql.tools.internal.execution;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import jakarta.inject.Singleton;

/**
 * Watches the close future of the Netty channel of a request. A connection that closes while the request is being answered
 * (an HTTP/1.1 client that disconnects) cancels the execution. An HTTP/2 stream that is reset leaves the connection open and
 * is not noticed.
 */
@Internal
@Singleton
@Requires(classes = NettyHttpRequest.class)
final class NettyDisconnectWatcher implements DisconnectWatcher {

    private static final Runnable NOTHING = () -> { };

    @Override
    public Runnable watch(HttpRequest<?> request, Runnable onClose) {
        if (!(request instanceof NettyHttpRequest<?> netty)) {
            return NOTHING;
        }
        Channel channel = netty.getChannelHandlerContext().channel();
        ChannelFutureListener listener = future -> onClose.run();
        channel.closeFuture().addListener(listener);
        return () -> channel.closeFuture().removeListener(listener);
    }
}
