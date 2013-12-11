package net.opecko.http.jetty;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AbstractIdleService;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import javax.inject.Inject;

/**
 * Wrap an embedded Jetty service in a Guava {@link com.google.common.util.concurrent.Service}.
 */
class JettyServer extends AbstractIdleService {

  private static final Logger LOGGER = LoggerFactory.getLogger(JettyServer.class);

  private final Server server;

  @Inject
  JettyServer(
      final Server server,
      final Set<Connector> connectorSet
  ) {
    Preconditions.checkNotNull(connectorSet);
    Preconditions.checkArgument(!connectorSet.isEmpty());
    this.server = Preconditions.checkNotNull(server);
    for (Connector connector : connectorSet) {
      LOGGER.info("Adding connector {}: {}", connector.getName(), connector);
    }
    server.setConnectors(connectorSet.toArray(new Connector[connectorSet.size()]));
  }

  @Override
  protected void startUp() throws Exception {
    LOGGER.info("Starting JettyServer");
    server.start();
  }

  @Override
  protected void shutDown() throws Exception {
    LOGGER.info("Stopping JettyServer");
    server.stop();
    LOGGER.info("JettyServer successfully stopped");
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).omitNullValues()
      .add("server", server)
      .toString();
  }

}
