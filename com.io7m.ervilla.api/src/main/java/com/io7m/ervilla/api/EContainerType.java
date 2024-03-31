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

package com.io7m.ervilla.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A running container.
 */

public interface EContainerType extends AutoCloseable
{
  /**
   * @return The globally unique container name
   */

  String name();

  /**
   * Execute the given command inside the container and wait a fixed
   * time for it to complete.
   *
   * @param command The command and arguments
   * @param time    The maximum time to wait
   * @param unit    The time unit
   *
   * @return The exit code of the command
   *
   * @throws InterruptedException On interruption
   * @throws IOException          If the process cannot be started
   */

  int executeAndWait(
    List<String> command,
    long time,
    TimeUnit unit)
    throws InterruptedException, IOException;

  /**
   * Execute the given command inside the container and wait indefinitely
   * for it to complete.
   *
   * @param command The command and arguments
   *
   * @return The exit code of the command
   *
   * @throws InterruptedException On interruption
   * @throws IOException          If the process cannot be started
   */

  int executeAndWaitIndefinitely(
    List<String> command)
    throws IOException, InterruptedException;

  /**
   * Copy a file or directory into the container.
   *
   * @param source      The source file/directory
   * @param destination The destination inside the container
   *
   * @throws InterruptedException On interruption
   * @throws IOException          If the process cannot be started
   */

  void copyInto(
    Path source,
    String destination)
    throws InterruptedException, IOException;

  /**
   * Copy a file or directory out of the container.
   *
   * @param source      The source file/directory inside the container
   * @param destination The destination on the host
   *
   * @throws InterruptedException On interruption
   * @throws IOException          If the process cannot be started
   */

  void copyFrom(
    String source,
    Path destination)
    throws InterruptedException, IOException;

  /**
   * Stop the container but do not destroy it. The container can be started
   * again with {@link #start()}.
   *
   * @param stop The method used to stop the container
   *
   * @throws InterruptedException On interruption
   * @throws IOException          On errors
   */

  void stop(EContainerStop stop)
    throws InterruptedException, IOException;

  /**
   * Start the container. This is only useful after a {@link #stop(EContainerStop)} as
   * containers are automatically started up when created.
   *
   * @throws InterruptedException On interruption
   * @throws IOException          On errors
   */

  void start()
    throws InterruptedException, IOException;
}
