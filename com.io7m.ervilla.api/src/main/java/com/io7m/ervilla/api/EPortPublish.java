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

import java.util.Objects;
import java.util.Optional;

/**
 * A request to publish a container port on the host.
 *
 * @param hostIP        The host IP (or localhost, if not specified)
 * @param hostPort      The host port
 * @param containerPort The container port
 * @param protocol      The port protocol
 */

public record EPortPublish(
  Optional<String> hostIP,
  int hostPort,
  int containerPort,
  EPortProtocol protocol)
{
  /**
   * A request to publish a container port on the host.
   *
   * @param hostIP        The host IP (or localhost, if not specified)
   * @param hostPort      The host port
   * @param containerPort The container port
   * @param protocol      The port protocol
   */

  public EPortPublish
  {
    Objects.requireNonNull(hostIP, "hostIP");
    Objects.requireNonNull(protocol, "protocol");
  }
}
