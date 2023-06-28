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

import com.io7m.ervilla.api.EContainerSpec;
import com.io7m.ervilla.api.EPortPublish;

import java.util.Optional;

import static com.io7m.ervilla.api.EPortProtocol.TCP;

/**
 * Convenience functions for PostgreSQL container specs.
 */

public final class EPgSpecs
{
  private EPgSpecs()
  {

  }

  /**
   * Create a new builder setting up a PostgreSQL container.
   *
   * @param repository       The repository (such as "docker.io")
   * @param version          The version (such as "15.3-alpine3.18")
   * @param address          The listen address (or localhost, if not specified)
   * @param hostPort         The host port (such as 5432)
   * @param databaseName     The name of the database that will be created
   * @param databaseUser     The name of the database owner
   * @param databasePassword The password of the database owner
   *
   * @return A spec builder
   */

  public static EContainerSpec.Builder builder(
    final String repository,
    final String version,
    final Optional<String> address,
    final int hostPort,
    final String databaseName,
    final String databaseUser,
    final String databasePassword)
  {
    return EContainerSpec.builder(
        repository,
        "postgres",
        version
      )
      .addPublishPort(new EPortPublish(
        address,
        hostPort,
        5432,
        TCP
      ))
      .addEnvironmentVariable("POSTGRES_DB", databaseName)
      .addEnvironmentVariable("POSTGRES_PASSWORD", databasePassword)
      .addEnvironmentVariable("POSTGRES_USER", databaseUser)
      .setReadyCheck(EPgReadyCheck.create(
        address.orElse("localhost"),
        hostPort,
        databaseUser,
        databasePassword,
        databaseName
      ));
  }

  /**
   * Create a new builder setting up a PostgreSQL container from docker.io.
   *
   * @param version          The version (such as "15.3-alpine3.18")
   * @param address          The listen address (or localhost, if not specified)
   * @param hostPort         The host port (such as 5432)
   * @param databaseName     The name of the database that will be created
   * @param databaseUser     The name of the database owner
   * @param databasePassword The password of the database owner
   *
   * @return A spec builder
   */

  public static EContainerSpec.Builder builderFromDockerIO(
    final String version,
    final Optional<String> address,
    final int hostPort,
    final String databaseName,
    final String databaseUser,
    final String databasePassword)
  {
    return builder(
      "docker.io",
      version,
      address,
      hostPort,
      databaseName,
      databaseUser,
      databasePassword
    );
  }
}
