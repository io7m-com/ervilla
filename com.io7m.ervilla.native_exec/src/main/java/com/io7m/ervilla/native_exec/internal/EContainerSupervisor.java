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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A container supervisor.
 */

public final class EContainerSupervisor implements EContainerSupervisorType
{
  private static final Logger LOG =
    LoggerFactory.getLogger(EContainerSupervisor.class);

  private final EContainerConfiguration configuration;
  private final HashMap<String, EContainer> containers;
  private final ExecutorService ioSupervisor;

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
  }

  @Override
  public void close()
    throws Exception
  {
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

  @Override
  public EContainerType start(
    final EContainerSpec spec)
    throws IOException
  {
    final var uniqueName =
      "ERVILLA-%s".formatted(UUID.randomUUID());

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
    LOG.debug("Exec: {}", arguments);

    final var container =
      new EContainer(
        this.configuration,
        spec,
        uniqueName,
        new ProcessBuilder(arguments).start()
      );

    this.containers.put(uniqueName, container);

    this.ioSupervisor.execute(() -> {
      try (var reader = container.process.errorReader()) {
        while (true) {
          final var line = reader.readLine();
          if (line == null) {
            break;
          }
          LOG.error("{}: {}", container.name, line);
        }
      } catch (final Exception e) {
        LOG.error("{}: ", container.name, e);
      }
    });

    this.ioSupervisor.execute(() -> {
      try (var reader = container.process.inputReader()) {
        while (true) {
          final var line = reader.readLine();
          if (line == null) {
            break;
          }
          LOG.debug("{}: {}", container.name, line);
        }
      } catch (final Exception e) {
        LOG.debug("{}: ", container.name, e);
      }
    });

    return container;
  }

  private static final class EContainer
    implements EContainerType
  {
    private final String name;
    private final Process process;
    private final EContainerConfiguration configuration;
    private final EContainerSpec spec;
    private final AtomicBoolean closed;

    private EContainer(
      final EContainerConfiguration inConfiguration,
      final EContainerSpec inSpec,
      final String inName,
      final Process inProcess)
    {
      this.configuration =
        Objects.requireNonNull(inConfiguration, "configuration");
      this.spec =
        Objects.requireNonNull(inSpec, "spec");
      this.name =
        Objects.requireNonNull(inName, "name");
      this.process =
        Objects.requireNonNull(inProcess, "process");
      this.closed =
        new AtomicBoolean(false);
    }

    @Override
    public String name()
    {
      return this.name;
    }

    @Override
    public void close()
      throws Exception
    {
      if (this.closed.compareAndSet(false, true)) {
        final var stopArgs = new ArrayList<String>(6);
        stopArgs.add(this.configuration.podmanExecutable());
        stopArgs.add("stop");
        stopArgs.add("--ignore");
        stopArgs.add("--time");
        stopArgs.add("5");
        stopArgs.add(this.name);
        LOG.debug("{}: Exec: {}", this.name, stopArgs);

        final var stopProc =
          new ProcessBuilder(stopArgs)
            .start();

        LOG.debug("{}: waiting for {}", this.name, stopProc);
        stopProc.waitFor(10L, TimeUnit.SECONDS);
        LOG.debug("{}: {}", this.name, stopProc);

        LOG.debug("{}: waiting for {}", this.name, this.process);
        this.process.waitFor(10L, TimeUnit.SECONDS);
        LOG.debug("{}: {}", this.name, this.process);
      }
    }
  }
}
