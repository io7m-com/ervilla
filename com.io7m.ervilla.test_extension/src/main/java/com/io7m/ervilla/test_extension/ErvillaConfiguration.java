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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Configuration information for the Ervilla extension.
 */

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ErvillaConfiguration
{
  /**
   * @return The project name
   */

  String projectName();

  /**
   * @return The podman executable used
   */

  String podmanExecutable() default "podman";

  /**
   * @return {@code true} if tests should be disabled if containers are not supported
   */

  boolean disabledIfUnsupported() default false;

  /**
   * @return The startup wait time
   */

  long startupWaitTime() default 30L;

  /**
   * @return The startup wait time unit
   */

  TimeUnit startupWaitTimeUnit() default TimeUnit.SECONDS;

  /**
   * @return The liveness check pause time
   */

  long livenessCheckPauseTime() default 250L;

  /**
   * @return The liveness check pause time unit
   */

  TimeUnit livenessCheckPauseTimeUnit() default TimeUnit.MILLISECONDS;

  /**
   * @return {@code true} if debug logging is enabled
   */

  boolean debugLogging() default false;
}
