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


package com.io7m.ervilla.native_exec;

import com.io7m.ervilla.api.EContainerBackend;
import com.io7m.ervilla.api.EContainerConfiguration;
import com.io7m.ervilla.api.EContainerSupervisorFactoryType;
import com.io7m.ervilla.api.EContainerSupervisorScope;
import com.io7m.ervilla.api.EContainerSupervisorType;
import com.io7m.ervilla.native_exec.internal.EContainerStore;
import com.io7m.ervilla.native_exec.internal.EContainerSupervisor;
import com.io7m.jade.api.ApplicationDirectories;
import com.io7m.jade.api.ApplicationDirectoryConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.lang.ProcessBuilder.Redirect.PIPE;

/**
 * A factory for container supervisors that execute the {@code podman} native
 * executable.
 */

public final class ENContainerSupervisors
  implements EContainerSupervisorFactoryType
{
  private static final Logger LOG =
    LoggerFactory.getLogger(ENContainerSupervisors.class);

  private final ConcurrentHashMap.KeySetView<EContainerSupervisor, Boolean> instances;
  private final UUID id;

  /**
   * A factory for container supervisors that execute the {@code podman} native
   * executable.
   */

  public ENContainerSupervisors()
  {
    this.instances =
      ConcurrentHashMap.newKeySet();
    this.id =
      UUID.randomUUID();
  }

  @Override
  public Optional<EContainerBackend> isSupported(
    final EContainerConfiguration configuration)
    throws InterruptedException
  {
    Objects.requireNonNull(configuration, "configuration");

    try {
      final var process =
        new ProcessBuilder()
          .command(configuration.podmanExecutable(), "version")
          .redirectOutput(PIPE)
          .redirectError(PIPE)
          .start();

      try (var output = bufferedReader(process.getInputStream())) {
        try (var errors = bufferedReader(process.getErrorStream())) {
          final var outputLines =
            output.lines().toList();
          final var errorLines =
            errors.lines().toList();

          process.waitFor(5L, TimeUnit.SECONDS);
          final var code = process.exitValue();
          if (code != 0) {
            errorLines.forEach(s -> LOG.error("{}", s));
          }

          final var attributes = new TreeMap<String, String>();
          for (final var line : outputLines) {
            final var segments = line.split(":", 2);
            if (segments.length == 2) {
              attributes.put(segments[0], segments[1].trim());
            }
          }
          return Optional.of(new EContainerBackend(attributes));
        }
      }
    } catch (final IOException e) {
      LOG.debug("Failed to run {}: ", configuration.podmanExecutable(), e);
      return Optional.empty();
    }
  }

  @Override
  public EContainerSupervisorType create(
    final EContainerConfiguration configuration,
    final EContainerSupervisorScope scope)
    throws Exception
  {
    final var directoryConfiguration =
      ApplicationDirectoryConfiguration.builder()
        .setOverridePropertyName("com.io7m.ervilla.override")
        .setPortablePropertyName("com.io7m.ervilla.portable")
        .setApplicationName("com.io7m.ervilla")
        .build();

    final var directories =
      ApplicationDirectories.get(directoryConfiguration);

    final var projectName =
      configuration.projectName();

    final var storeDbDirectory =
      directories.dataDirectory()
        .resolve(projectName.value());

    Files.createDirectories(storeDbDirectory);

    final var storeDb =
      storeDbDirectory.resolve("containers.db");

    LOG.debug("Container database is {}", storeDb);

    final var store =
      EContainerStore.open(projectName, storeDb, this.id, scope);

    final var instance =
      EContainerSupervisor.create(
        configuration,
        store,
        this.instances::remove,
        this.id,
        scope
      );

    if (this.instances.isEmpty()) {
      LOG.debug("No existing instances: Running cleanup.");
      instance.cleanUpOldContainersAndPods();
    }

    this.instances.add(instance);
    return instance;
  }

  private static BufferedReader bufferedReader(
    final InputStream stream)
  {
    return new BufferedReader(new InputStreamReader(stream));
  }
}
