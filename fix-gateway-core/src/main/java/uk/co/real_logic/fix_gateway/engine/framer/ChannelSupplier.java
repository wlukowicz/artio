/*
 * Copyright 2015-2016 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway.engine.framer;

import org.agrona.CloseHelper;
import uk.co.real_logic.fix_gateway.engine.EngineConfiguration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * Mockable class for intercepting network communications
 */
public class ChannelSupplier implements AutoCloseable
{

    private final boolean hasBindAddress;
    private final Selector selector;
    private final ServerSocketChannel listeningChannel;

    public ChannelSupplier(final EngineConfiguration configuration)
    {
        hasBindAddress = configuration.hasBindAddress();

        if (hasBindAddress)
        {
            try
            {
                listeningChannel = ServerSocketChannel.open();
                listeningChannel.bind(configuration.bindAddress()).configureBlocking(false);

                selector = Selector.open();
                listeningChannel.register(selector, SelectionKey.OP_ACCEPT);
            }
            catch (final IOException ex)
            {
                throw new IllegalArgumentException(ex);
            }
        }
        else
        {
            listeningChannel = null;
            selector = null;
        }
    }

    public int forEachChannel(final NewChannelHandler handler) throws IOException
    {
        if (!hasBindAddress)
        {
            return 0;
        }

        final int newConnections = selector.selectNow();
        if (newConnections > 0)
        {
            final Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext())
            {
                it.next();

                final SocketChannel channel = listeningChannel.accept();

                handler.onNewChannel(channel);

                it.remove();
            }
        }

        return newConnections;
    }

    public void close() throws Exception
    {
        CloseHelper.close(listeningChannel);
        CloseHelper.close(selector);
    }

    public SocketChannel open(final InetSocketAddress address) throws IOException
    {
        final SocketChannel channel = SocketChannel.open();
        channel.connect(address);
        return channel;
    }

    @FunctionalInterface
    public interface NewChannelHandler
    {
        void onNewChannel(final SocketChannel socketChannel) throws IOException;
    }

}
