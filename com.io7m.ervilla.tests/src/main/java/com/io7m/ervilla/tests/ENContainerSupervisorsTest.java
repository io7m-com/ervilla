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

package com.io7m.ervilla.tests;

import com.io7m.ervilla.api.EContainerConfiguration;
import com.io7m.ervilla.api.EContainerSpec;
import com.io7m.ervilla.api.EContainerStop;
import com.io7m.ervilla.api.EPortAddressType;
import com.io7m.ervilla.api.EPortPublish;
import com.io7m.ervilla.api.EVolumeMount;
import com.io7m.ervilla.native_exec.ENContainerSupervisors;
import com.io7m.ervilla.postgres.EPgSpecs;
import com.io7m.lanark.core.RDottedName;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.io7m.ervilla.api.EContainerStop.KILL;
import static com.io7m.ervilla.api.EContainerStop.STOP;
import static com.io7m.ervilla.api.EContainerSupervisorScope.PER_TEST;
import static com.io7m.ervilla.api.EPortProtocol.TCP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 30L, unit = TimeUnit.SECONDS)
public final class ENContainerSupervisorsTest
{
  private static final Logger LOG =
    LoggerFactory.getLogger(ENContainerSupervisorsTest.class);

  private static final RDottedName PROJECT_NAME =
    new RDottedName("com.io7m.ervilla");

  private static final String POSTGRES_VERSION =
    "15.6-alpine3.19";

  private static final String IDSTORE_VERSION =
    "1.1.0";

  private static final String IDSTORE_HASH =
    "sha256:e77ad1f7f606a42a6bb0bbc885030a647fa4b90c15d47d5b32f43f1c98475f6e";

  @Test
  public void testIsSupported()
    throws InterruptedException
  {
    final var supervisors =
      new ENContainerSupervisors();
    final var support =
      supervisors.isSupported(EContainerConfiguration.defaults(PROJECT_NAME));

    if (support.isPresent()) {
      final var s = support.get();
      for (final var entry : s.attributes().entrySet()) {
        LOG.debug(
          "{}",
          String.format(
            "%-12s : %s",
            entry.getKey(),
            entry.getValue()));
      }
    }
  }

  @Test
  public void testWrongExecutableNotSupported()
    throws InterruptedException
  {
    final var supervisors =
      new ENContainerSupervisors();
    final var support =
      supervisors.isSupported(
        new EContainerConfiguration(
          PROJECT_NAME,
          "THIS-DOES-NOT-EXIST",
          Duration.ofSeconds(30L),
          Duration.ofMillis(250L),
          true,
          STOP
        )
      );

    assertTrue(support.isEmpty());
  }

  @Test
  public void testRun0(
    final @TempDir Path directory)
    throws Exception
  {
    final var supervisors =
      new ENContainerSupervisors();

    Assumptions.assumeTrue(
      supervisors.isSupported(
        EContainerConfiguration.defaults(PROJECT_NAME))
        .isPresent()
    );

    try (var supervisor =
           supervisors.create(EContainerConfiguration.defaults(PROJECT_NAME), PER_TEST)) {
      final var c =
        supervisor.start(
          EContainerSpec.builder(
              "quay.io",
              "io7mcom/idstore",
              IDSTORE_VERSION
            )
            .setImageHash(IDSTORE_HASH)
            .addPublishPort(new EPortPublish(
              new EPortAddressType.All(),
              51000,
              51000,
              TCP
            ))
            .addPublishPort(new EPortPublish(
              new EPortAddressType.All(),
              51001,
              51001,
              TCP
            ))
            .addEnvironmentVariable("ENV_0", "x")
            .addEnvironmentVariable("ENV_1", "y")
            .addEnvironmentVariable("ENV_2", "z")
            .addVolumeMount(new EVolumeMount(directory, "/x"))
            .addArgument("help")
            .addArgument("version")
            .build()
        );
      assertTrue(c.name().startsWith("ERVILLA-"));
    }
  }

