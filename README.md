# Software Operations API

Backend project intended to manage software operational work, maintenance
activities, and controlled lifecycle transitions. Currently, it contains a
minimal Java/Maven foundation with an entry point that prints the project name.

## Prerequisites

- Java 25 LTS. Tested with Eclipse Temurin 25.0.4.1.  
- On Linux, a POSIX shell, `curl` or `wget`, and `unzip` or `tar`.
- Network access for the initial Maven and build-plugin downloads.

Set `JAVA_HOME` to your Temurin JDK directory and put its `bin` directory first
on `PATH` in the current shell. Before building, check:

```bash
java -version
javac -version
./mvnw --version
```

The Maven version output should identify Maven 3.9.16 and Temurin Java 25.0.4.1.
The included Apache Maven Wrapper 3.3.4 downloads the configured Maven version;
a global Maven installation is unnecessary.

## Build

From the repository root:

```bash
./mvnw test
./mvnw package
```

For builds starting without previous generated output:

```bash
./mvnw clean test
./mvnw clean package
```

`test` compiles sources and runs the JUnit unit-test suite. The current tests
protect pure-Java domain construction, the WorkOrder lifecycle and invariants,
blocking, terminal states, deployment requirements, and progressive
immutability. `package` also creates the project JAR under `target/`.

## Run

```bash
./mvnw compile
java -cp target/classes io.github.mrav7.softwareoperationsapi.Application
```

Expected output:

```text
Software Operations API
```

## Source layout

- `src/main/java/` contains application source code.
- `src/test/java/` contains the JUnit domain unit tests.
- `target/` contains generated build output and is ignored by Git.
