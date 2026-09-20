# Software Operations API

Java/Spring Boot backend for software operational work and controlled lifecycle
transitions. The current HTTP API registers and retrieves software components
and creates and retrieves work orders using the existing Java domain.

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

`test` compiles sources and runs the domain and Spring HTTP-boundary tests. The domain tests
protect pure-Java domain construction, the WorkOrder lifecycle and invariants,
blocking, terminal states, deployment requirements, and progressive
immutability. Web tests verify registration, creation, retrieval, and domain
integration. `package` also creates an executable Spring Boot JAR under `target/`.

## Run

```bash
./mvnw spring-boot:run
```

Spring Boot 4.1.1 starts an HTTP server on port **8080** by default.
Alternatively, after `./mvnw package`:

```bash
java -jar target/software-operations-api-0.1.0-SNAPSHOT.jar
```

## HTTP basics

Register a component:

```bash
curl -i -H 'Content-Type: application/json' \
  -d '{"name":"configuration-service","description":"Configuration API"}' \
  http://localhost:8080/api/components
```

Creation returns `201 Created`, a JSON representation, and a `Location` header.
Use the returned component ID to retrieve it or create a work order:

```bash
curl http://localhost:8080/api/components/COMPONENT_ID

curl -i -H 'Content-Type: application/json' \
  -d '{"componentId":"COMPONENT_ID","title":"Deploy release","type":"DEPLOYMENT","priority":"HIGH","targetVersion":"2.4.1"}' \
  http://localhost:8080/api/work-orders

curl http://localhost:8080/api/work-orders/WORK_ORDER_ID
```

Replace the ID placeholders with UUIDs returned by the API. Retrieval returns
`200 OK`; new work orders start in `CREATED`.

Data is temporary, process-local, and lost on restart. Only these four endpoints
are available. Component-name uniqueness is not yet enforced. Missing resources
return an empty `404`; input validation and error responses are not yet a stable
contract. Existing domain invariants still apply.

## Source layout

- `src/main/java/` contains application source code.
- `src/test/java/` contains domain unit tests and Spring HTTP-boundary tests.
- `target/` contains generated build output and is ignored by Git.