  @Test
  public void testRunPod0(
    final @TempDir Path directory)
    throws Exception
  {
    final var supervisors =
      new ENContainerSupervisors();

    Assumptions.assumeTrue(
      supervisors.isSupported(EContainerConfiguration.defaults(PROJECT_NAME))
        .isPresent()
    );

    try (var supervisor =
           supervisors.create(EContainerConfiguration.defaults(PROJECT_NAME), PER_TEST)) {

      final var pod =
        supervisor.createPod(
          List.of(
            new EPortPublish(
              new EPortAddressType.All(),
              5432,
              5432,
              TCP
            )
          )
        );

      pod.start(
        EPgSpecs.builderFromDockerIO(
          POSTGRES_VERSION,
          new EPortAddressType.All(),
          5432,
          "db-xyz",
          "db-user",
          "db-password"
        ).build()
      );

      pod.start(
        EContainerSpec.builder(
            "docker.io",
            "busybox",
            "1.36.1-musl"
          )
          .addArgument("nc")
          .addArgument("-v")
          .addArgument("-z")
          .addArgument("localhost")
          .addArgument("5432")
          .build()
      );
    }
  }

  @Test
  public void testRunExec(
    final @TempDir Path directory)
    throws Exception
  {
    final var supervisors =
      new ENContainerSupervisors();

    Assumptions.assumeTrue(
      supervisors.isSupported(EContainerConfiguration.defaults(PROJECT_NAME))
        .isPresent()
    );

    try (var supervisor =
           supervisors.create(EContainerConfiguration.defaults(PROJECT_NAME), PER_TEST)) {
      final var c =
        supervisor.start(
          EPgSpecs.builderFromDockerIO(
            POSTGRES_VERSION,
            new EPortAddressType.All(),
            5432,
            "db-xyz",
            "db-user",
            "db-password"
          ).build()
        );

      final var fileIn =
        directory.resolve("HELLO.TXT");
      final var fileOut =
        directory.resolve("GOODBYE.TXT");

      Files.writeString(fileIn, "HELLO!");
      c.copyInto(fileIn, "/HELLO.TXT");
      c.copyFrom("/HELLO.TXT", fileOut);
      c.executeAndWaitIndefinitely(List.of("ls", "/"));

      assertEquals("HELLO!", Files.readString(fileOut));
      assertTrue(c.name().startsWith("ERVILLA-"));

      c.stop(STOP);
      c.start();
      c.stop(KILL);
      c.start();
    }
  }

