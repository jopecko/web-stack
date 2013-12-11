package net.opecko.http.metrics;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.yammer.metrics.HealthChecks;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.HealthCheck;
import com.yammer.metrics.core.HealthCheckRegistry;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.reporting.AdminServlet;

import net.opecko.http.config.metrics.MetricsPath;
import net.opecko.http.servlet.ServletEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guice module for installing instrumentation.
 */
@Parameters(separators = "=")
public class MetricsModule extends AbstractModule {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetricsModule.class);

  @Parameter(names = "--metrics_path", description = "Metrics Web page path under context root")
  private String metricsPath = "/metrics";

  @Override
  protected void configure() {
    bind(MetricsRegistry.class).toInstance(Metrics.defaultRegistry());

    if (!Strings.isNullOrEmpty(metricsPath)) {
      bind(String.class).annotatedWith(MetricsPath.class).toInstance(metricsPath);
      LOGGER.info("Metrics servlet installed at: {}", metricsPath);
      Multibinder.newSetBinder(binder(), ServletEndpoint.class)
        .addBinding()
        .toProvider(MetricsEndpointProvider.class)
        .in(Scopes.SINGLETON);
    } else {
      LOGGER.info("Metrics servlet not installed");
    }
    Multibinder.newSetBinder(binder(), HealthCheck.class);
  }

  @Provides
  @Singleton
  public HealthCheckRegistry provideHealthCheckRegistry(final Set<HealthCheck> healthChecks) {
    HealthCheckRegistry healthCheckRegistry = HealthChecks.defaultRegistry();
    for (HealthCheck healthCheck : healthChecks) {
      healthCheckRegistry.register(healthCheck);
    }
    return healthCheckRegistry;
  }

  private static final class MetricsEndpointProvider implements Provider<ServletEndpoint> {

    private final AdminServlet adminServlet;
    private final String adminPath;

    @Inject
    private MetricsEndpointProvider(
        final AdminServlet adminServlet,
        @MetricsPath final String adminPath
    ) {

      Preconditions.checkArgument(!Strings.isNullOrEmpty(adminPath));
      this.adminServlet = Preconditions.checkNotNull(adminServlet);
      this.adminPath = adminPath;
    }

    @Override
    public ServletEndpoint get() {
      return new ServletEndpoint(adminServlet, adminPath, adminPath + "/*");
    }
  }

}
