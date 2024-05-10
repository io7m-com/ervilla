ervilla
===

[![Maven Central](https://img.shields.io/maven-central/v/com.io7m.ervilla/com.io7m.ervilla.svg?style=flat-square)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.io7m.ervilla%22)
[![Maven Central (snapshot)](https://img.shields.io/nexus/s/com.io7m.ervilla/com.io7m.ervilla?server=https%3A%2F%2Fs01.oss.sonatype.org&style=flat-square)](https://s01.oss.sonatype.org/content/repositories/snapshots/com/io7m/ervilla/)
[![Codecov](https://img.shields.io/codecov/c/github/io7m-com/ervilla.svg?style=flat-square)](https://codecov.io/gh/io7m-com/ervilla)
![Java Version](https://img.shields.io/badge/21-java?label=java&color=e6c35c)

![com.io7m.ervilla](./src/site/resources/ervilla.jpg?raw=true)

| JVM | Platform | Status |
|-----|----------|--------|
| OpenJDK (Temurin) Current | Linux | [![Build (OpenJDK (Temurin) Current, Linux)](https://img.shields.io/github/actions/workflow/status/io7m-com/ervilla/main.linux.temurin.current.yml)](https://www.github.com/io7m-com/ervilla/actions?query=workflow%3Amain.linux.temurin.current)|
| OpenJDK (Temurin) LTS | Linux | [![Build (OpenJDK (Temurin) LTS, Linux)](https://img.shields.io/github/actions/workflow/status/io7m-com/ervilla/main.linux.temurin.lts.yml)](https://www.github.com/io7m-com/ervilla/actions?query=workflow%3Amain.linux.temurin.lts)|
| OpenJDK (Temurin) Current | Windows | [![Build (OpenJDK (Temurin) Current, Windows)](https://img.shields.io/github/actions/workflow/status/io7m-com/ervilla/main.windows.temurin.current.yml)](https://www.github.com/io7m-com/ervilla/actions?query=workflow%3Amain.windows.temurin.current)|
| OpenJDK (Temurin) LTS | Windows | [![Build (OpenJDK (Temurin) LTS, Windows)](https://img.shields.io/github/actions/workflow/status/io7m-com/ervilla/main.windows.temurin.lts.yml)](https://www.github.com/io7m-com/ervilla/actions?query=workflow%3Amain.windows.temurin.lts)|

## ervilla

A minimalist JUnit 5 extension to create and destroy Podman containers
during tests.

### Features

  * Conveniently and reliably start containers using `podman` in test suites.
  * Programmable readiness checks for checking if a container is really ready for use.
  * Written in pure Java 21.
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

#### Container Database

`ervilla` maintains a persistent store of the containers and pods that it
has created. Each time the `ervilla` package is started, it will attempt to
clean up any pods and/or containers that have inadvertently managed to survive
the previous test run. The `ervilla` package expects each test suite to
provide a _project name_; A test run in a given project will _only_ attempt
to clean up containers/pods that are labelled with the same project name.

This is important with regard to concurrency: `ervilla` is designed to be
used in sets of projects that may be built in parallel on the same physical
machine during continuous integration runs. By keeping containers strictly
separated by project name, the package avoids accidentally trying to clean up
the containers that may be created by the _other_ project's test suite running
in parallel with the current project.

#### Disable Support

It can be useful to have tests simply disable themselves rather than running
and failing if they are executed on a system that doesn't have a `podman`
executable. Both the name of the `podman` executable and a flag
that disables tests if `podman` isn't supported can be specified in an
`@ErvillaConfiguration` annotation placed on the test class.

An example test from the test suite:

```
@ExtendWith(ErvillaExtension.class)
@ErvillaConfiguration(
  projectName = "com.io7m.example"
  podmanExecutable = "podman",
  disabledIfUnsupported = true
)
public final class ErvillaExtensionContainerPerTest
{
  private EContainerType container;

  @BeforeEach
  public void setup(
    final EContainerSupervisorType supervisor)
    throws Exception
  {
    this.container =
      supervisor.start(
        EContainerSpec.builder(
            "quay.io",
            "io7mcom/idstore",
            "1.1.0"
          )
          .setImageHash(
            "sha256:e77ad1f7f606a42a6bb0bbc885030a647fa4b90c15d47d5b32f43f1c98475f6e")
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

#### Container Scope

An injected _supervisor_ instance has one of the following scope values:

  * `PER_SUITE`
  * `PER_CLASS`
  * `PER_TEST`

Containers created from a supervisor with `PER_SUITE` scope will be destroyed
at the end of the entire test suite run.

Containers created from a supervisor with `PER_CLASS` scope will be destroyed
at the end of the containing test class execution.

Containers created from a supervisor with `PER_TEST` scope will be destroyed
at the end of each test method.

To get a `PER_SUITE` scoped supervisor, annotate the injected supervisor
parameter with `@ErvillaCloseAfterSuite`.

To get a `PER_CLASS` scoped supervisor, annotate the injected supervisor
parameter with `@ErvillaCloseAfterClass`.

To get a `PER_TEST` scoped supervisor, do not annotate the injected
supervisor.


```
@ExtendWith(ErvillaExtension.class)
@ErvillaConfiguration(disabledIfUnsupported = true)
public final class ErvillaExtensionCloseAfterAllTest
{
  private static EContainerType CONTAINER;

  @BeforeAll
  public static void beforeAll(
    final @ErvillaCloseAfterClass EContainerSupervisorType supervisor)
    throws Exception
  {
    CONTAINER =
      supervisor.start(
        EContainerSpec.builder(
            "quay.io",
            "io7mcom/idstore",
            "1.1.0"
          )
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

#### Container Reuse

It is common, in test suites, to instantiate heavyweight containers such
as databases just once and then reuse those containers throughout the entire
test suite. The reuse of a container (with appropriate code to drop and
recreate a database each time) can turn a ten minute test suite execution into
a one minute execution.

The following is a relatively safe way to achieve this:

```
class TestServices {
  private static EContainerType DATABASE;

  public static void resetDatabase(
    final EContainerType container)
  {
    // Database-specific code here, to drop and recreate databases...
    ...
    ...
  }

  public static EContainerType createDatabase(
    final EContainerSupervisorType supervisor)
  {
    // Database-specific code here, to create database containers...
    ...
    ...
  }

  public static EContainerType database(
    final EContainerSupervisorType supervisor)
  {
    if (DATABASE == null) {
      DATABASE = createDatabase(supervisor);
    }
    return DATABASE;
  }
}

@ExtendWith({ErvillaExtension.class})
@ErvillaConfiguration(projectName = "com.io7m.example", disabledIfUnsupported = true)
class Test0 {
  private static EContainerType DATABASE;

  @BeforeAll
  public static void setupOnce(
    final @ErvillaCloseAfterSuite EContainerSupervisorType containers)
    throws Exception
  {
    DATABASE = TestServices.database(supervisor);
    TestServices.resetDatabase(DATABASE);
  }

  @Test
  public void test0()
  {
    // Use database here
  }

  @Test
  public void test1()
  {
    // Use database here
  }

  @Test
  public void test2()
  {
    // Use database here
  }
}

@ExtendWith({ErvillaExtension.class})
@ErvillaConfiguration(projectName = "com.io7m.example", disabledIfUnsupported = true)
class Test1 {
  private static EContainerType DATABASE;

  @BeforeAll
  public static void setupOnce(
    final @ErvillaCloseAfterSuite EContainerSupervisorType containers)
    throws Exception
  {
    DATABASE = TestServices.database(supervisor);
    TestServices.resetDatabase(DATABASE);
  }

  @Test
  public void test0()
  {
    // Use database here
  }

  @Test
  public void test1()
  {
    // Use database here
  }

  @Test
  public void test2()
  {
    // Use database here
  }
}
```

Assuming that `resetDatabase` can drop and recreate the container's
database, and `createDatabase` does the initial creation of the
database container, the above `Test1` and `Test0` classes will reuse
the exact same database container instance.

#### Readiness Checks

It is possible to provide implementations of the `EReadyCheckType`
interface for each container. A readiness check is a piece of code that
is executed in a loop until it returns a positive response, and is responsible
for performing some kind of image-specific check to determine if a container
is actually ready for use.

By default, with no readiness checks provided for a container, the
system will only consider a container to be ready for use when the
underlying `podman` executable says that the container is in state `Up`.
However, for some images, the container being in the `Up` state doesn't
necessarily mean that the container is actually ready for use, and this
is where readiness checks should be used.

A good example of this is the images for [PostgreSQL](https://www.postgresql.org).
The PostgreSQL server takes a few seconds to start up while it loads various
bits of configuration and database state from the disk. This means that,
despite the container being in the `Up` state, the database won't actually be
ready to service database requests for at least a few seconds after startup.

The `EPgReadyCheck` class is a PostgreSQL-specific
readiness check that repeatedly tries to open a JDBC connection to a created
PostgreSQL container, and the system won't consider the container to be
ready for use until a JDBC connection succeeds.

Other readiness checks exist, such as checks to attempt to connect to
a bound TCP/IP socket on the container.

