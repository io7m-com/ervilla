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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

/**
 * A ready check that succeeds if an HTTP client receives a given status.
 */

public final class EReadyCheckHTTPStatus implements EReadyCheckType
{
  private static final Logger LOG =
    LoggerFactory.getLogger(EReadyCheckHTTPStatus.class);

  private final String address;
  private final int port;
  private final int status;
  private final HttpClient httpClient;

  /**
   * A ready check that succeeds if an HTTP client receives a given status.
   *
   * @param inAddress The address
   * @param inPort    The port
   * @param inStatus  The required status
   */

  public EReadyCheckHTTPStatus(
    final String inAddress,
    final int inPort,
    final int inStatus)
  {
    this.address =
      Objects.requireNonNull(inAddress, "address");
    this.port =
      inPort;
    this.status =
      inStatus;
    this.httpClient =
      HttpClient.newHttpClient();
  }

  @Override
  public boolean isReady()
    throws Exception
  {
    final URI target =
      URI.create(
        "http://%s:%d/".formatted(this.address, Integer.valueOf(this.port))
      );

    final var request =
      HttpRequest.newBuilder(target)
        .GET()
        .build();

    final var response =
      this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == this.status) {
      return true;
    }

    LOG.debug("HTTP failed: {}", Integer.valueOf(response.statusCode()));
    LOG.trace("HTTP returned: {}", response.body());
    return false;
  }
}
