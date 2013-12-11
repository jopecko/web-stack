package net.opecko.http.metrics;

import com.google.common.util.concurrent.Service;
import com.yammer.metrics.core.HealthCheck;

/**
 * A {@link HealthCheck} that ensures the {@link Service} is not in a failure mode.
 */
public class ServiceHealthCheck extends HealthCheck {

  private final Service service;

  public ServiceHealthCheck(final Service service) {
    super(service.getClass().getName());
    this.service = service;
  }

  @Override
  protected Result check() throws Exception {
    switch (service.state()) {
      case FAILED:
      case TERMINATED:
      case STOPPING:
      case NEW:
      case STARTING:
        return Result.unhealthy(service.state().name());

      default:
        return Result.healthy();
    }
  }

}
