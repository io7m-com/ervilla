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

import com.io7m.ervilla.api.EContainerSpec;
import com.io7m.ervilla.api.EContainerSupervisorType;
import com.io7m.ervilla.api.EContainerType;
import com.io7m.ervilla.api.EPortPublish;
import com.io7m.ervilla.test_extension.ErvillaCloseAfterAll;
import com.io7m.ervilla.test_extension.ErvillaConfiguration;
import com.io7m.ervilla.test_extension.ErvillaExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;

import static com.io7m.ervilla.api.EPortProtocol.TCP;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(ErvillaExtension.class)
@ErvillaConfiguration(disabledIfUnsupported = true)
public final class ErvillaExtensionCloseAfterAllTest
{
  private static HashSet<String> NAMES = new HashSet<>();
  private static EContainerType CONTAINER;

  @BeforeAll
  public static void beforeAll(
    final @ErvillaCloseAfterAll EContainerSupervisorType supervisor)
    throws IOException
  {
    CONTAINER =
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
          .addArgument("help")
          .addArgument("version")
          .build()
      );
  }

  @Test
  public void test0()
  {
    NAMES.add(CONTAINER.name());
    assertEquals(1, NAMES.size());
  }

  @Test
  public void test1()
  {
    assertEquals(1, NAMES.size());
    NAMES.add(CONTAINER.name());
    assertEquals(1, NAMES.size());
  }

  @Test
  public void test2()
  {
    assertEquals(1, NAMES.size());
    NAMES.add(CONTAINER.name());
    assertEquals(1, NAMES.size());
  }
}
