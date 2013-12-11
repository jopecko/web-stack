package net.opecko.http.metrics;

import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.Lists;
import com.sun.jersey.api.model.AbstractMethod;
import com.sun.jersey.api.model.AbstractResourceMethod;
import com.sun.jersey.api.model.AbstractSubResourceLocator;
import com.sun.jersey.api.model.AbstractSubResourceMethod;
import com.sun.jersey.api.model.PathValue;
import com.sun.jersey.spi.container.ResourceFilter;
import com.sun.jersey.spi.container.ResourceFilterFactory;
import com.yammer.metrics.core.MetricsRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Joe O'Pecko
 */
@Singleton
public class HttpStatusCodeMetricResourceFilterFactory implements ResourceFilterFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpStatusCodeMetricResourceFilterFactory.class);

  private final MetricsRegistry metricsRegistry;

  @Inject
  HttpStatusCodeMetricResourceFilterFactory(final MetricsRegistry metricsRegistry) {
    this.metricsRegistry = metricsRegistry;
  }

  @Override
  public List<ResourceFilter> create(final AbstractMethod am) {

    // documented to only be AbstractSubResourceLocator, AbstractResourceMethod, or AbstractSubResourceMethod
    if (am instanceof AbstractSubResourceLocator) {
      // not actually invoked per request, nothing to do
      LOGGER.debug("Ignoring AbstractSubResourceLocator " + am);
      return null;
    } else if (am instanceof AbstractResourceMethod) {
      String basename = getMetricBaseName((AbstractResourceMethod) am);
      Class<?> klass = am.getResource().getResourceClass();

      return Lists.<ResourceFilter>newArrayList(
        new HttpStatusCodeMetricResourceFilter(basename, klass, metricsRegistry)
      );
    } else {
      LOGGER.warn("Got an unexpected instance of " + am.getClass().getName() + ": " + am);
      return null;
    }
  }

  static String getMetricBaseName(final AbstractResourceMethod am) {

    String metricId = getPathWithoutSurroundingSlashes(am.getResource().getPath());

    if (!metricId.isEmpty()) {
      metricId = "/" + metricId;
    }

    String httpMethod;
    if (am instanceof AbstractSubResourceMethod) {
      // if this is a subresource, add on the subresource's path component
      AbstractSubResourceMethod asrm = (AbstractSubResourceMethod) am;
      metricId += "/" + getPathWithoutSurroundingSlashes(asrm.getPath());
      httpMethod = asrm.getHttpMethod();
    } else {
      httpMethod = am.getHttpMethod();
    }

    if (metricId.isEmpty()) {
      // this happens for WadlResource -- that case actually exists at "application.wadl" though
      metricId = "(no path)";
    }
    metricId += " " + httpMethod;
    return metricId;
  }

  private static String getPathWithoutSurroundingSlashes(@Nullable final PathValue pathValue) {
    if (pathValue == null) {
      return "";
    }
    String value = pathValue.getValue();
    if (value.startsWith("/")) {
      value = value.substring(1);
    }
    if (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

}
