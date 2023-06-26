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

package com.io7m.ervilla.test_extension;

import com.io7m.ervilla.api.EContainerBackend;
import com.io7m.ervilla.api.EContainerConfiguration;
import com.io7m.ervilla.api.EContainerSupervisorType;
import com.io7m.ervilla.native_exec.ENContainerSupervisors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/**
 * The Ervilla test extension.
 */

public final class ErvillaExtension
  implements ParameterResolver,
  BeforeAllCallback,
  BeforeEachCallback,
  AfterEachCallback,
  AfterAllCallback
{
  private static final Logger LOG =
    LoggerFactory.getLogger(ErvillaExtension.class);
  private static final ENContainerSupervisors SUPERVISORS =
    new ENContainerSupervisors();

  private final ArrayList<EContainerSupervisorType> supervisorsPerTest;
  private final ArrayList<EContainerSupervisorType> supervisorsPerClass;

  /**
   * The Ervilla test extension.
   */

  public ErvillaExtension()
  {
    this.supervisorsPerTest =
      new ArrayList<>();
    this.supervisorsPerClass =
      new ArrayList<>();
  }

  private static Optional<EContainerBackend> supervisorCheckSupport(
    final ExtensionContext context)
    throws InterruptedException
  {
    final var configuration = supervisorGetConfiguration(
      context);

    return SUPERVISORS.isSupported(configuration);
  }

  private static Optional<EContainerSupervisorType> createSupervisor(
    final ExtensionContext context)
    throws Exception
  {
    final var configuration =
      supervisorGetConfiguration(context);
    final var supportOpt =
      supervisorCheckSupport(context);

    if (supportOpt.isPresent()) {
      final var support = supportOpt.get();
      LOG.info("Containers are supported.");
      for (final var entry : support.attributes().entrySet()) {
        LOG.info("Containers: {}: {}", entry.getKey(), entry.getValue());
      }
      return Optional.of(SUPERVISORS.create(configuration));
    }

    LOG.info("Containers are NOT supported.");
    return Optional.empty();
  }

  private static EContainerConfiguration supervisorGetConfiguration(
    final ExtensionContext context)
  {
    final var annotation =
      context.getRequiredTestClass()
        .getAnnotation(ErvillaConfiguration.class);

    final EContainerConfiguration configuration;
    if (annotation == null) {
      configuration = EContainerConfiguration.defaults();
    } else {
      configuration = new EContainerConfiguration(
        annotation.podmanExecutable()
      );
    }
    return configuration;
  }

  private static boolean lackOfSupportIsAllowed(
    final ExtensionContext context)
  {
    final var annotation =
      context.getRequiredTestClass()
        .getAnnotation(ErvillaConfiguration.class);

    if (annotation != null) {
      return annotation.disabledIfUnsupported();
    }
    return false;
  }

  @Override
  public boolean supportsParameter(
    final ParameterContext parameterContext,
    final ExtensionContext extensionContext)
    throws ParameterResolutionException
  {
    final var requiredType =
      parameterContext.getParameter().getType();

    return Objects.equals(
      requiredType,
      EContainerSupervisorType.class
    );
  }

  @Override
  public Object resolveParameter(
    final ParameterContext parameterContext,
    final ExtensionContext extensionContext)
    throws ParameterResolutionException
  {
    final var requiredType =
      parameterContext.getParameter().getType();

    if (Objects.equals(requiredType, EContainerSupervisorType.class)) {
      try {
        final var supervisor =
          createSupervisor(extensionContext).orElseThrow();

        if (parameterContext.findAnnotation(ErvillaCloseAfterAll.class).isPresent()) {
          this.supervisorsPerClass.add(supervisor);
        } else {
          this.supervisorsPerTest.add(supervisor);
        }

        return supervisor;
      } catch (final Exception e) {
        throw new ParameterResolutionException(e.getMessage(), e);
      }
    }

    throw new ParameterResolutionException(
      "Unrecognized requested parameter type: %s".formatted(requiredType)
    );
  }

  @Override
  public void afterAll(
    final ExtensionContext context)
  {
    for (final var supervisor : this.supervisorsPerClass) {
      try {
        supervisor.close();
      } catch (final Exception e) {
        LOG.error("Error closing supervisor: ", e);
      }
    }

    this.supervisorsPerClass.clear();
  }

  @Override
  public void afterEach(
    final ExtensionContext context)
  {
    for (final var supervisor : this.supervisorsPerTest) {
      try {
        supervisor.close();
      } catch (final Exception e) {
        LOG.error("Error closing supervisor: ", e);
      }
    }

    this.supervisorsPerTest.clear();
  }

  @Override
  public void beforeAll(
    final ExtensionContext context)
    throws Exception
  {
    if (supervisorCheckSupport(context).isEmpty()) {
      if (lackOfSupportIsAllowed(context)) {
        Assumptions.abort(
          "Containers are not supported, but tests are marked as disabled.");
      } else {
        throw new IllegalStateException("Containers are not supported!");
      }
    }
  }

  @Override
  public void beforeEach(
    final ExtensionContext context)
  {

  }
}
