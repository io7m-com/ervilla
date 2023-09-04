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


package com.io7m.ervilla.native_exec.internal;

import com.io7m.ervilla.api.EContainerReference;
import com.io7m.ervilla.api.EContainerSupervisorScope;
import com.io7m.lanark.core.RDottedName;
import com.io7m.trasco.api.TrArguments;
import com.io7m.trasco.api.TrExecutorConfiguration;
import com.io7m.trasco.api.TrSchemaRevisionSet;
import com.io7m.trasco.vanilla.TrExecutors;
import com.io7m.trasco.vanilla.TrSchemaRevisionSetParsers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.sqlite.SQLiteErrorCode;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.io7m.trasco.api.TrExecutorUpgrade.PERFORM_UPGRADES;
import static java.math.BigInteger.valueOf;

/**
 * A container store. Records the set of created containers so that containers
 * and pods can be destroyed even after a process crash.
 */

public final class EContainerStore implements Closeable
{
  private static final Logger LOG =
    LoggerFactory.getLogger(EContainerStore.class);

  private static final String DATABASE_APPLICATION_ID =
    "com.io7m.ervilla";

  private final SQLiteDataSource dataSource;
  private final UUID instanceId;
  private final EContainerSupervisorScope scope;

  private EContainerStore(
    final SQLiteDataSource inDataSource,
    final UUID inInstanceId,
    final EContainerSupervisorScope inScope)
  {
    this.dataSource =
      Objects.requireNonNull(inDataSource, "dataSource");
    this.instanceId =
      Objects.requireNonNull(inInstanceId, "instanceId");
    this.scope =
      Objects.requireNonNull(inScope, "scope");
  }

  /**
   * Open or create a container store.
   *
   * @param projectName The project name
   * @param file        The store file
   * @param instanceId  The instance ID
   * @param scope       The supervisor scope
   *
   * @return An open store
   *
   * @throws IOException On errors
   */

  public static EContainerStore open(
    final RDottedName projectName,
    final Path file,
    final UUID instanceId,
    final EContainerSupervisorScope scope)
    throws IOException
  {
    Objects.requireNonNull(projectName, "projectName");
    Objects.requireNonNull(file, "file");

    try {
      final var sqlParsers = new TrSchemaRevisionSetParsers();
      final TrSchemaRevisionSet revisions;
      try (var stream = EContainerStore.class.getResourceAsStream(
        "/com/io7m/ervilla/native_exec/internal/database.xml")) {
        revisions = sqlParsers.parse(URI.create("urn:source"), stream);
      }

      final var url = new StringBuilder(128);
      url.append("jdbc:sqlite:");
      url.append(file.toAbsolutePath());

      final var config = new SQLiteConfig();
      config.enforceForeignKeys(true);
      config.setApplicationId(0x45525649);

      final var dataSource = new SQLiteDataSource(config);
      dataSource.setUrl(url.toString());

      try (var connection = dataSource.getConnection()) {
        connection.setAutoCommit(false);
        new TrExecutors().create(
          new TrExecutorConfiguration(
            c -> schemaVersionGet(c, projectName),
            (version, c) -> schemaVersionSet(c, version, projectName),
            event -> {

            },
            revisions,
            PERFORM_UPGRADES,
            TrArguments.empty(),
            connection
          )
        ).execute();
        connection.commit();
      }

      final var store =
        new EContainerStore(dataSource, instanceId, scope);

      try (var connection = dataSource.getConnection()) {
        connection.setAutoCommit(false);
        store.audit(connection, "OPEN");
        EContainerStore.auditCleanup(connection);
        connection.commit();
      } catch (final SQLException e) {
        throw new IOException(e.getMessage(), e);
      }

      return store;
    } catch (final IOException e) {
      throw e;
    } catch (final Exception e) {
      throw new IOException(e.getMessage(), e);
    }
  }

