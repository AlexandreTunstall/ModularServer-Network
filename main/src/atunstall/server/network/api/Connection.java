package atunstall.server.network.api;

import atunstall.server.io.api.InputStream;
import atunstall.server.io.api.OutputStream;

import java.net.InetAddress;

/**
 * Models a connection formed between a server and a client.
 */
public interface Connection extends InputStream, OutputStream {
    /**
     * Returns the address of the target system this connection is connected to.
     * @return The target address.
     */
    InetAddress getTarget();

    /**
     * Returns the port number on the target system this connection is connected to.
     * @return The target port number.
     */
    int getTargetPort();
}
