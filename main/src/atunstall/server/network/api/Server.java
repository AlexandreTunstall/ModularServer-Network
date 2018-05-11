package atunstall.server.network.api;

import java.util.function.Consumer;

/**
 * Models a server port capable of receiving connection requests.
 */
public interface Server {
    /**
     * Adds the given consumer to the set of callbacks that are executed every time a connection with this server is opened.
     * Each consumer may only be added once.
     * @param consumer The consumer to add to the set of callbacks.
     */
    void addConnectCallback(Consumer<Connection> consumer);

    /**
     * Checks if this server is open for clients to connect to it.
     * If it is not, no new connections with this server can be made.
     * @return True if the server is open, false otherwise.
     */
    boolean isOpen();

    /**
     * Sets whether the server is open or not.
     * @param open True if the server should be open to new connections, false otherwise.
     */
    void setOpen(boolean open);

    /**
     * Returns the number of the port this server is running on.
     * @return The port number.
     */
    int getPort();
}
