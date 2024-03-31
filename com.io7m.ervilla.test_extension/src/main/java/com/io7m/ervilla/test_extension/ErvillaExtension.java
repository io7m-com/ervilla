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
import com.io7m.ervilla.api.EContainerSupervisorScope;
import com.io7m.ervilla.api.EContainerSupervisorType;
import com.io7m.ervilla.test_extension.internal.ErvillaSingletons;
import com.io7m.lanark.core.RDottedName;
import org.junit.jupiter.api.Assertions;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

/**
 * The Ervilla test extension.
 */

public final class ErvillaExtension
  implements ParameterResolver,
  BeforeAllCallback,
  AfterEachCallback,
  AfterAllCallback,
  BeforeEachCallback
{
  private static final Logger LOG =
    LoggerFactory.getLogger(ErvillaExtension.class);

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

  /**
   * Check if containers are supported based on the configuration annotations (if any)
   * given on the test class referenced by the extension context.
   *
   * @param context The context
   *
   * @return A backend, if containers are supported, or nothing otherwise
   *
   * @throws InterruptedException On interruption
   */

  public static Optional<EContainerBackend> supervisorCheckSupport(
    final ExtensionContext context)
    throws InterruptedException
  {
    Objects.requireNonNull(context, "context");

    final var configuration =
      supervisorGetConfiguration(context);

    return ErvillaSingletons.SUPERVISORS.isSupported(configuration);
  }

  /**
   * Create a supervisor based on the configuration derived from the
   * annotations on the calling class. This is equivalent to calling
   * {@link #supervisorGetConfiguration(ExtensionContext)},
   * {@link #supervisorCheckSupport(ExtensionContext)} and then
   * creating a supervisor using the default supervisor factory.
   *
   * @param context The extension context
   * @param scope
   *
   * @return A supervisor, if containers are supported
   *
   * @throws InterruptedException On interruption
   * @throws Exception            On errors
   */

  public Optional<EContainerSupervisorType> supervisorCreate(
    final ExtensionContext context,
    final EContainerSupervisorScope scope)
    throws InterruptedException, Exception
  {
    Objects.requireNonNull(context, "context");

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

      return switch (scope) {
        case PER_SUITE -> {
          yield Optional.of(ErvillaSingletons.perSuiteSupervisor(configuration));
        }

        case PER_CLASS -> {
          final EContainerSupervisorType supervisor =
            ErvillaSingletons.perClassSupervisor(configuration);

          this.supervisorsPerClass.add(supervisor);
          yield Optional.of(supervisor);
        }

        case PER_TEST -> {
          final EContainerSupervisorType supervisor =
            ErvillaSingletons.perTestSupervisor(configuration);

          this.supervisorsPerTest.add(supervisor);
          yield Optional.of(supervisor);
        }
      };
    }

    LOG.info("Containers are NOT supported.");
    return Optional.empty();
  }

  /**
   * Get an appropriate supervisor configuration. This can be derived from
   * annotations on the calling test class, or the defaults can be returned
   * otherwise.
   *
   * @param context The extension context
   *
   * @return A supervisor configuration
   */

  public static EContainerConfiguration supervisorGetConfiguration(
    final ExtensionContext context)
  {
    Objects.requireNonNull(context, "context");

    final var annotation =
      context.getRequiredTestClass()
        .getAnnotation(ErvillaConfiguration.class);

    final EContainerConfiguration configuration;
    if (annotation == null) {
      configuration = EContainerConfiguration.defaults(
        new RDottedName(annotation.projectName())
      );
    } else {
      configuration = new EContainerConfiguration(
        new RDottedName(annotation.projectName()),
        annotation.podmanExecutable(),
        Duration.of(
          annotation.startupWaitTime(),
          annotation.startupWaitTimeUnit().toChronoUnit()
        ),
        Duration.of(
          annotation.livenessCheckPauseTime(),
          annotation.livenessCheckPauseTimeUnit().toChronoUnit()
        ),
        annotation.debugLogging()
      );
    }
    return configuration;
  }

  /**
   * Determine if a lack of container support is allowed. A lack of support
   * is allowed if there's an appropriate annotation on the calling test class
   * that says that tests can be skipped.
   *
   * @param context The extension context
   *
   * @return {@code true} if a lack of container support is acceptable
   */

  public static boolean isLackOfContainerSupportAllowed(
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

  /**
   * If supervisors are not supported, but the configuration says this is
   * acceptable, "fail" using {@link Assumptions#abort()}. If supervisors
   * are not supported, but the configuration says this is _not_ acceptable,
   * then fail using {@link Assertions#fail()}. Otherwise, do nothing.
   *
   * @param context The extension context
   *
   * @throws InterruptedException On interruption
   */

  public static void supervisorFailBasedOnSupportAppropriately(
    final ExtensionContext context)
    throws InterruptedException
  {
    if (supervisorCheckSupport(context).isEmpty()) {
      if (isLackOfContainerSupportAllowed(context)) {
        Assumptions.abort(
          "Containers are not supported, but tests are marked as disabled.");
      } else {
        Assertions.fail(
          "Containers are not supported!"
        );
      }
    }
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

  private static EContainerSupervisorScope determineScope(
    final ParameterContext parameterContext)
  {
    final var afterClassOpt =
      parameterContext.findAnnotation(ErvillaCloseAfterClass.class);
    final var afterSuiteOpt =
      parameterContext.findAnnotation(ErvillaCloseAfterSuite.class);

    if (afterClassOpt.isPresent() && afterSuiteOpt.isPresent()) {
      throw new ParameterResolutionException(
        "Specifying both %s and %s is not allowed."
          .formatted(
            ErvillaCloseAfterClass.class,
            ErvillaCloseAfterSuite.class
          )
      );
    }

    if (afterSuiteOpt.isPresent()) {
      return EContainerSupervisorScope.PER_SUITE;
    }

    if (afterClassOpt.isPresent()) {
      return EContainerSupervisorScope.PER_CLASS;
    }

    return EContainerSupervisorScope.PER_TEST;
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
        return this.supervisorCreate(
          extensionContext,
          determineScope(parameterContext)
        ).orElseThrow();
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
    LOG.debug(
      "Closing {} per-class supervisors.",
      Integer.valueOf(this.supervisorsPerClass.size())
    );

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
    LOG.debug(
      "Closing {} per-test supervisors.",
      Integer.valueOf(this.supervisorsPerTest.size())
    );

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
    supervisorFailBasedOnSupportAppropriately(context);
    this.registerSuiteCloseable(context);
  }

  private void registerSuiteCloseable(
    final ExtensionContext context)
  {
    context.getRoot()
      .getStore(GLOBAL)
      .put(OnSuiteEnd.class.getCanonicalName(), new OnSuiteEnd());
  }

  @Override
  public void beforeEach(
    final ExtensionContext context)
  {
    context.getStore(GLOBAL)
      .put(ErvillaExtension.class.getCanonicalName(), this);
    this.registerSuiteCloseable(context);
  }

  private final class OnSuiteEnd
    implements ExtensionContext.Store.CloseableResource
  {
    private OnSuiteEnd()
    {

    }

    @Override
    public void close()
      throws Exception
    {
      ErvillaSingletons.close();
    }
  }
}
