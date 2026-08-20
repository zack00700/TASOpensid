---
name: quarkus-microservices
description: >
  Quarkus microservice architecture: service decomposition, resilience patterns, event-driven
  communication, service-to-service calls, observability, configuration management across
  environments, and deployment on Azure. Use this skill whenever the user is designing
  a new microservice, wiring services together, implementing resilience (retry, circuit breaker,
  timeout, fallback), setting up Kafka producers/consumers, designing inter-service contracts,
  discussing deployment strategy on Azure VMs or App Service, or asks about service boundaries,
  data ownership, or distributed system challenges in a Quarkus context.
---

# Quarkus Microservices — Architect Reference

## Core Philosophy
> *"Each service owns its data. Communicate via contracts. Expect failure. Observe everything."*

Microservices are a deployment and organizational pattern — not an excuse for distributed
spaghetti. Design for **autonomy**, **resilience**, and **eventual consistency**.

---

## 1. Service Decomposition Principles

For the **port concession platform**, boundaries follow operational domains:

```
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│  Manifest       │   │  Billing        │   │  Tax Cert       │
│  Service        │   │  Service        │   │  Service        │
│  (EDIFACT/CSV   │   │  (Invoices,     │   │  (FIRS/GRA/ETA/ │
│   ingestion)    │   │   contracts)    │   │   FNE)          │
└────────┬────────┘   └────────┬────────┘   └────────┬────────┘
         │  manifest.processed  │ invoice.created      │
         └──────────────────────┴──────────────────────┘
                         (Kafka / Azure Service Bus)
```

**Boundaries:**
- Each service has its **own database** — never share schemas across services
- Communication: **async events** for state changes, **sync REST** for queries
- Data needed from another service: either subscribe to its events, or expose a read API

---

## 2. MicroProfile REST Client — Sync Service Calls

```java
@RegisterRestClient(configKey = "billing-api")
@RegisterProvider(BearerTokenFilter.class)
@Path("/api/v1")
public interface BillingApiClient {

    @GET
    @Path("/invoices/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<InvoiceDto> getInvoice(@PathParam("id") String id);

    @POST
    @Path("/invoices")
    Uni<InvoiceDto> createInvoice(@Valid InvoiceCreateRequest request);
}
```

```properties
# application.properties
quarkus.rest-client.billing-api.url=http://billing-service:8080
quarkus.rest-client.billing-api.connect-timeout=3000
quarkus.rest-client.billing-api.read-timeout=10000
%prod.quarkus.rest-client.billing-api.url=${BILLING_SERVICE_URL}
```

---

## 3. Resilience — SmallRye Fault Tolerance

Add to every outbound service call. Never call external services without protection.

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-smallrye-fault-tolerance</artifactId>
</dependency>
```

```java
@ApplicationScoped
public class BillingIntegration {

    @Inject
    @RestClient
    BillingApiClient billingClient;

    @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
           retryOn = {WebApplicationException.class, ProcessingException.class},
           abortOn = {UnauthorizedException.class, BadRequestException.class})
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(
        requestVolumeThreshold = 10,
        failureRatio = 0.5,
        delay = 30,
        delayUnit = ChronoUnit.SECONDS,
        successThreshold = 3
    )
    @Fallback(fallbackMethod = "getInvoiceFallback")
    public Uni<InvoiceDto> getInvoice(String id) {
        return billingClient.getInvoice(id);
    }

    private Uni<InvoiceDto> getInvoiceFallback(String id) {
        LOG.warnf("Billing service unavailable, using cached data for invoice [id=%s]", id);
        return invoiceCache.get(id)
                .map(Uni::createFrom)
                .orElse(Uni.createFrom().failure(new ServiceUnavailableException("Billing")));
    }
}
```

---

## 4. Resilience Decision Matrix

| Scenario | Pattern | Config |
|---------|---------|--------|
| Transient network error | `@Retry` | 3 retries, exponential backoff |
| Slow downstream | `@Timeout` | Budget 80% of your own SLA |
| Downstream consistently failing | `@CircuitBreaker` | 50% failure rate, 30s open |
| Non-critical enrichment | `@Fallback` to cached/default | Return degraded response |
| Idempotent write (tax signing) | `@Retry` + idempotency key | 5 retries, jitter |

---

## 5. Event-Driven Communication — Kafka / Azure Service Bus

### Producer

```java
@ApplicationScoped
public class ManifestEventProducer {

