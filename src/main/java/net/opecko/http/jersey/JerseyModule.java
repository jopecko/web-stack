package net.opecko.http.jersey;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.base.Strings;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.servlet.ServletModule;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

import net.opecko.http.config.jersey.JerseyPath;
import net.opecko.http.metrics.HttpStatusCodeMetricResourceFilterFactory;
import net.opecko.http.servlet.FilterDefinition;
import net.opecko.http.servlet.ServletEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Sojern
 */
@Parameters(separators = "=")
public class JerseyModule extends ServletModule {

  private static final Logger LOGGER = LoggerFactory.getLogger(JerseyModule.class);

  @Parameter(names = "--jersey_path", description = "Jersey path under context root")
  private final String jerseyPath = "/api/*";

  @Override
  protected void configureServlets() {
    if (!Strings.isNullOrEmpty(jerseyPath)) {
      //bind(GuiceContainer.class);
      bind(JacksonJsonProvider.class).in(Scopes.SINGLETON);
      bind(String.class).annotatedWith(JerseyPath.class).toInstance(jerseyPath);
      LOGGER.info("Jersey servlet installed at: {}", jerseyPath);
      Multibinder.newSetBinder(binder(), ServletEndpoint.class)
        .addBinding()
        .toProvider(JerseyGuiceServletEndpointProvider.class)
        .in(Scopes.SINGLETON);

      Multibinder.newSetBinder(binder(), FilterDefinition.class)
        .addBinding()
        .toProvider(JerseyGuiceFilterDefinitionProvider.class)
        .in(Scopes.SINGLETON);

      bind(HttpStatusCodeMetricResourceFilterFactory.class);

      // this should be injected with whatever's registered!!!
      Map<String, String> config = new HashMap<>();
      config.put(
        ResourceConfig.PROPERTY_RESOURCE_FILTER_FACTORIES,
        HttpStatusCodeMetricResourceFilterFactory.class.getCanonicalName()
      );
      serve(jerseyPath).with(GuiceContainer.class, config);
    } else {
      LOGGER.info("Metrics servlet not installed");
    }
  }

  private static final class JerseyGuiceFilterDefinitionProvider implements Provider<FilterDefinition> {

    private final GuiceContainer guiceContainer;
    private final String jerseyPath;

    @Inject
    private JerseyGuiceFilterDefinitionProvider(
        final GuiceContainer guiceContainer,
        @JerseyPath final String jerseyPath
    ) {
      this.guiceContainer = guiceContainer;
      this.jerseyPath = jerseyPath;
    }

    @Override
    public FilterDefinition get() {
      return new FilterDefinition(guiceContainer, jerseyPath);
    }

  }

  private static final class JerseyGuiceServletEndpointProvider implements Provider<ServletEndpoint> {

    private final GuiceContainer guiceContainer;
    private final String jerseyPath;

    @Inject
    private JerseyGuiceServletEndpointProvider(
        final GuiceContainer guiceContainer,
        @JerseyPath final String jerseyPath
    ) {
      this.guiceContainer = guiceContainer;
      this.jerseyPath = jerseyPath;
    }

    @Override
    public ServletEndpoint get() {
      return new ServletEndpoint(guiceContainer, jerseyPath);
    }

  }

}
