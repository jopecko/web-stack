package net.opecko.http.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.google.common.base.Joiner;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.MetricsRegistry;

/**
 * @author Sojern
 */
public class HttpStatusCodeMetricResourceFilter implements ResourceFilter, ContainerResponseFilter {

  private static final Joiner SPACE_JOINER = Joiner.on(" ");

  private final ConcurrentMap<Integer, Counter> counters;
  private final String basename;
  private final Class<?> klass;
  private final MetricsRegistry metricsRegistry;

  HttpStatusCodeMetricResourceFilter(
      final String basename,
      final Class<?> klass,
      final MetricsRegistry metricsRegistry
  ) {
    this.basename = basename;
    this.klass = klass;
    this.metricsRegistry = metricsRegistry;
    this.counters = new ConcurrentHashMap<>();
  }

  @Override
  public ContainerResponse filter(final ContainerRequest request, final ContainerResponse response) {
    getCounter(Integer.valueOf(response.getStatus())).inc();
    return response;
  }

  @Override
  public ContainerRequestFilter getRequestFilter() {
    // don't filter requests
    return null;
  }

  @Override
  public ContainerResponseFilter getResponseFilter() {
    return this;
  }

  private Counter getCounter(final int status) {
    Counter counter = counters.get(Integer.valueOf(status));
    if (null == counter) {
      Counter newCounter = metricsRegistry.newCounter(klass, SPACE_JOINER.join(basename, status, "counter"));
      counter = counters.putIfAbsent(Integer.valueOf(status), newCounter);
      if (null == counter) {
        counter = newCounter;
      }
    }
    return counter;
  }

}
