package net.opecko.http.jetty;

import java.util.EnumSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.DispatcherType;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

import net.opecko.http.config.server.Acceptors;
import net.opecko.http.config.server.AvailableProcessors;
import net.opecko.http.config.server.BindAddress;
import net.opecko.http.config.server.ContextRoot;
import net.opecko.http.config.server.HttpStackServer;
import net.opecko.http.config.server.IdleTimeout;
import net.opecko.http.config.server.MaximumThreads;
import net.opecko.http.config.server.MinimumThreads;
import net.opecko.http.config.server.Selectors;
import net.opecko.http.config.server.ServerPort;
import net.opecko.http.servlet.FilterDefinition;
import net.opecko.http.servlet.ServletEndpoint;

import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP server module.
 */
@Parameters(separators = "=")
public class JettyModule extends AbstractModule {

  private static final Logger LOGGER = LoggerFactory.getLogger(JettyModule.class);

  @Parameter(names = "--bind_address", description = "Bind host address")
  private String bindAddress = "0.0.0.0";

  @Parameter(names = "--server_port", description = "HTTP port")
  private int serverPort = 8080;

  @Parameter(names = "--context_root", description = "Context root")
  private String contextRoot = "/";

  @Parameter(names = "--available_processors",
      description = "Number of processors on the VM (0 = determine at runtime)")
  private int availableProcessors;

  @Parameter(names = "--minimum_threads",
      description = "Minimum number of server threads per processor")
  private int minimumThreads = 16;

  @Parameter(names = "--maximum_threads",
      description = "Maximum number of server threads per processor")
  private int maximumThreads = 256;

  @Parameter(names = "--idle_timeout", description = "Connection idle timeout in milliseconds")
  private int idleTimeout = 60000;

  @Parameter(names = "--acceptors", description = "Connection acceptors per processor")
  private int acceptors = 2;

  @Parameter(names = "--selectors", description = "Connection selectors per processor")
  private int selectors = 2;

  @Override
  protected void configure() {
    LOGGER.info("Bind address: {}", bindAddress);
    LOGGER.info("Server port: {}", serverPort);
    LOGGER.info("Context root: {}", contextRoot);
    bind(String.class).annotatedWith(BindAddress.class).toInstance(bindAddress);
    bind(Integer.class).annotatedWith(ServerPort.class).toInstance(serverPort);
    bind(String.class).annotatedWith(ContextRoot.class).toInstance(contextRoot);
    bind(Service.class).annotatedWith(HttpStackServer.class).to(JettyServer.class);
    bind(int.class).annotatedWith(IdleTimeout.class).toInstance(idleTimeout);
    Multibinder.newSetBinder(binder(), Connector.class)
        .addBinding().toProvider(HttpConnectorProvider.class).in(Scopes.SINGLETON);
    Multibinder.newSetBinder(binder(), ServletEndpoint.class);
    Multibinder.newSetBinder(binder(), Service.class);
    Multibinder.newSetBinder(binder(), ConnectionFactory.class).addBinding().to(HttpConnectionFactory.class);
    Multibinder.newSetBinder(binder(), FilterDefinition.class);
  }

  @Singleton
  @Provides
  public Server provideServer(final ThreadPool threadPool, final Handler handler) {
    Server server = new Server(threadPool);
    server.setHandler(handler);
    return server;
  }

  @Singleton
  @Provides
  public Handler providerHandler(
      @ContextRoot final String contextPath,
      final Set<ServletEndpoint> endPoints,
      final Set<FilterDefinition> filters
  ) {
    ServletContextHandler handler = new ServletContextHandler();
    handler.setContextPath(contextPath);
    Joiner joiner = Joiner.on(", ");
    for (ServletEndpoint endPoint : endPoints) {
      LOGGER.info("Serving {} from {}", endPoint.getHttpServlet(), joiner.join(endPoint.getPaths()));
      ServletHolder servletHolder = new ServletHolder(endPoint.getHttpServlet());
      for (String path : endPoint.getPaths()) {
        handler.addServlet(servletHolder, path);
      }
    }
    for (FilterDefinition filterDef : filters) {
//      LOGGER.info("Installing filter {} on {}", filter.getFilter(), joiner.join(filter.getPaths()));
      FilterHolder filterHolder = new FilterHolder(filterDef.getFilter());
      handler.addFilter(filterHolder, filterDef.getPath(), EnumSet.allOf(DispatcherType.class));
    }
    return handler;
  }

  @Singleton
  @Provides
  @AvailableProcessors
  public int provideAvailableProcessors() {
    int processorCount;
    if (availableProcessors == 0) {
      processorCount = Runtime.getRuntime().availableProcessors();
      LOGGER.info("Determined available processors from system as {}", processorCount);
    } else {
      processorCount = availableProcessors;
      LOGGER.info("Overriding system available processors with {}", processorCount);
    }
    return processorCount;
  }

  @Provides
  @MinimumThreads
  public int provideMinimumThreads(@AvailableProcessors final int availableProcessors) {
    return minimumThreads * availableProcessors;
  }

  @Provides
  @MaximumThreads
  public int provideMaximumThreads(@AvailableProcessors final int availableProcessors) {
    return maximumThreads * availableProcessors;
  }

  @Provides
  @Acceptors
  public int provideAcceptors(@AvailableProcessors final int availableProcessors) {
    return acceptors * availableProcessors;
  }

  @Provides
  @Selectors
  public int provideSelectors(@AvailableProcessors final int availableProcessors) {
    return selectors * availableProcessors;
  }

  @Singleton
  @Provides
  public ThreadPool provideThreadPool(
      @MinimumThreads final int minimumThreads,
      @MaximumThreads final int maximumThreads,
      @IdleTimeout final int idleTimeout
  ) {
    LOGGER.info(
      "Thread pool minimum threads = {}, maximum threads = {}, idle timeout = {} ms",
      minimumThreads,
      maximumThreads,
      idleTimeout
    );
    return new QueuedThreadPool(maximumThreads, minimumThreads, idleTimeout);
  }

  static class HttpConnectorProvider implements Provider<Connector> {
    private final Server server;
    private final String host;
    private final int port;
    private final int acceptors;
    private final int selectors;
    private final Set<ConnectionFactory> connectionFactories;

    @Inject
    HttpConnectorProvider(
        final Server server,
        @BindAddress final String host,
        @ServerPort final int port,
        @Selectors final int selectors,
        @Acceptors final int acceptors,
        final Set<ConnectionFactory> connectionFactories
    ) {
      this.server = Preconditions.checkNotNull(server);
      this.host = Preconditions.checkNotNull(host);
      this.port = port;
      this.selectors = selectors;
      this.acceptors = acceptors;
      this.connectionFactories = ImmutableSet.copyOf(connectionFactories);
    }

    @Override
    public Connector get() {
      LOGGER.info("Creating HTTP connector with acceptors = {}, selectors = {}", acceptors, selectors);
      LOGGER.info("Binding HTTP connector to host = {}, port = {}", host, port);
      LOGGER.info("HTTP Connection factories = {}", connectionFactories);
      ServerConnector connector = new ServerConnector(
        server,
        /* executor */ null,
        /* scheduler */ null,
        /* byte buffer pool */ null,
        acceptors,
        selectors,
        connectionFactories.toArray(new HttpConnectionFactory[connectionFactories.size()])
      );
      connector.setName("http");
      if (!Strings.isNullOrEmpty(host)) {
        connector.setHost(host);
      }
      connector.setPort(port);
      return connector;
    }
  }

}
