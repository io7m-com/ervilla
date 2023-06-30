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
import com.io7m.ervilla.api.EPortPublish;
import com.io7m.ervilla.api.EVolumeMount;
import com.io7m.ervilla.native_exec.ENContainerSupervisors;
import com.io7m.ervilla.postgres.EPgSpecs;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.io7m.ervilla.api.EPortProtocol.TCP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ENContainerSupervisorsTest
{
  private static final Logger LOG =
    LoggerFactory.getLogger(ENContainerSupervisorsTest.class);

  @Test
  public void testIsSupported()
    throws InterruptedException
  {
    final var supervisors =
      new ENContainerSupervisors();
    final var support =
      supervisors.isSupported(EContainerConfiguration.defaults());

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
          "THIS-DOES-NOT-EXIST",
          30L,
          TimeUnit.SECONDS)
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
      supervisors.isSupported(EContainerConfiguration.defaults())
        .isPresent()
    );

    try (var supervisor =
           supervisors.create(EContainerConfiguration.defaults())) {
      final var c =
        supervisor.start(
          EContainerSpec.builder(
              "quay.io",
              "io7mcom/idstore",
              "1.0.0-beta0013"
            )
            .setImageHash(
              "sha256:c3c679cbda4fc5287743c5a3edc1ffa31babfaf5be6e3b0705f37ee969ff15ec")
            .addPublishPort(new EPortPublish(
              Optional.empty(),
              51000,
              51000,
              TCP
            ))
            .addPublishPort(new EPortPublish(
              Optional.of("[::]"),
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
      supervisors.isSupported(EContainerConfiguration.defaults())
        .isPresent()
    );

    try (var supervisor =
           supervisors.create(EContainerConfiguration.defaults())) {

      final var pod =
        supervisor.createPod(
          List.of(
            new EPortPublish(
              Optional.empty(),
              5432,
              5432,
              TCP
            )
          )
        );

      pod.start(
        EPgSpecs.builderFromDockerIO(
          "15.3-alpine3.18",
          Optional.empty(),
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
      supervisors.isSupported(EContainerConfiguration.defaults())
        .isPresent()
    );

    try (var supervisor =
           supervisors.create(EContainerConfiguration.defaults())) {
      final var c =
        supervisor.start(
          EPgSpecs.builderFromDockerIO(
            "15.3-alpine3.18",
            Optional.of("[::]"),
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

      c.stop();
      c.start();
      c.stop();
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
      supervisors.isSupported(EContainerConfiguration.defaults())
        .isPresent()
    );

    try (var supervisor =
           supervisors.create(EContainerConfiguration.defaults())) {
      final var c =
        supervisor.start(
          EPgSpecs.builderFromDockerIO(
            "15.3-alpine3.18",
            Optional.of("[::]"),
            5432,
            "db-xyz",
            "db-user",
            "db-password"
          ).build()
        );

      LOG.debug("### STOP!");
      c.stop();
      LOG.debug("### START!");
      c.start();
      LOG.debug("### STOP!");
      c.stop();
      LOG.debug("### START!");
      c.start();

      final var e =
        c.executeAndWaitIndefinitely(List.of(
          "psql",
          "--help"
        ));

      assertEquals(0, e);
    }
  }
}