    @Channel("manifest-processed")
    Emitter<ManifestProcessedEvent> emitter;

    public Uni<Void> publishManifestProcessed(Manifest manifest) {
        var event = new ManifestProcessedEvent(
                manifest.getId(),
                manifest.getBlNumber(),
                manifest.getTerminalCode(),
                manifest.getContainerCount(),
                Instant.now()
        );

        return Uni.createFrom().completionStage(
                emitter.send(Message.of(event)
                        .addMetadata(OutgoingKafkaRecordMetadata.builder()
                                .withKey(manifest.getTerminalCode())  // partition by terminal
                                .build())));
    }
}
```

### Consumer

```java
@ApplicationScoped
public class ManifestProcessedConsumer {

    @Inject
    BillingService billingService;

    @Incoming("manifest-processed")
    @Blocking  // billing is a DB operation — run off the event loop
    public Uni<Void> onManifestProcessed(ManifestProcessedEvent event) {
        LOG.infof("Received manifest event [bl=%s, terminal=%s]",
                event.blNumber(), event.terminalCode());

        return billingService.initiateBillingCycle(event)
                .onFailure().invoke(ex ->
                        LOG.errorf(ex, "Failed billing cycle for manifest [bl=%s]",
                                event.blNumber()));
    }
}
```

```properties
# Kafka config
mp.messaging.outgoing.manifest-processed.connector=smallrye-kafka
mp.messaging.outgoing.manifest-processed.topic=ipaki.manifest.processed
mp.messaging.outgoing.manifest-processed.value.serializer=io.quarkus.kafka.client.serialization.ObjectMapperSerializer

mp.messaging.incoming.manifest-processed.connector=smallrye-kafka
mp.messaging.incoming.manifest-processed.topic=ipaki.manifest.processed
mp.messaging.incoming.manifest-processed.group.id=billing-service
mp.messaging.incoming.manifest-processed.auto.offset.reset=earliest
mp.messaging.incoming.manifest-processed.failure-strategy=dead-letter-queue
mp.messaging.incoming.manifest-processed.dead-letter-queue.topic=ipaki.manifest.processed.dlq
```

---

## 6. Idempotency — Critical for Retry + Event Processing

```java
@ApplicationScoped
public class TaxSigningService {

    @Inject TaxSigningRepository repository;
    @Inject FirsApiClient firsClient;

    /**
     * Idempotent signing — safe to call multiple times with same invoiceId.
     * Required because FIRS API is sometimes slow and we retry aggressively.
     */
    public Uni<SigningResult> signInvoice(String invoiceId, SigningRequest request) {
        // Check if already signed — return cached result
        return repository.findByInvoiceId(invoiceId)
                .flatMap(existing -> {
                    if (existing != null && existing.isSigned()) {
                        LOG.infof("Invoice [id=%s] already signed, returning cached result", invoiceId);
                        return Uni.createFrom().item(existing.toResult());
                    }
                    return doSignWithFirs(invoiceId, request);
                });
    }
}
```

---

## 7. Observability — Health, Metrics, Tracing

```java
// Health — per external dependency
@ApplicationScoped
@Liveness
@Readiness
public class ExternalDependenciesHealthCheck implements HealthCheck {

    @Inject SftpHealthProbe sftp;
    @Inject DatabaseHealthProbe database;
    @Inject KafkaHealthProbe kafka;

    @Override
    public HealthCheckResponse call() {
        boolean sftpOk = sftp.isAvailable();
        boolean dbOk = database.isAvailable();
        boolean kafkaOk = kafka.isAvailable();

        return HealthCheckResponse.named("external-dependencies")
                .status(sftpOk && dbOk && kafkaOk)
                .withData("sftp", sftpOk ? "UP" : "DOWN")
                .withData("database", dbOk ? "UP" : "DOWN")
                .withData("kafka", kafkaOk ? "UP" : "DOWN")
                .build();
    }
}
```

```properties
# Tracing — OpenTelemetry
quarkus.otel.exporter.otlp.traces.endpoint=http://otel-collector:4317
quarkus.otel.service.name=manifest-service
quarkus.otel.resource.attributes=deployment.environment=prod,service.version=1.2.0
```

---

## 8. Configuration per Environment (Azure)

```properties
# application.properties — profiles for each deployment context

