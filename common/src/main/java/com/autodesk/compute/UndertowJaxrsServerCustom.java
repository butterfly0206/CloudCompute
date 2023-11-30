package com.autodesk.compute;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.handlers.DisallowedMethodsHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.util.HttpString;
import lombok.SneakyThrows;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.jboss.resteasy.util.PortProvider;

public class UndertowJaxrsServerCustom extends UndertowJaxrsServer {

    private final PathHandler localRoot = new PathHandler();
    private final ServletContainer localContainer = ServletContainer.Factory.newInstance();

    private final DisallowedMethodsHandler disallowedMethodsHandler =
            new DisallowedMethodsHandler(localRoot, new HttpString("TRACE"), new HttpString("TRACK"));

    @Override
    public void addResourcePrefixPath(final String path, final ResourceHandler handler) {
        localRoot.addPrefixPath(path, handler);
    }

    @Override
    @SneakyThrows(jakarta.servlet.ServletException.class)
    public UndertowJaxrsServer deploy(final DeploymentInfo builder) {
        final DeploymentManager manager = localContainer.addDeployment(builder);
        manager.deploy();
        localRoot.addPrefixPath(builder.getContextPath(), manager.start());
        return this;
    }

    @Override
    public UndertowJaxrsServer start(final Undertow.Builder builder) {
        final Undertow.Builder finalBuilder = addCommonOptions(builder);
        server = finalBuilder.build();
        server.start();
        return this;
    }

    public Undertow.Builder addCommonOptions(final Undertow.Builder builder) {
        return builder.setHandler(disallowedMethodsHandler)
                .setServerOption(UndertowOptions.NO_REQUEST_TIMEOUT, 120 * 1000)
                .setServerOption(UndertowOptions.REQUEST_PARSE_TIMEOUT, 5 * 1000)
                .setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, true);

    }

    @Override
    public UndertowJaxrsServer start() {
        // Set connection default to two minutes
        server = addCommonOptions(Undertow.builder())
                .addHttpListener(PortProvider.getPort(), "localhost")
                .build();
        server.start();
        return this;
    }
}
