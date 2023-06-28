/*
 * Copyright © 2023 Mark Raynsford <code@io7m.com> https://www.io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.io7m.ervilla.native_exec.internal;

import com.io7m.ervilla.api.EContainerConfiguration;
import com.io7m.ervilla.api.EContainerSpec;
import com.io7m.ervilla.api.EContainerSupervisorType;
import com.io7m.ervilla.api.EContainerType;
import com.io7m.ervilla.api.EPortPublish;
import com.io7m.jdeferthrow.core.ExceptionTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A container supervisor.
 */

public final class EContainerSupervisor implements EContainerSupervisorType
{
  private static final Logger LOG =
    LoggerFactory.getLogger(EContainerSupervisor.class);

  private static final Consumer<String> IGNORING =
    text -> {
    };

  private final EContainerConfiguration configuration;
  private final HashMap<String, EContainer> containers;
  private final ExecutorService ioSupervisor;
  private final SecureRandom rng;

  /**
   * A container supervisor.
   *
   * @param inConfiguration The container configuration
   */

  public EContainerSupervisor(
    final EContainerConfiguration inConfiguration)
  {
    this.configuration =
      Objects.requireNonNull(inConfiguration, "configuration");
    this.containers =
      new HashMap<String, EContainer>();
    this.ioSupervisor =
      Executors.newCachedThreadPool(r -> {
        final var thread = new Thread(r);
        thread.setName("com.io7m.ervilla[%d]".formatted(Long.valueOf(thread.getId())));
        thread.setDaemon(true);
        return thread;
      });

    try {
      this.rng = SecureRandom.getInstanceStrong();
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String portSpec(
    final EPortPublish publish)
  {
    if (publish.hostIP().isPresent()) {
      final var hostIP = publish.hostIP().get();
      return "%s:%s:%s/%s".formatted(
        hostIP,
        Integer.valueOf(publish.hostPort()),
        Integer.valueOf(publish.containerPort()),
        switch (publish.protocol()) {
          case TCP -> "tcp";
          case UDP -> "udp";
        }
      );
    }
    return "%s:%s/%s".formatted(
      Integer.valueOf(publish.hostPort()),
      Integer.valueOf(publish.containerPort()),
      switch (publish.protocol()) {
        case TCP -> "tcp";
        case UDP -> "udp";
      }
    );
  }

  private String freshToken()
  {
    final var data = new byte[9];
    this.rng.nextBytes(data);
    return Base64.getMimeEncoder()
      .encodeToString(data)
      .replace('/', '_')
      .replace('=', '_')
      .replace('+', '_')
      .toUpperCase(Locale.ROOT);
  }

  @Override
  public void close()
    throws Exception
  {
    try {
      MDC.put("Container", "*");
      MDC.put("PID", "*");
      MDC.put("Source", "supervisor");

      LOG.debug("Shutting down containers.");
      final var exceptions = new ExceptionTracker<Exception>();
      for (final var entry : this.containers.entrySet()) {
        try {
          final var container = entry.getValue();
          container.close();
        } catch (final Exception e) {
          exceptions.addException(e);
        }
      }

      this.ioSupervisor.shutdown();
      this.ioSupervisor.awaitTermination(5L, TimeUnit.SECONDS);
      exceptions.throwIfNecessary();
    } finally {
      MDC.remove("Container");
      MDC.remove("PID");
      MDC.remove("Source");
    }
  }

  @Override
  public EContainerType start(
    final EContainerSpec spec)
    throws IOException, InterruptedException
  {
    try {
      MDC.put("Container", "*");
      MDC.put("PID", "*");
      MDC.put("Source", "supervisor");

      final var uniqueName =
        "ERVILLA-%s".formatted(this.freshToken());

      final var arguments = new ArrayList<String>();
      arguments.add(this.configuration.podmanExecutable());
      arguments.add("run");
      arguments.add("--rm");
      arguments.add("--interactive");
      arguments.add("--tty");

      for (final var entry : new TreeMap<>(spec.environment()).entrySet()) {
        arguments.add("--env");
        arguments.add("%s=%s".formatted(entry.getKey(), entry.getValue()));
      }
      for (final var port : spec.ports()) {
        arguments.add("--publish");
        arguments.add(portSpec(port));
      }

      arguments.add("--name");
      arguments.add(uniqueName);
      arguments.add(spec.fullImageName());
      arguments.addAll(spec.arguments());

      final var process =
        this.executeLogged(
          Optional.of(uniqueName),
          "podman",
          arguments,
          IGNORING
        );

      MDC.put("Container", uniqueName);
      MDC.put("PID", Long.toUnsignedString(process.pid()));

      final var container =
        new EContainer(this, this.configuration, spec, uniqueName, process);

      this.containers.put(uniqueName, container);
      this.startCheckContainerUp(uniqueName, container);
      startCheckContainerReady(spec, container);

      LOG.debug("Container appears to be running.");
      return container;
    } finally {
      MDC.remove("Container");
      MDC.remove("PID");
      MDC.remove("Source");
    }
  }

  private static void startCheckContainerReady(
    final EContainerSpec spec,
    final EContainer container)
    throws InterruptedException
  {
    /*
     * Run the ready check; the container might be "up", but the application
     * within it might not be ready yet.
     */

    final var readyCheck = spec.readyCheck();
    while (container.process.isAlive()) {
      try {
        if (readyCheck.isReady()) {
          LOG.debug("Ready check returned true.");
          break;
        }
        LOG.debug("Ready check returned false. Pausing...");
        Thread.sleep(50L);
      } catch (final InterruptedException e) {
        throw e;
      } catch (final Exception e) {
        LOG.trace("Ready check failed: ", e);
      }
    }
  }

  private void startCheckContainerUp(
    final String uniqueName,
    final EContainer container)
    throws IOException, InterruptedException
  {
    /*
     * Run the container "up" check.
     */

    final var psArgs = new ArrayList<String>();
    psArgs.add(this.configuration.podmanExecutable());
    psArgs.add("ps");
    psArgs.add("--filter");
    psArgs.add("name=%s".formatted(uniqueName));
    psArgs.add("--format");
    psArgs.add("{{.Status}}");

    while (container.process.isAlive()) {
      final var statusText =
        new AtomicReference<String>();

      final var statusProc =
        this.executeLogged(
          Optional.of(uniqueName),
          "status",
          psArgs,
          statusText::set
        );

      final int exitCode =
        statusProc.waitFor();

      if (exitCode == 0) {
        final var status = statusText.get();
        if (status != null) {
          if (status.toUpperCase().startsWith("UP ")) {
            break;
          }
          LOG.debug("Status is {} so will continue to wait.", status);
        }
      }

      Thread.sleep(100L);
    }
  }

  private void superviseProcessOutput(
    final Optional<String> container,
    final String command,
    final Process process,
    final Consumer<String> receiver)
  {
    this.ioSupervisor.execute(() -> {
      MDC.put("PID", Long.toUnsignedString(process.pid()));
      container.ifPresent(s -> MDC.put("Container", s));
      MDC.put("Source", command + ": stdout");

      try (var reader = process.inputReader()) {
        while (true) {
          final var line = reader.readLine();
          if (line == null) {
            break;
          }
          LOG.trace("{}", line);
          receiver.accept(line);
        }
      } catch (final Exception e) {
        LOG.trace("", e);
      }
    });
  }

  private void superviseProcessError(
    final Optional<String> container,
    final String command,
    final Process process)
  {
    this.ioSupervisor.execute(() -> {
      MDC.put("PID", Long.toUnsignedString(process.pid()));
      container.ifPresent(s -> MDC.put("Container", s));
      MDC.put("Source", command + ": stderr");

      try (var reader = process.errorReader()) {
        while (true) {
          final var line = reader.readLine();
          if (line == null) {
            break;
          }
          LOG.error("{}", line);
        }
      } catch (final Exception e) {
        LOG.error("", e);
      }
    });
  }

  private Process executeLogged(
    final Optional<String> container,
    final String name,
    final List<String> command,
    final Consumer<String> lineConsumer)
    throws IOException
  {
    LOG.debug("Exec: {}", command);

    final var process =
      new ProcessBuilder(command)
        .start();

    this.superviseProcessError(container, name, process);
    this.superviseProcessOutput(container, name, process, lineConsumer);
    return process;
  }

  private static final class EContainer
    implements EContainerType
  {
    private final String name;
    private final Process process;
    private final EContainerSupervisor supervisor;
    private final EContainerConfiguration configuration;
    private final EContainerSpec spec;

    private EContainer(
      final EContainerSupervisor inSupervisor,
      final EContainerConfiguration inConfiguration,
      final EContainerSpec inSpec,
      final String inName,
      final Process inProcess)
    {
      this.supervisor =
        Objects.requireNonNull(inSupervisor, "supervisor");
      this.configuration =
        Objects.requireNonNull(inConfiguration, "configuration");
      this.spec =
        Objects.requireNonNull(inSpec, "spec");
      this.name =
        Objects.requireNonNull(inName, "name");
      this.process =
        Objects.requireNonNull(inProcess, "process");
    }

    @Override
    public String name()
    {
      return this.name;
    }

    @Override
    public int executeAndWait(
      final List<String> command,
      final long time,
      final TimeUnit unit)
      throws InterruptedException, IOException
    {
      Objects.requireNonNull(command, "command");
      Objects.requireNonNull(unit, "unit");

      try {
        MDC.put("Container", this.name);
        MDC.put("PID", Long.toUnsignedString(this.process.pid()));
        MDC.put("Source", "supervisor");

        final var execArgs = new ArrayList<String>();
        execArgs.add(this.configuration.podmanExecutable());
        execArgs.add("exec");
        execArgs.add(this.name);
        execArgs.addAll(command);

        final var execProcess =
          this.supervisor.executeLogged(
            Optional.of(this.name),
            command.get(0),
            execArgs,
            IGNORING
          );

        execProcess.waitFor(time, unit);
        return execProcess.exitValue();
      } finally {
        MDC.remove("Container");
        MDC.remove("PID");
        MDC.remove("Source");
      }
    }

    @Override
    public int executeAndWaitIndefinitely(
      final List<String> command)
      throws IOException, InterruptedException
    {
      return this.executeAndWait(command, Long.MAX_VALUE, TimeUnit.SECONDS);
    }

    @Override
    public void copyInto(
      final Path source,
      final String destination)
      throws InterruptedException, IOException
    {
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(destination, "destination");

      try {
        MDC.put("Container", this.name);
        MDC.put("PID", Long.toUnsignedString(this.process.pid()));
        MDC.put("Source", "supervisor");

        final var copyArgs = new ArrayList<String>();
        copyArgs.add(this.configuration.podmanExecutable());
        copyArgs.add("cp");
        copyArgs.add(source.toAbsolutePath().toString());
        copyArgs.add("%s:%s".formatted(this.name, destination));

        final var copyProcess =
          this.supervisor.executeLogged(
            Optional.of(this.name),
            "cp",
            copyArgs,
            IGNORING
          );

        copyProcess.waitFor();
        if (copyProcess.exitValue() != 0) {
          throw new IOException(
            "Process '%s' returned exit code %d"
              .formatted(
                copyArgs,
                Integer.valueOf(copyProcess.exitValue())
              )
          );
        }
      } finally {
        MDC.remove("Container");
        MDC.remove("PID");
        MDC.remove("Source");
      }
    }

    @Override
    public void copyFrom(
      final String source,
      final Path destination)
      throws InterruptedException, IOException
    {
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(destination, "destination");

      try {
        MDC.put("Container", this.name);
        MDC.put("PID", Long.toUnsignedString(this.process.pid()));
        MDC.put("Source", "supervisor");

        final var copyArgs = new ArrayList<String>();
        copyArgs.add(this.configuration.podmanExecutable());
        copyArgs.add("cp");
        copyArgs.add("%s:%s".formatted(this.name, source));
        copyArgs.add(destination.toAbsolutePath().toString());

        final var copyProcess =
          this.supervisor.executeLogged(
            Optional.of(this.name),
            "cp",
            copyArgs,
            IGNORING
          );

        copyProcess.waitFor();
        if (copyProcess.exitValue() != 0) {
          throw new IOException(
            "Process '%s' returned exit code %d"
              .formatted(
                copyArgs,
                Integer.valueOf(copyProcess.exitValue())
              )
          );
        }
      } finally {
        MDC.remove("Container");
        MDC.remove("PID");
        MDC.remove("Source");
      }
    }

    @Override
    public void close()
      throws Exception
    {
      try {
        MDC.put("Container", this.name);
        MDC.put("PID", Long.toUnsignedString(this.process.pid()));
        MDC.put("Source", "supervisor");

        LOG.debug("Shutting down.");
        if (!this.process.isAlive()) {
          LOG.debug("Container is already dead.");
          return;
        }

        final var stopArgs = new ArrayList<String>(6);
        stopArgs.add(this.configuration.podmanExecutable());
        stopArgs.add("stop");
        stopArgs.add("--ignore");
        stopArgs.add("--time");
        stopArgs.add("1");
        stopArgs.add(this.name);

        final var stopProc =
          this.supervisor.executeLogged(
            Optional.of(this.name),
            "stop",
            stopArgs,
            IGNORING
          );

        try {
          MDC.pushByKey("PID", Long.toUnsignedString(stopProc.pid()));
          LOG.debug("Waiting for 'stop' invocation.");
          stopProc.waitFor(10L, TimeUnit.SECONDS);
          LOG.debug("Status {}", stopProc);
        } finally {
          MDC.popByKey("PID");
        }

        LOG.debug("Waiting for container shutdown.");
        this.process.waitFor(10L, TimeUnit.SECONDS);
        LOG.debug("Status {}", this.process);
        if (this.process.isAlive()) {
          LOG.warn("Container appears to still be running!");
        }
      } finally {
        MDC.remove("Container");
        MDC.remove("PID");
        MDC.remove("Source");
      }
    }
  }
}
