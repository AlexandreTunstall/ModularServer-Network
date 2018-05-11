package atunstall.server.network.api;

import atunstall.server.core.api.Unique;
import atunstall.server.core.api.Version;

import java.net.InetAddress;
import java.util.function.Consumer;

/**
 * Interface used to interact on the network.
 */
@Unique
@Version(major = 1, minor = 0)
public interface Network {
    /**
     * Returns a server bound to the given port number.
     * @param port The port number the server should be bound to. This number must be between 0 and 65535.
     * @return The existing server bound to the port, or a new server if no server was bound to it.
     * @throws IllegalArgumentException If no server instance could be created for the given port number (out of range or busy port)
     */
    Server getServer(int port);

    /**
     * Attempts to create a connection with the target.
     * @param target The target address with which to open a connection.
     * @param port The target port with which to open a connection.
     * @param consumer The consumer for the newly created connection.
     * @param onFailure The consumer for any failures that prevent the creation of the connection.
     */
    void createConnection(InetAddress target, int port, Consumer<Connection> consumer, Consumer<Throwable> onFailure);
}
