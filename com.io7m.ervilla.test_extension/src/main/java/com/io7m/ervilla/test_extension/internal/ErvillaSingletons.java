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


package com.io7m.ervilla.test_extension.internal;

import com.io7m.ervilla.api.EContainerConfiguration;
import com.io7m.ervilla.api.EContainerSupervisorType;
import com.io7m.ervilla.native_exec.ENContainerSupervisors;

import java.util.concurrent.atomic.AtomicReference;

import static com.io7m.ervilla.api.EContainerSupervisorScope.PER_CLASS;
import static com.io7m.ervilla.api.EContainerSupervisorScope.PER_SUITE;
import static com.io7m.ervilla.api.EContainerSupervisorScope.PER_TEST;

/**
 * The extension singletons.
 */

public final class ErvillaSingletons
{
  /**
   * The singleton supervisor factory.
   */

  public static final ENContainerSupervisors SUPERVISORS =
    new ENContainerSupervisors();

  private static AtomicReference<EContainerSupervisorType> PER_SUITE_SUPERVISOR =
    new AtomicReference<>();

  private ErvillaSingletons()
  {

  }

  /**
   * Create or return an existing per-suite supervisor.
   *
   * @param configuration The configuration
   *
   * @return A supervisor
   *
   * @throws Exception On errors
   */

  public static EContainerSupervisorType perSuiteSupervisor(
    final EContainerConfiguration configuration)
    throws Exception
  {
    final var existing = PER_SUITE_SUPERVISOR.get();
    if (existing == null) {
      final var newSupervisor =
        SUPERVISORS.create(configuration, PER_SUITE);
      PER_SUITE_SUPERVISOR.set(newSupervisor);
      return newSupervisor;
    }
    return existing;
  }

  /**
   * Close any per-suite supervisor.
   *
   * @throws Exception On errors
   */

  public static void close()
    throws Exception
  {
    final var existing = PER_SUITE_SUPERVISOR.get();
    if (existing != null) {
      existing.close();
    }
    PER_SUITE_SUPERVISOR.set(null);
  }

  /**
   * Create a per-class supervisor.
   *
   * @param configuration The configuration
   *
   * @return A supervisor
   *
   * @throws Exception On errors
   */

  public static EContainerSupervisorType perClassSupervisor(
    final EContainerConfiguration configuration)
    throws Exception
  {
    return SUPERVISORS.create(configuration, PER_CLASS);
  }

  /**
   * Create a per-test supervisor.
   *
   * @param configuration The configuration
   *
   * @return A supervisor
   *
   * @throws Exception On errors
   */

  public static EContainerSupervisorType perTestSupervisor(
    final EContainerConfiguration configuration)
    throws Exception
  {
    return SUPERVISORS.create(configuration, PER_TEST);
  }
}
