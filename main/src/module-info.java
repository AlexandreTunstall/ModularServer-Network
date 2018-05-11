module atunstall.server.network {
    requires atunstall.server.core;
    requires atunstall.server.io;
    exports atunstall.server.network.api;
    exports atunstall.server.network.impl to atunstall.server.core;
}