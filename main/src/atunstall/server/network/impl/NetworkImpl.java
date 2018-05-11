package atunstall.server.network.impl;

import atunstall.server.core.api.Module;
import atunstall.server.core.api.Version;
import atunstall.server.core.api.logging.Level;
import atunstall.server.core.api.logging.Logger;
import atunstall.server.io.api.util.ArrayStreams;
import atunstall.server.network.api.Connection;
import atunstall.server.network.api.Network;
import atunstall.server.network.api.Server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Module
public class NetworkImpl implements Network {
    private static final ThreadGroup NETWORK_THREAD_GROUP = new ThreadGroup("Network");
    private final AsynchronousChannelGroup channelGroup;
    private final Logger logger;
    private final ExecutorService executor;
    private final ArrayStreams arrayStreams;
    private final AtomicLong threadCount;
    private final AtomicLong serverCount;

    public NetworkImpl(@Version(major = 1, minor = 0) Logger logger, @Version(major = 1, minor = 0) ArrayStreams arrayStreams) {
        this.logger = logger.getChild("Network");
        this.executor = Executors.newCachedThreadPool(this::newThread);
        this.arrayStreams = arrayStreams;
        threadCount = new AtomicLong(0L);
        serverCount = new AtomicLong(0L);
        try {
            channelGroup = AsynchronousChannelGroup.withThreadPool(executor);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create channel group", e);
        }
    }

    @Override
    public Server getServer(int port) {
        try {
            return new ServerImpl(this, AsynchronousServerSocketChannel.open(channelGroup).bind(new InetSocketAddress("127.0.0.1", port)), port);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to create server socket", e);
        }
    }

    @Override
    public void createConnection(InetAddress target, int port, Consumer<Connection> consumer, Consumer<Throwable> onFailure) {
        try {
            AsynchronousSocketChannel socket = AsynchronousSocketChannel.open(channelGroup);
            socket.connect(new InetSocketAddress(target.getHostAddress(), port), onFailure, new ConnectionHandler(socket, target, port, consumer));
        } catch (IOException e) {
            onFailure.accept(e);
        }
    }

    AtomicLong getServerCount() {
        return serverCount;
    }

    Logger getLogger() {
        return logger;
    }

    Executor getExecutor() {
        return executor;
    }

    ArrayStreams getArrayStreams() {
        return arrayStreams;
    }

    Connection createConnection(AsynchronousSocketChannel socket, InetAddress host, int port) {
        return new ConnectionImpl(this, socket, host, port);
    }

    private Thread newThread(Runnable runnable) {
        Thread thread;
        long count = threadCount.getAndIncrement();
        if (count > 0) {
            thread = new Thread(NETWORK_THREAD_GROUP, runnable, NETWORK_THREAD_GROUP.getName() + "-" + count);
        } else {
            thread = new Thread(NETWORK_THREAD_GROUP, () -> {
                do {
                    runnable.run();
                } while (serverCount.get() > 0);
            }, NETWORK_THREAD_GROUP.getName() + "-" + count);
        }
        logger.log(Level.DEBUG, "Creating thread " + thread.getName());
        return thread;
    }

    private class ConnectionHandler implements CompletionHandler<Void, Consumer<Throwable>> {
        private final AsynchronousSocketChannel socket;
        private final InetAddress host;
        private final int port;
        private final Consumer<Connection> consumer;

        private ConnectionHandler(AsynchronousSocketChannel socket, InetAddress host, int port, Consumer<Connection> consumer) {
            this.socket = socket;
            this.host = host;
            this.port = port;
            this.consumer = consumer;
        }

        @Override
        public void completed(Void result, Consumer<Throwable> attachment) {
            try {
                consumer.accept(createConnection(socket, host, port));
            } catch (Throwable t) {
                attachment.accept(t);
            }
        }

        @Override
        public void failed(Throwable exc, Consumer<Throwable> attachment) {
            attachment.accept(exc);
        }
    }
}