  @Test
  public void testRunStopStart()
    throws Exception
  {
    final var supervisors =
      new ENContainerSupervisors();

    Assumptions.assumeTrue(
      supervisors.isSupported(EContainerConfiguration.defaults(PROJECT_NAME))
        .isPresent()
    );

    try (var supervisor =
           supervisors.create(EContainerConfiguration.defaults(PROJECT_NAME), PER_TEST)) {
      final var c =
        supervisor.start(
          EPgSpecs.builderFromDockerIO(
            POSTGRES_VERSION,
            new EPortAddressType.All(),
            5432,
            "db-xyz",
            "db-user",
            "db-password"
          ).build()
        );

      LOG.info("### STOP!");
      c.stop(STOP);
      LOG.info("### START!");
      c.start();
      LOG.info("### STOP!");
      c.stop(KILL);
      LOG.info("### START!");
      c.start();

      final var e =
        c.executeAndWaitIndefinitely(List.of(
          "psql",
          "--help"
        ));

      assertEquals(0, e);
    }
  }

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  public void testBindAllIPv4()
    throws Exception
  {
    final var supervisors =
      new ENContainerSupervisors();

    Assumptions.assumeTrue(
      supervisors.isSupported(
          EContainerConfiguration.defaults(PROJECT_NAME))
        .isPresent()
    );

    try (var supervisor =
           supervisors.create(EContainerConfiguration.defaults(PROJECT_NAME), PER_TEST)) {
      final var c =
        supervisor.start(
          EContainerSpec.builder(
              "quay.io",
              "prometheus/busybox",
              "latest"
            )
            .addPublishPort(new EPortPublish(
              new EPortAddressType.AllIPv4(),
              60000,
              60000,
              TCP
            ))
            .addArgument("nc")
            .addArgument("-v")
            .addArgument("-v")
            .addArgument("-v")
            .addArgument("-l")
            .addArgument("-s")
            .addArgument("0.0.0.0")
            .addArgument("-e")
            .addArgument("echo hello")
            .addArgument("60000")
            .build()
        );
      assertTrue(c.name().startsWith("ERVILLA-"));

      while (true) {
        try (var socket = new Socket()) {
          socket.connect(new InetSocketAddress("0.0.0.0", 60000));
          return;
        } catch (final ConnectException e) {
          Thread.sleep(500L);
        }
      }
    }
  }

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  public void testBindAllIPv6()
    throws Exception
  {
    final var supervisors =
      new ENContainerSupervisors();

    Assumptions.assumeTrue(
      supervisors.isSupported(
          EContainerConfiguration.defaults(PROJECT_NAME))
        .isPresent()
    );

    try (var supervisor =
           supervisors.create(EContainerConfiguration.defaults(PROJECT_NAME), PER_TEST)) {
      final var c =
        supervisor.start(
          EContainerSpec.builder(
              "quay.io",
              "prometheus/busybox",
              "latest"
            )
            .addPublishPort(new EPortPublish(
              new EPortAddressType.AllIPv6(),
              60000,
              60000,
              TCP
            ))
            .addArgument("nc")
            .addArgument("-v")
            .addArgument("-v")
            .addArgument("-v")
            .addArgument("-l")
            .addArgument("-s")
            .addArgument("::")
            .addArgument("-e")
            .addArgument("echo hello")
            .addArgument("60000")
            .build()
        );
      assertTrue(c.name().startsWith("ERVILLA-"));

      while (true) {
        try (var socket = new Socket()) {
          socket.connect(new InetSocketAddress("::1", 60000));
          return;
        } catch (final ConnectException e) {
          Thread.sleep(500L);
        }
      }
    }
  }

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  public void testBindAll0()
    throws Exception
  {
    final var supervisors =
      new ENContainerSupervisors();

    Assumptions.assumeTrue(
      supervisors.isSupported(
          EContainerConfiguration.defaults(PROJECT_NAME))
        .isPresent()
    );

    try (var supervisor =
           supervisors.create(EContainerConfiguration.defaults(PROJECT_NAME), PER_TEST)) {
      final var c =
        supervisor.start(
          EContainerSpec.builder(
              "quay.io",
              "prometheus/busybox",
              "latest"
            )
            .addPublishPort(new EPortPublish(
              new EPortAddressType.All(),
              60000,
              60000,
              TCP
            ))
            .addArgument("nc")
            .addArgument("-v")
            .addArgument("-v")
            .addArgument("-v")
            .addArgument("-l")
            .addArgument("-e")
            .addArgument("echo hello")
            .addArgument("60000")
            .build()
        );
      assertTrue(c.name().startsWith("ERVILLA-"));

      while (true) {
        try (var socket = new Socket()) {
          socket.connect(new InetSocketAddress("0.0.0.0", 60000));
          return;
        } catch (final ConnectException e) {
          Thread.sleep(500L);
        }
      }
    }
  }

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  public void testBindAll1()
    throws Exception
  {
    final var supervisors =
      new ENContainerSupervisors();

    Assumptions.assumeTrue(
      supervisors.isSupported(
          EContainerConfiguration.defaults(PROJECT_NAME))
        .isPresent()
    );

    try (var supervisor =
           supervisors.create(EContainerConfiguration.defaults(PROJECT_NAME), PER_TEST)) {
      final var c =
        supervisor.start(
          EContainerSpec.builder(
              "quay.io",
              "prometheus/busybox",
              "latest"
            )
            .addPublishPort(new EPortPublish(
              new EPortAddressType.All(),
              60000,
              60000,
              TCP
            ))
            .addArgument("nc")
            .addArgument("-v")
            .addArgument("-v")
            .addArgument("-v")
            .addArgument("-l")
            .addArgument("-e")
            .addArgument("echo hello")
            .addArgument("60000")
            .build()
        );
      assertTrue(c.name().startsWith("ERVILLA-"));

      while (true) {
        try (var socket = new Socket()) {
          socket.connect(new InetSocketAddress("::1", 60000));
          return;
        } catch (final ConnectException e) {
          Thread.sleep(500L);
        }
      }
    }
  }
}
