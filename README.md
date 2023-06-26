ervilla
===

[![Maven Central](https://img.shields.io/maven-central/v/com.io7m.ervilla/com.io7m.ervilla.svg?style=flat-square)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.io7m.ervilla%22)
[![Maven Central (snapshot)](https://img.shields.io/nexus/s/https/s01.oss.sonatype.org/com.io7m.ervilla/com.io7m.ervilla.svg?style=flat-square)](https://s01.oss.sonatype.org/content/repositories/snapshots/com/io7m/ervilla/)
[![Codecov](https://img.shields.io/codecov/c/github/io7m/ervilla.svg?style=flat-square)](https://codecov.io/gh/io7m/ervilla)

![ervilla](./src/site/resources/ervilla.jpg?raw=true)

| JVM | Platform | Status |
|-----|----------|--------|
| OpenJDK (Temurin) Current | Linux | [![Build (OpenJDK (Temurin) Current, Linux)](https://img.shields.io/github/actions/workflow/status/io7m/ervilla/main.linux.temurin.current.yml)](https://github.com/io7m/ervilla/actions?query=workflow%3Amain.linux.temurin.current)|
| OpenJDK (Temurin) LTS | Linux | [![Build (OpenJDK (Temurin) LTS, Linux)](https://img.shields.io/github/actions/workflow/status/io7m/ervilla/main.linux.temurin.lts.yml)](https://github.com/io7m/ervilla/actions?query=workflow%3Amain.linux.temurin.lts)|
| OpenJDK (Temurin) Current | Windows | [![Build (OpenJDK (Temurin) Current, Windows)](https://img.shields.io/github/actions/workflow/status/io7m/ervilla/main.windows.temurin.current.yml)](https://github.com/io7m/ervilla/actions?query=workflow%3Amain.windows.temurin.current)|
| OpenJDK (Temurin) LTS | Windows | [![Build (OpenJDK (Temurin) LTS, Windows)](https://img.shields.io/github/actions/workflow/status/io7m/ervilla/main.windows.temurin.lts.yml)](https://github.com/io7m/ervilla/actions?query=workflow%3Amain.windows.temurin.lts)|

## ervilla

A minimalist JUnit 5 extension to create and destroy Podman containers
during tests.

### Features

  * Written in pure Java 17.
  * [OSGi](https://www.osgi.org/) ready.
  * [JPMS](https://en.wikipedia.org/wiki/Java_Platform_Module_System) ready.
  * ISC license.
  * High-coverage automated test suite.

### Motivation

Test suites often contain integration tests that have dependencies on
external servers such as databases. Projects such as
[testcontainers](https://testcontainers.com/) provide a heavyweight and
complex API for starting Docker containers in tests, but no simple, lightweight
alternative exists for [Podman](https://podman.io).

The `ervilla` package provides a tiny API and a [JUnit 5](https://junit.org/junit5/)
extension for succinctly and efficiently creating (and automatically destroying)
Podman containers during tests.

### Building

```
$ mvn clean verify
```

### Usage

Annotate your test suite with `@ExtendWith(ErvillaExtension.class)`. This
will allow tests to get access to an injected `EContainerSupervisorType`
instance that can be used to create containers.

Additionally, it can be useful to have tests simply disable themselves rather
than running and failing if they are executed on a system that doesn't have
a `podman` executable. Both the name of the `podman` executable and a flag
that disables tests if `podman` isn't supported can be specified in an
`@ErvillaConfiguration` annotation placed on the test class.

An example test from the test suite:

```
@ExtendWith(ErvillaExtension.class)
@ErvillaConfiguration(
  podmanExecutable = "podman",
  disabledIfUnsupported = true
)
public final class ErvillaExtensionContainerPerTest
{
  private EContainerType container;

  @BeforeEach
  public void setup(
    final EContainerSupervisorType supervisor)
    throws IOException
  {
    this.container =
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
    assertTrue(this.container.name().startsWith("ERVILLA-"));
  }

  @Test
  public void test1()
  {
    assertTrue(this.container.name().startsWith("ERVILLA-"));
  }

  @Test
  public void test2()
  {
    assertTrue(this.container.name().startsWith("ERVILLA-"));
  }
}
```

All of the containers created in each invocation of the `@BeforeEach`
method are destroyed automatically after each test completes. In some
cases, it may be desirable to start up a container once and use the
same instance in all tests. This can be achieved by defining a `@BeforeAll`
method and annotating the injected supervisor parameter with the
`@ErvillaCloseAfterAll` annotation:

```
@ExtendWith(ErvillaExtension.class)
@ErvillaConfiguration(disabledIfUnsupported = true)
public final class ErvillaExtensionCloseAfterAllTest
{
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

  }

  @Test
  public void test1()
  {

  }

  @Test
  public void test2()
  {

  }
}
```

The container is created once in the `@BeforeAll` method and assigned to
a static field `CONTAINER`. Each of `test0()`, `test1()`, `test2()` can
use this container instance, and the instance itself will be destroyed when
the test class completes.

