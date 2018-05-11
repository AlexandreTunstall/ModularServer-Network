package atunstall.server.network.impl;

import atunstall.server.core.api.logging.Level;
import atunstall.server.io.api.ByteBuffer;
import atunstall.server.io.api.ParsableByteBuffer;
import atunstall.server.io.api.util.AppendableParsableByteBuffer;
import atunstall.server.io.api.util.HandledInputStream;
import atunstall.server.network.api.Connection;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class ConnectionImpl implements Connection {
    private final NetworkImpl network;
    private final AsynchronousSocketChannel socket;
    private final Object outputLock;
    private final InetAddress targetAddress;
    private final int targetPort;
    private HandledInputStream inputWrapper;
    private AppendableParsableByteBuffer buffer;
    private byte[] outputBuffer;
    private java.nio.ByteBuffer outputBufferWrapper;
    private byte[] readBuffer;
    private java.nio.ByteBuffer readBufferWrapper;

    ConnectionImpl(NetworkImpl network, AsynchronousSocketChannel socket, InetAddress targetAddress, int targetPort) {
        this.network = network;
        this.socket = socket;
        outputLock = new Object();
        this.targetAddress = targetAddress;
        this.targetPort = targetPort;
        inputWrapper = network.getArrayStreams().createInputStream(this);
        buffer = network.getArrayStreams().createByteBuffer(4096);
        outputBufferWrapper = java.nio.ByteBuffer.wrap(outputBuffer = new byte[4096]);
        readBufferWrapper = java.nio.ByteBuffer.wrap(readBuffer = new byte[4096]);
        socket.read(readBufferWrapper, null, new CompletionHandlerImpl());
    }

    @Override
    public InetAddress getTarget() {
        return targetAddress;
    }

    @Override
    public int getTargetPort() {
        return targetPort;
    }

    @Override
    public void queueConsumer(Consumer<? super ParsableByteBuffer> consumer) {
        inputWrapper.queueConsumer(consumer);
        if (buffer.count() > 0L && isClosed()) {
            network.getExecutor().execute(this::consumeUntilEmpty);
        }
    }

    @Override
    public boolean isClosed() {
        return !socket.isOpen();
    }

    @Override
    public void close() throws Exception {
        if (!inputWrapper.isClosed()) {
            inputWrapper.close();
            return;
        }
        network.getLogger().log(Level.DEBUG, "Connection closed");
    }

    @Override
    public void accept(ByteBuffer byteBuffer) {
        if (isClosed()) {
            return;
        }
        synchronized (outputLock) {
            long index = 0L;
            try {
                while (index + outputBuffer.length <= byteBuffer.count()) {
                    byteBuffer.get(index, outputBuffer, 0, outputBuffer.length);
                    outputBufferWrapper.position(0);
                    index += socket.write(outputBufferWrapper).get();
                }
                while (index < byteBuffer.count()) {
                    int count = (int) (byteBuffer.count() - index);
                    byteBuffer.get(index, outputBuffer, 0, count);
                    outputBufferWrapper.position(0);
                    outputBufferWrapper.limit(count);
                    index += socket.write(outputBufferWrapper).get();
                }
            } catch (InterruptedException ignored) {} catch (ExecutionException e) {
                if (e.getCause() instanceof IOException) {
                    safeClose();
                    throw new UnsupportedOperationException("error while writing to stream", e.getCause());
                }
            }
            if (isClosed()) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private void safeClose() {
        try {
            close();
        } catch (Exception e) {
            network.getLogger().log(Level.ERROR, "Failed to close socket", e);
        }
    }

    private void consumeUntilEmpty() {
        inputWrapper.consumeSafe(buffer);
        if (buffer.count() > 0L && inputWrapper.consumerCount() > 0) {
            network.getExecutor().execute(this::consumeUntilEmpty);
        }
    }

    private class CompletionHandlerImpl implements CompletionHandler<Integer, Void> {
        @Override
        public void completed(Integer result, Void attachment) {
            if (result == -1) {
                safeClose();
                consumeUntilEmpty();
                return;
            }
            buffer.append(readBuffer, 0, result);
            readBufferWrapper.position(0);
            if (isClosed()) {
                consumeUntilEmpty();
                return;
            }
            inputWrapper.consumeSafe(buffer);
            //network.getLogger().log(Level.DEBUG, "Queueing next socket read");
            socket.read(readBufferWrapper, null, this);
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            network.getLogger().log(Level.ERROR, "Failed to read file", exc);
            safeClose();
            consumeUntilEmpty();
        }
    }
}
