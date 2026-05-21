# tosbe

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/tosbe-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)

## MongoDB Transactions

MongoDB transactions require a replica set. Configure your MongoDB instance as a replica set if you need transactional integrity.

## Ask AI endpoint

The OpenAI API key is supplied via the `OPENAI_API_KEY` environment variable. `application.properties` reads it using `openai.api.key=${OPENAI_API_KEY}`. Ensure this variable is set in your environment or configured as a secret when deploying.

The model used for OpenAI requests can be configured with the `openai.api.model` property in `application.properties` (default `gpt-4o`).

Set the following environment variables before running:

```
export OPENAI_API_KEY=<your key>
export QUARKUS_MONGODB_CONNECTION_STRING="mongodb://localhost:27017"
export QUARKUS_MONGODB_DATABASE="app"
```

Run the application:

```
./mvnw quarkus:dev
```

Example request:

```
curl -X POST http://localhost:8080/api/ask-ai \
  -H "Content-Type: application/json" \
  -d '{"question":"How many invoices this month?"}'
```

### KPI field selection

When a KPI chart is requested, the service uses the first numeric field of the
aggregation result as the metric to display. Only this field is summed across
all documents; additional numeric fields are ignored. Ensure the aggregation
pipeline places the desired metric as the first numeric field in its output.
