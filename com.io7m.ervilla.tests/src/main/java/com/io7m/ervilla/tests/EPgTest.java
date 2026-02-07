/*
 * Copyright © 2026 Mark Raynsford <code@io7m.com> https://www.io7m.com
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

import com.io7m.ervilla.api.EContainerSupervisorType;
import com.io7m.ervilla.api.EContainerType;
import com.io7m.ervilla.api.EPortAddressType;
import com.io7m.ervilla.postgres.EPgSpecs;
import com.io7m.ervilla.test_extension.ErvillaCloseAfterSuite;
import com.io7m.ervilla.test_extension.ErvillaConfiguration;
import com.io7m.ervilla.test_extension.ErvillaExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.ds.PGSimpleDataSource;

@ExtendWith({ErvillaExtension.class})
@ErvillaConfiguration(
  projectName = "com.io7m.ervilla",
  disabledIfUnsupported = true
)
public final class EPgTest
{
  private static EContainerType CONTAINER;

  @BeforeAll
  public static void setupOnce(
    final @ErvillaCloseAfterSuite EContainerSupervisorType containers)
    throws Exception
  {
    final var builder =
      EPgSpecs.builderFromDockerIO(
        "18.1-trixie",
        new EPortAddressType.AllIPv4(),
        5432,
        "postgres",
        "postgres",
        "12345678"
      );

    CONTAINER = containers.start(builder.build());
  }

  @Test
  public void testConnect()
    throws Exception
  {
    final PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUser("postgres");
    dataSource.setPassword("12345678");
    dataSource.setDatabaseName("postgres");
    dataSource.setApplicationName("ervilla");
    dataSource.setServerNames(new String[]{"0.0.0.0",});
    dataSource.setPortNumbers(new int[]{5432,});

    try (var ignored = dataSource.getConnection()) {

    }
  }
}