  private static void schemaVersionSet(
    final Connection connection,
    final BigInteger version,
    final RDottedName project)
    throws SQLException
  {
    final String statementText;
    if (Objects.equals(version, BigInteger.ZERO)) {
      statementText = """
        INSERT INTO schema_version
          (version_number, version_application, version_project)
            VALUES (?, ?, ?)
                """;

      try (var statement =
             connection.prepareStatement(statementText)) {
        statement.setLong(1, version.longValueExact());
        statement.setString(2, DATABASE_APPLICATION_ID);
        statement.setString(3, project.value());
        statement.execute();
      }
    } else {
      statementText = "UPDATE schema_version SET version_number = ?";
      try (var statement =
             connection.prepareStatement(statementText)) {
        statement.setLong(1, version.longValueExact());
        statement.execute();
      }
    }
  }

  private static Optional<BigInteger> schemaVersionGet(
    final Connection connection,
    final RDottedName project)
    throws SQLException
  {
    Objects.requireNonNull(connection, "connection");

    try {
      final var statementText = """
        SELECT version_number, version_application, version_project
          FROM schema_version
        """;

      LOG.debug("execute: {}", statementText);

      try (var statement = connection.prepareStatement(statementText)) {
        try (var result = statement.executeQuery()) {
          if (!result.next()) {
            throw new SQLException("schema_version table is empty!");
          }

          final var version =
            result.getLong(1);
          final var applicationCA =
            result.getString(2);
          final var projectName =
            result.getString(3);

          if (!Objects.equals(applicationCA, DATABASE_APPLICATION_ID)) {
            throw new SQLException(
              String.format(
                "Database application ID is %s but should be %s",
                applicationCA,
                DATABASE_APPLICATION_ID
              )
            );
          }

          if (!Objects.equals(projectName, project.value())) {
            throw new SQLException(
              String.format(
                "Database project ID is %s but should be %s",
                projectName,
                project.value()
              )
            );
          }

          return Optional.of(valueOf(version));
        }
      }
    } catch (final SQLException e) {
      if (e.getErrorCode() == SQLiteErrorCode.SQLITE_ERROR.code) {
        connection.rollback();
        return Optional.empty();
      }
      throw e;
    }
  }

  /**
   * Create a pod. Does nothing if the pod already exists.
   *
   * @param name The name
   *
   * @throws IOException On errors
   */

  public void podPut(
    final String name)
    throws IOException
  {
    Objects.requireNonNull(name, "name");

    final var stText = """
      INSERT INTO pods (p_name) VALUES (?)
        ON CONFLICT DO NOTHING
      """;

    try (var connection = this.dataSource.getConnection()) {
      connection.setAutoCommit(false);

      try (var st = connection.prepareStatement(stText)) {
        st.setString(1, name);
        final var count = st.executeUpdate();
        if (count > 0) {
          this.audit(connection, "POD CREATE %s".formatted(name));
        }
      }

      connection.commit();
    } catch (final SQLException e) {
      throw new IOException(e.getMessage(), e);
    }
  }

  private void audit(
    final Connection connection,
    final String text)
    throws SQLException
  {
    final var stText = """
      INSERT INTO audit (
        a_instance,
        a_scope,
        a_time_ms,
        a_text
      ) VALUES (
        ?,
        ?,
        ?,
        ?
      )
      """;

    try (var st = connection.prepareStatement(stText)) {
      st.setString(1, this.instanceId.toString());
      st.setString(2, this.scope.toString());
      st.setLong(3, Instant.now().toEpochMilli());
      st.setString(4, text);
      st.executeUpdate();
    }
  }

  private static void auditCleanup(
    final Connection connection)
    throws SQLException
  {
    final var stText = """
      DELETE FROM audit WHERE audit.a_time_ms < ?
      """;

    final var timeNow =
      Instant.now();
    final var timeThen =
      timeNow.minus(30L, ChronoUnit.DAYS);

    try (var st = connection.prepareStatement(stText)) {
      st.setLong(1, timeThen.toEpochMilli());
      final var count = st.executeUpdate();
      LOG.debug("Deleted {} old audit entries", Integer.valueOf(count));
    }
  }

  /**
   * Create a container. Does nothing if the container already exists.
   *
   * @param reference The container reference
   *
   * @throws IOException On errors
   */

