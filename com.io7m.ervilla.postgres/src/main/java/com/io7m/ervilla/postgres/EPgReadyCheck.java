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


package com.io7m.ervilla.postgres;

import com.io7m.ervilla.api.EReadyCheckType;
import org.postgresql.PGProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.DriverManager;
import java.util.Objects;
import java.util.Properties;

/**
 * A ready check that succeeds if it can successfully introduce itself to a PostgreSQL server.
 */

public final class EPgReadyCheck implements EReadyCheckType
{
  private static final Logger LOG =
    LoggerFactory.getLogger(EPgReadyCheck.class);

  private final String address;
  private final int port;
  private final String userName;
  private final String password;
  private final String database;

  private EPgReadyCheck(
    final String inAddress,
    final int inPort,
    final String inUserName,
    final String inPassword,
    final String inDatabase)
  {
    this.address =
      Objects.requireNonNull(inAddress, "address");
    this.port = inPort;
    this.userName =
      Objects.requireNonNull(inUserName, "userName");
    this.password =
      Objects.requireNonNull(inPassword, "password");
    this.database =
      Objects.requireNonNull(inDatabase, "database");
  }

  /**
   * A ready check that succeeds if it can successfully introduce itself to
   * a PostgreSQL server.
   *
   * @param inAddress The address
   * @param inPort    The port
   * @param userName  The username
   * @param database  The database name
   * @param password  The database password
   *
   * @return A check
   */

  public static EReadyCheckType create(
    final String inAddress,
    final int inPort,
    final String userName,
    final String password,
    final String database)
  {
    return new EPgReadyCheck(inAddress, inPort, userName, password, database);
  }

  @Override
  public boolean isReady()
    throws Exception
  {
    final var properties = new Properties();
    properties.setProperty(PGProperty.USER.getName(), this.userName);
    properties.setProperty(PGProperty.PASSWORD.getName(), this.password);
    properties.setProperty(PGProperty.PG_HOST.getName(), this.address);
    properties.setProperty(PGProperty.PG_PORT.getName(), Integer.toString(this.port));
    properties.setProperty(PGProperty.PG_DBNAME.getName(), this.database);

    try (var conn = DriverManager.getConnection("jdbc:postgresql://", properties)) {
      return conn.isValid(1000);
    }
  }
}