# --- Development ---
%dev.quarkus.datasource.jdbc.url=jdbc:sqlserver://localhost:1433;databaseName=ipaki_dev
%dev.ipaki.sftp.host=localhost

# --- Test ---
%test.quarkus.datasource.db-kind=h2
%test.ipaki.sftp.host=mock-sftp

# --- Staging (Azure) ---
%staging.quarkus.datasource.jdbc.url=${AZURE_SQL_URL}
%staging.ipaki.sftp.host=${SFTP_HOST_STAGING}

# --- Production (Azure) ---
%prod.quarkus.datasource.jdbc.url=${AZURE_SQL_URL}
%prod.ipaki.firs.api.url=https://api.firs.gov.ng/
%prod.quarkus.log.level=WARN
%prod.quarkus.log.category."com.ipaki".level=INFO
```

Secrets injected via Azure Key Vault — never hardcoded:

```properties
%prod.quarkus.azure.keyvault.secret.endpoint=https://ipaki-kv.vault.azure.net/
# Reference Key Vault secrets as: ${kv//secret-name}
%prod.quarkus.datasource.password=${kv//ipaki-db-password}
```

---

## 9. Service Deployment Patterns (Azure + WinSW)

Each service deployed as a **Windows Service** via WinSW on Azure VMs:

```xml
<!-- manifest-service.xml -->
<service>
  <id>ipaki-manifest-service</id>
  <name>iPaki Manifest Service</name>
  <description>Quarkus service for EDIFACT/CSV manifest processing</description>
  <executable>java</executable>
  <arguments>
    -Xms256m -Xmx512m
    -Dquarkus.profile=prod
    -Dquarkus.log.file.enable=true
    -Dquarkus.log.file.path=logs/manifest-service.log
    -jar manifest-service-runner.jar
  </arguments>
  <log mode="roll-by-size">
    <sizeThreshold>10240</sizeThreshold>
    <keepFiles>5</keepFiles>
  </log>
  <onfailure action="restart" delay="10 sec"/>
</service>
```

---

## 10. Anti-Patterns — Distributed Systems

| Anti-pattern | Problem | Fix |
|-------------|---------|-----|
| Synchronous chain of 3+ services | Cascading failure, high latency | Break with async events |
| Shared database between services | Tight coupling, schema lock-in | Each service owns its schema |
| Calling services without fault tolerance | Single downstream failure kills everything | Always `@Retry` + `@CircuitBreaker` |
| Chatty fine-grained API calls in a loop | N+1 calls, performance collapse | Batch API endpoints |
| Synchronous writes to slow external API | Blocks until API responds | Queue the request, confirm async |
| No dead-letter queue on Kafka consumer | Silent event loss on failure | Always configure DLQ |
| Secrets in `application.properties` | Credentials in source code | Azure Key Vault references |

---

## 11. Checklist — New Microservice

- [ ] Service has its own database/schema — no shared data stores
- [ ] All outbound calls have `@Retry` + `@Timeout` + `@CircuitBreaker`
- [ ] Kafka consumers configured with DLQ (`failure-strategy=dead-letter-queue`)
- [ ] All state-changing operations are idempotent
- [ ] Health checks for all external dependencies (`/q/health`)
- [ ] OpenTelemetry tracing configured with service name and environment
- [ ] Configuration per environment via `%prod`/`%staging` profiles
- [ ] Secrets sourced from Azure Key Vault — never hardcoded
- [ ] WinSW descriptor includes restart-on-failure policy
- [ ] Contract tests (Pact) for all consumed APIs

---

## See also
- `quarkus-api` — REST endpoint design within each service
- `quarkus-no-regression` — contract testing between services
- `quarkus-code-best-practices` — within-service code quality
- `quarkus-agents` — concevoir un nouveau microservice via agents exploratoire + spécialisés (Workflow D)