  public void containerPut(
    final EContainerReference reference)
    throws IOException
  {
    final var stText = """
      INSERT INTO containers (c_name, c_pod)
        VALUES (?, (SELECT p_id FROM pods WHERE p_name = ?))
          ON CONFLICT DO NOTHING
      """;

    try (var connection = this.dataSource.getConnection()) {
      connection.setAutoCommit(false);

      try (var st = connection.prepareStatement(stText)) {
        st.setString(1, reference.name());
        st.setString(2, reference.pod().orElse(null));
        final var count = st.executeUpdate();
        if (count > 0) {
          this.audit(
            connection,
            "CONTAINER CREATE %s".formatted(reference.name())
          );
        }
      }

      connection.commit();
    } catch (final SQLException e) {
      throw new IOException(e.getMessage(), e);
    }
  }

  /**
   * List containers.
   *
   * @return The containers
   *
   * @throws IOException On errors
   */

  public Set<EContainerReference> containerList()
    throws IOException
  {
    final var results =
      new HashSet<EContainerReference>();

    final var stText = """
      SELECT
        c_name,
        p_name
      FROM
        containers
        LEFT OUTER JOIN pods ON containers.c_pod = pods.p_id
      """;

    try (var connection = this.dataSource.getConnection()) {
      connection.setAutoCommit(false);

      try (var st = connection.prepareStatement(stText)) {
        try (var set = st.executeQuery()) {
          while (set.next()) {
            final var cName =
              set.getString(1);
            final var pName =
              Optional.ofNullable(set.getString(2));

            results.add(new EContainerReference(cName, pName));
          }
        }
      }
    } catch (final SQLException e) {
      throw new IOException(e.getMessage(), e);
    }

    return Set.copyOf(results);
  }

  /**
   * Delete a container. Does nothing if the container does not exist.
   *
   * @param reference The container reference
   *
   * @throws IOException On errors
   */

  public void containerDelete(
    final EContainerReference reference)
    throws IOException
  {
    final var stText =
      "DELETE FROM containers WHERE containers.c_name = ?";

    try (var connection = this.dataSource.getConnection()) {
      connection.setAutoCommit(false);

      try (var st = connection.prepareStatement(stText)) {
        st.setString(1, reference.name());
        final var count = st.executeUpdate();
        if (count > 0) {
          this.audit(
            connection,
            "CONTAINER DELETE %s".formatted(reference.name())
          );
        }
      }

      connection.commit();
    } catch (final SQLException e) {
      throw new IOException(e.getMessage(), e);
    }
  }

  /**
   * List pods.
   *
   * @return The pods
   *
   * @throws IOException On errors
   */

  public Set<String> podList()
    throws IOException
  {
    final var results = new HashSet<String>();

    try (var connection = this.dataSource.getConnection()) {
      connection.setAutoCommit(false);

      try (var st = connection.prepareStatement("SELECT p_name FROM pods")) {
        try (var set = st.executeQuery()) {
          while (set.next()) {
            results.add(set.getString(1));
          }
        }
      }
    } catch (final SQLException e) {
      throw new IOException(e.getMessage(), e);
    }

    return Set.copyOf(results);
  }

  /**
   * Delete a pod. Fails if one or more containers reference the pod.
   *
   * @param name The pod name
   *
   * @throws IOException On errors
   */

  public void podDelete(
    final String name)
    throws IOException
  {
    final var stText =
      "DELETE FROM pods WHERE pods.p_name = ?";

    try (var connection = this.dataSource.getConnection()) {
      connection.setAutoCommit(false);

      try (var st = connection.prepareStatement(stText)) {
        st.setString(1, name);
        final var count = st.executeUpdate();
        if (count > 0) {
          this.audit(
            connection,
            "POD DELETE %s".formatted(name)
          );
        }
      }

      connection.commit();
    } catch (final SQLException e) {
      throw new IOException(e.getMessage(), e);
    }
  }

  @Override
  public void close()
    throws IOException
  {
    try (var connection = this.dataSource.getConnection()) {
      connection.setAutoCommit(false);
      this.audit(connection, "CLOSE");
      connection.commit();
    } catch (final SQLException e) {
      throw new IOException(e.getMessage(), e);
    }
  }
}
