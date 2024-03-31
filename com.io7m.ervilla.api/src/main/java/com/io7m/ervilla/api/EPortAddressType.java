/*
 * Copyright © 2024 Mark Raynsford <code@io7m.com> https://www.io7m.com
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

/**
 * The address to which to bind a port.
 */

public sealed interface EPortAddressType
{
  /**
   * @return The address string to be used externally to connect to the bound port
   */

  String targetAddress();

  /**
   * Bind to all IPv4 addresses ("0.0.0.0").
   */

  record AllIPv4()
    implements EPortAddressType
  {
    @Override
    public String targetAddress()
    {
      return "127.0.0.1";
    }
  }

  /**
   * Bind to all IPv6 addresses ("::").
   */

  record AllIPv6()
    implements EPortAddressType
  {
    @Override
    public String targetAddress()
    {
      return "[::1]";
    }
  }

  /**
   * Bind to all addresses ("0.0.0.0" and "::").
   */

  record All()
    implements EPortAddressType
  {
    @Override
    public String targetAddress()
    {
      return "[::1]";
    }
  }

  /**
   * Bind to a specific IPv6 address.
   *
   * @param address The host address
   */

  record Address6(String address)
    implements EPortAddressType
  {
    /**
     * Bind to a specific IPv6 address.
     */

    public Address6
    {
      Objects.requireNonNull(address, "address");
    }

    @Override
    public String targetAddress()
    {
      return "[%s]".formatted(this.address);
    }
  }

  /**
   * Bind to a specific IPv4 address.
   *
   * @param address The host address
   */

  record Address4(String address)
    implements EPortAddressType
  {
    /**
     * Bind to a specific IPv4 address.
     */

    public Address4
    {
      Objects.requireNonNull(address, "address");
    }

    @Override
    public String targetAddress()
    {
      return this.address;
    }
  }
}
