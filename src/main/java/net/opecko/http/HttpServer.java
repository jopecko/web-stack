package net.opecko.http;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Types;

import net.opecko.http.config.server.HttpStackServer;
import net.opecko.http.flags.FlagsModuleBuilder;
import net.opecko.http.jetty.JettyModule;
import net.opecko.http.metrics.MetricsModule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HttpServer's master configurator, bootstrapper and controller.
 */
public class HttpServer extends AbstractService {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpServer.class);
  private static final String JETTY_HOME = "jetty.home";
  private static final Function<Service, Future<Service.State>> START = new Function<Service, Future<Service.State>>() {
    @Override
    public Future<Service.State> apply(final Service service) {
      final SettableFuture<Service.State> future = SettableFuture.create();
      service.addListener(
        new Service.Listener() {
          @Override
          public void running() {
            future.set(Service.State.RUNNING);
          }
        },
        Executors.newSingleThreadExecutor()
      );
      service.startAsync();
      return future;
    }
  };
  private static final Function<Service, Future<Service.State>> STOP = new Function<Service, Future<Service.State>>() {
    @Override
    public Future<Service.State> apply(final Service service) {
      final SettableFuture<Service.State> future = SettableFuture.create();
      service.addListener(
        new Service.Listener() {
          @Override
          public void terminated(final Service.State from) {
            future.set(Service.State.TERMINATED);
          }
        },
        Executors.newSingleThreadExecutor()
      );
      service.stopAsync();
      return future;
    }
  };
  private static final Function<Service, String> NAME = new Function<Service, String>() {
    @Override public String apply(final Service service) {
      return service.getClass().getSimpleName();
    }
  };

  private final String[] args;
  @Nullable private Thread shutdownHook;

  public HttpServer(final String[] args) {
    this.args = Arrays.copyOf(args, args.length);
  }

  /**
   * Starts a {@link HttpServer}.
   */
  public static void main(final String[] args) {
    new HttpServer(args).main();
  }

  protected void main() {
    boolean success = false;

    try {
      startAsync().awaitRunning();
      success = isRunning();
    } catch (final Exception e) {
      LOGGER.error("Initialization failure", e);
    } finally {
      if (!success) {
        Runtime.getRuntime().exit(1);
      }
    }
  }

  /**
   * Forces the server to a complete stop.
   */
  @Override
  protected void doStop() {
    try {
      if (null != shutdownHook) {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        shutdownHook.start();
        shutdownHook.join();
      }
      notifyStopped();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      shutdownHook = null;
      if (this.state() != Service.State.TERMINATED) {
        notifyFailed(new IllegalArgumentException("Termination failure"));
      }
    }
  }

  /**
   * Starts the server.
   */
  @Override
  protected void doStart() {
    Preconditions.checkState(
      !Strings.isNullOrEmpty(System.getProperty(JETTY_HOME)),
      "Must set system property jetty.home to start"
    );

    LOGGER.info("Jetty home set to {}", System.getProperty(JETTY_HOME));
    Module rootModule = new FlagsModuleBuilder().addModules(getModules()).build(args);
    Injector injector = Guice.createInjector(Stage.PRODUCTION, rootModule);

    @SuppressWarnings("unchecked")
    TypeLiteral<Set<Service>> typeLiteral = (TypeLiteral<Set<Service>>)
        TypeLiteral.get(Types.newParameterizedType(Set.class, Service.class));
    final Set<Service> services = injector.getInstance(Key.get(typeLiteral));
    final Service httpStackServer = injector.getInstance(Key.get(Service.class, HttpStackServer.class));

    Runtime.getRuntime().addShutdownHook(shutdownHook = new Thread() {
      @Override
      public void run() {
        LOGGER.info("Shutdown initiated. Shutting down server...");
        httpStackServer.stopAsync().awaitTerminated();

        if (!applyAndWait(services, Service.State.TERMINATED, STOP)) {
          LOGGER.error("Failure stopping services");
        }
      }
    });

    LOGGER.info("Services installed: {}", Joiner.on(", ").join(Iterables.transform(services, NAME)));

    if (applyAndWait(services, Service.State.RUNNING, START)) {
      httpStackServer.startAsync().awaitRunning();
      notifyStarted();
    } else {
      notifyFailed(new IllegalStateException("Failure starting services"));
    }
  }

  private boolean applyAndWait(
      final Collection<Service> services,
      final Service.State expectedState,
      final Function<Service, Future<Service.State>> function
  ) {
    boolean success = true;

    try {
      Collection<Future<Service.State>> futures = Collections2.transform(services, function);
      for (Future<Service.State> future : futures) {
        try {
          if (future.get() != expectedState) {
            success = false;
          }
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          LOGGER.warn("Service control failure", e);
        }
      }
    } catch (final ExecutionException e) {
      LOGGER.warn("Service control failure", e);
    }
    return success;
  }

  /**
   * Configures the server with all primary modules to load.
   * You can override this to add or remove modules.
   */
  protected List<Module> getModules() {
    return Arrays.<Module>asList(
      new JettyModule(),
      new MetricsModule()
    );
  }

}
