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

import com.io7m.ervilla.api.EContainerReference;
import com.io7m.ervilla.native_exec.internal.EContainerStore;
import com.io7m.lanark.core.RDottedName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.io7m.ervilla.api.EContainerSupervisorScope.PER_TEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class EContainerStoreTest
{
  @Test
  public void testWrongProject(
    final @TempDir Path directory)
    throws IOException
  {
    try (var store = EContainerStore.open(
      new RDottedName("com.io7m.example"),
      directory.resolve("ervilla.db"),
      UUID.randomUUID(),
      PER_TEST
    )) {

    }

    final var ex =
      assertThrows(IOException.class, () -> {
        try (var store = EContainerStore.open(
          new RDottedName("com.io7m.example2"),
          directory.resolve("ervilla.db"),
          UUID.randomUUID(),
          PER_TEST
        )) {

        }
      });

    assertTrue(ex.getMessage().startsWith("Database project ID is"));
  }

  @Test
  public void testCreatePod(
    final @TempDir Path directory)
    throws IOException
  {
    try (var store = EContainerStore.open(
      new RDottedName("com.io7m.example"),
      directory.resolve("ervilla.db"),
      UUID.randomUUID(),
      PER_TEST
    )) {
      store.podPut("pod0");
      assertEquals(Set.of("pod0"), store.podList());

      store.podPut("pod0");
      assertEquals(Set.of("pod0"), store.podList());
    }

    try (var store = EContainerStore.open(
      new RDottedName("com.io7m.example"),
      directory.resolve("ervilla.db"),
      UUID.randomUUID(),
      PER_TEST
    )) {
      assertEquals(Set.of("pod0"), store.podList());
    }

    try (var store = EContainerStore.open(
      new RDottedName("com.io7m.example"),
      directory.resolve("ervilla.db"),
      UUID.randomUUID(),
      PER_TEST
    )) {
      store.podDelete("pod0");
      assertEquals(Set.of(), store.podList());
    }

    try (var store = EContainerStore.open(
      new RDottedName("com.io7m.example"),
      directory.resolve("ervilla.db"),
      UUID.randomUUID(),
      PER_TEST
    )) {
      assertEquals(Set.of(), store.podList());
    }
  }

  @Test
  public void testCreateContainer(
    final @TempDir Path directory)
    throws IOException
  {
    try (var store = EContainerStore.open(
      new RDottedName("com.io7m.example"),
      directory.resolve("ervilla.db"),
      UUID.randomUUID(),
      PER_TEST
    )) {
      final var c0 = new EContainerReference("c0", Optional.empty());
      store.containerPut(c0);
      assertEquals(Set.of(c0), store.containerList());

      store.containerPut(c0);
      assertEquals(Set.of(c0), store.containerList());
    }

    try (var store = EContainerStore.open(
      new RDottedName("com.io7m.example"),
      directory.resolve("ervilla.db"),
      UUID.randomUUID(),
      PER_TEST
    )) {
      final var c0 = new EContainerReference("c0", Optional.empty());
      assertEquals(Set.of(c0), store.containerList());
    }

    try (var store = EContainerStore.open(
      new RDottedName("com.io7m.example"),
      directory.resolve("ervilla.db"),
      UUID.randomUUID(),
      PER_TEST
    )) {
      final var c0 = new EContainerReference("c0", Optional.empty());
      store.containerDelete(c0);
      assertEquals(Set.of(), store.containerList());
    }

    try (var store = EContainerStore.open(
      new RDottedName("com.io7m.example"),
      directory.resolve("ervilla.db"),
      UUID.randomUUID(),
      PER_TEST
    )) {
      assertEquals(Set.of(), store.containerList());
    }
  }

  @Test
  public void testPodDeleteReferenced(
    final @TempDir Path directory)
    throws IOException
  {
    try (var store = EContainerStore.open(
      new RDottedName("com.io7m.example"),
      directory.resolve("ervilla.db"),
      UUID.randomUUID(),
      PER_TEST
    )) {
      store.podPut("pod0");
      final var c0 = new EContainerReference("c0", Optional.of("pod0"));
      store.containerPut(c0);
      assertEquals(Set.of(c0), store.containerList());

      assertThrows(IOException.class, () -> {
        store.podDelete("pod0");
      });

      store.containerDelete(c0);
      store.podDelete("pod0");

      assertEquals(Set.of(), store.containerList());
      assertEquals(Set.of(), store.podList());
    }
  }
}
