package net.opecko.http.flags;

import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.internal.Lists;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.inject.Binder;
import com.google.inject.Module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Special module that uses a two-stage binding method so parameters can be parsed from the
 * command line.
 * <p>
 * The modules are all passed to JCommander, which attempts to bind the command line to all
 * the associated {@code @Parameter} fields.
 * <p>
 * Second, those modules are installed into the same Binder. This way the
 * {@link Module#configure(Binder)} method of each module isn't called until the command
 * line parsing has occurred.
 */
public class FlagsModuleBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlagsModuleBuilder.class);

  private final List<Module> modules = Lists.newArrayList();

  public final FlagsModuleBuilder addModule(final Module module) {
    modules.add(Preconditions.checkNotNull(module));
    return this;
  }

  public final FlagsModuleBuilder addModules(final Iterable<Module> modules) {
    Iterables.addAll(this.modules, modules);
    return this;
  }

  public final Module build(final String... args) {
    LOGGER.info("Command line: {}", Joiner.on(" ").join(args));
    return new Module() {

      @Override
      public void configure(final Binder binder) {
        JCommander jcommander = new JCommander(modules);
        HelpFlag helpFlag = new HelpFlag();
        jcommander.addObject(helpFlag);
        boolean showHelp = false;
        try {
          jcommander.parse(args);
        } catch (final ParameterException e) {
          LOGGER.error("Error parsing command line", e);
          showHelp = true;
        }
        if (showHelp || helpFlag.help) {
          jcommander.usage();
          System.exit(1);
        }
        for (Module module : modules) {
          binder.install(module);
        }
      }
    };

  }

  private static class HelpFlag {

    @Parameter(description = "Help", names = "--help", help = true)
    private boolean help;

  }

}
