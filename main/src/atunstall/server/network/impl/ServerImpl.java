package atunstall.server.network.impl;

import atunstall.server.core.api.logging.Level;
import atunstall.server.network.api.Connection;
import atunstall.server.network.api.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class ServerImpl implements Server {
    private final NetworkImpl network;
    private final AsynchronousServerSocketChannel serverSocket;
    private final int port;
    private final Set<Consumer<Connection>> callbacks;
    private final AcceptCompletionHandler handler;
    private boolean open;

    ServerImpl(NetworkImpl network, AsynchronousServerSocketChannel serverSocket, int port) {
        this.network = network;
        this.serverSocket = serverSocket;
        this.port = port;
        callbacks = new HashSet<>();
        handler = new AcceptCompletionHandler();
    }

    @Override
    public void addConnectCallback(Consumer<Connection> consumer) {
        synchronized (callbacks) {
            callbacks.add(consumer);
        }
    }

    @Override
    public boolean isOpen() {
        return serverSocket.isOpen();
    }

    @Override
    public void setOpen(boolean open) {
        if (!isOpen() && open) {
            throw new UnsupportedOperationException("cannot reopen server socket");
        } else if (!open) {
            if (this.open) {
                network.getServerCount().decrementAndGet();
                this.open = false;
            }
            try {
                serverSocket.close();
            } catch (IOException e) {
                throw new UnsupportedOperationException("failed to close server socket", e);
            }
        } else {
            network.getLogger().log(Level.INFO, "Opening server on port " + getPort());
            if (!this.open) {
                network.getServerCount().incrementAndGet();
                this.open = true;
            }
            network.getExecutor().execute(this::queueAccept);
        }
    }

    @Override
    public int getPort() {
        return port;
    }

    private void queueAccept() {
        network.getLogger().log(Level.DEBUG, "Waiting for a connection");
        serverSocket.accept(null, handler);
    }

    private class AcceptCompletionHandler implements CompletionHandler<AsynchronousSocketChannel, Void> {
        @Override
        public void completed(AsynchronousSocketChannel result, Void attachment) {
            network.getLogger().log(Level.DEBUG, "Connection accepted");
            try {
                InetSocketAddress address = (InetSocketAddress) result.getRemoteAddress();
                Connection connection = network.createConnection(result, address.getAddress(), address.getPort());
                network.getLogger().log(Level.DEBUG, "Notifying listeners");
                callbacks.forEach(c -> c.accept(connection));
            } catch (IOException e) {
                network.getLogger().log(Level.ERROR, "Error accepting connection", e);
            }
            queueAccept();
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            network.getLogger().log(Level.ERROR, "Error accepting connection", exc);
            setOpen(false);
        }
    }
}
