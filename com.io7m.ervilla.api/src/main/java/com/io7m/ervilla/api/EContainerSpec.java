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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Parameters needed to start a container.
 *
 * @param registry    The container registry (such as "quay.io")
 * @param imageName   The image name (such as "io7mcom/idstore")
 * @param imageTag    The image tag (such as "1.0.0-beta0013")
 * @param imageHash   The image hash (such as "sha256:ab38fabce3")
 * @param ports       The set of ports to publish
 * @param environment The set of environment variables
 * @param arguments   The entrypoint arguments
 */

public record EContainerSpec(
  String registry,
  String imageName,
  String imageTag,
  Optional<String> imageHash,
  List<EPortPublish> ports,
  Map<String, String> environment,
  List<String> arguments)
{
  /**
   * Parameters needed to start a container.
   *
   * @param registry    The container registry (such as "quay.io")
   * @param imageName   The image name (such as "io7mcom/idstore")
   * @param imageTag    The image tag (such as "1.0.0-beta0013")
   * @param imageHash   The image hash (such as "sha256:ab38fabce3")
   * @param ports       The set of ports to publish
   * @param environment The set of environment variables
   * @param arguments   The entrypoint arguments
   */

  public EContainerSpec
  {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(environment, "environment");
    Objects.requireNonNull(imageHash, "imageHash");
    Objects.requireNonNull(imageName, "imageName");
    Objects.requireNonNull(imageTag, "imageTag");
    Objects.requireNonNull(ports, "ports");
    Objects.requireNonNull(registry, "registry");
  }

  /**
   * @return The full image name
   */

  public String fullImageName()
  {
    return String.format(
      "%s/%s:%s%s",
      this.registry,
      this.imageName,
      this.imageTag,
      this.imageHash.map("@%s"::formatted).orElse("")
    );
  }

  /**
   * Create a new mutable builder for container specs.
   *
   * @param registry  The container registry (such as "quay.io")
   * @param imageName The image name (such as "io7mcom/idstore")
   * @param imageTag  The image tag (such as "1.0.0-beta0013")
   *
   * @return A new mutable builder.
   */

  public static Builder builder(
    final String registry,
    final String imageName,
    final String imageTag)
  {
    return new Builder(registry, imageName, imageTag);
  }

  /**
   * A mutable builder for container specs.
   */

  public static final class Builder
  {
    private final String registry;
    private final String imageName;
    private final String imageTag;
    private Optional<String> imageHash;
    private final List<EPortPublish> ports;
    private final Map<String, String> environment;
    private final List<String> arguments;

    Builder(
      final String inRegistry,
      final String inImageName,
      final String inImageTag)
    {
      this.registry =
        Objects.requireNonNull(inRegistry, "inRegistry");
      this.imageName =
        Objects.requireNonNull(inImageName, "inImageName");
      this.imageTag =
        Objects.requireNonNull(inImageTag, "inImageTag");

      this.imageHash =
        Optional.empty();
      this.ports =
        new ArrayList<>();
      this.environment =
        new HashMap<>();
      this.arguments =
        new ArrayList<>();
    }

    /**
     * Add a published port.
     *
     * @param publish The port
     *
     * @return this
     */

    public Builder addPublishPort(
      final EPortPublish publish)
    {
      this.ports.add(Objects.requireNonNull(publish, "publish"));
      return this;
    }

    /**
     * Add a container environment variable.
     *
     * @param name  The name
     * @param value The value
     *
     * @return this
     */

    public Builder addEnvironmentVariable(
      final String name,
      final String value)
    {
      this.environment.put(
        Objects.requireNonNull(name, "name"),
        Objects.requireNonNull(value, "value")
      );
      return this;
    }

    /**
     * Add an entrypoint argument.
     *
     * @param argument The argument
     *
     * @return this
     */

    public Builder addArgument(
      final String argument)
    {
      this.arguments.add(Objects.requireNonNull(argument, "argument"));
      return this;
    }

    /**
     * Set the image hash.
     *
     * @param hash The hash
     *
     * @return this
     */

    public Builder setImageHash(
      final String hash)
    {
      this.imageHash = Optional.of(hash);
      return this;
    }

    /**
     * @return A container spec based on the parameters so far
     */

    public EContainerSpec build()
    {
      return new EContainerSpec(
        this.registry,
        this.imageName,
        this.imageTag,
        this.imageHash,
        this.ports,
        this.environment,
        this.arguments
      );
    }
  }
}
