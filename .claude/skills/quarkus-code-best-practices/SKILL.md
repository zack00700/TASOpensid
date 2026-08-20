---
name: quarkus-code-best-practices
description: >
  Quarkus coding standards, architecture patterns, CDI best practices, error handling,
  logging, configuration management, and code quality rules for a Quarkus microservice.
  Use this skill whenever the user asks how to write better Quarkus code, how to structure
  a service class, how to handle configuration, how to log properly, asks about CDI scope,
  reviews code for quality, or asks "is this the right way to do X in Quarkus". Trigger for
  topics like @ApplicationScoped, @Inject, constructor injection, immutability, MicroProfile Config,
  @ConfigMapping, exception design, structured logging, or code review best practices.
---

# Quarkus Code Best Practices — Architect Reference

## Core Philosophy
> *"Explicit over implicit. Immutable over mutable. Fail fast, log well, recover gracefully."*

Code is read far more than it is written. Optimize for **clarity**, **correctness**, and
**observability** — not for cleverness.

---

## 1. Project Layer Structure

```
src/main/java/com/ipaki/
├── api/                    # REST resources, DTOs, exception mappers
│   ├── ManifestResource.java
│   ├── dto/
│   └── mapper/
├── service/                # Business logic, orchestration
│   └── ManifestService.java
├── domain/                 # Domain model, value objects, domain events
│   ├── Manifest.java
│   └── ContainerStatus.java
├── repository/             # Data access (Panache repos ou plain JPA)
│   └── ManifestRepository.java
├── infrastructure/         # External adapters (SFTP, REST clients, MQ)
│   ├── sftp/
│   ├── edifact/
│   └── firs/
└── config/                 # @ConfigMapping classes, CDI producers
    └── AppConfig.java
```

**Dependency rule**: `api → service → domain ← repository`, `service → infrastructure`
Never let `domain` import from `api` or `infrastructure`.

---

## 2. Principes SOLID — Application Concrète en Quarkus

Les principes SOLID ne sont pas des suggestions académiques — ils sont le **seul rempart efficace contre la rigidité du code** et la dette technique exponentielle.

### S — Single Responsibility Principle

```java
// MAUVAIS — ManifestService fait tout : parsing, persistance, notification, facturation
@ApplicationScoped
public class ManifestService {
    public void processManifest(String edifact) {
        var manifest = parseEdifact(edifact);        // parsing
        repository.persist(manifest);                // persistance
        billingService.createInvoice(manifest);      // facturation
        notificationService.sendAlert(manifest);     // notification
    }
}

// BON — chaque classe a une seule raison de changer
@ApplicationScoped
public class EdifactParser {          // seule responsabilité : parser l'EDIFACT
    public Manifest parse(String edifact) { ... }
}

@ApplicationScoped
public class ManifestService {        // orchestre sans implémenter les détails
    public Uni<ManifestDto> process(String edifact) {
        return Uni.createFrom().item(() -> edifactParser.parse(edifact))
                .flatMap(repository::persist)
                .invoke(m -> eventProducer.publishManifestProcessed(m))
                .map(mapper::toDto);
    }
}
```

### O — Open/Closed Principle

```java
// Fermé à la modification, ouvert à l'extension via stratégie
public interface TaxCertificationStrategy {
    boolean supports(String countryCode);
    Uni<CertificationResult> certify(Invoice invoice);
}

@ApplicationScoped
public class FirsCertification implements TaxCertificationStrategy {
    @Override public boolean supports(String c) { return "NG".equals(c); }
    @Override public Uni<CertificationResult> certify(Invoice invoice) { ... }
}

@ApplicationScoped
public class GraCertification implements TaxCertificationStrategy {
    @Override public boolean supports(String c) { return "GH".equals(c); }
    @Override public Uni<CertificationResult> certify(Invoice invoice) { ... }
}

// Ajouter un nouveau pays = nouvelle classe, ZÉRO modification de l'existant
@ApplicationScoped
public class TaxCertificationRouter {
    @Inject @All List<TaxCertificationStrategy> strategies;

    public Uni<CertificationResult> certify(Invoice invoice) {
        return strategies.stream()
                .filter(s -> s.supports(invoice.countryCode()))
                .findFirst()
                .orElseThrow(() -> new UnsupportedCountryException(invoice.countryCode()))
                .certify(invoice);
    }
}
```

### L — Liskov Substitution Principle

```java
// Toute implémentation de SftpConnector doit être substituable
public interface SftpConnector {
    Uni<List<String>> listFiles(String path);
    Uni<byte[]> download(String path);
    Uni<Void> archive(String path, String archivePath);
}

// JschSftpConnector, MockSftpConnector, AzureBlobSftpAdapter
// peuvent tous remplacer SftpConnector sans briser le comportement attendu
```

### I — Interface Segregation Principle

```java
// MAUVAIS — interface trop large, force les implémentations partielles
public interface ManifestRepository {
    Uni<Manifest> findById(String id);
    Uni<Void> persist(Manifest m);
    Uni<Void> delete(String id);
    Uni<List<Manifest>> findByTerminal(String terminal);
    Uni<Statistics> computeStatistics(DateRange range);  // pourquoi dans le repo ?
    Uni<Void> exportToCsv(String path);                  // pourquoi dans le repo ?
}

// BON — interfaces ségrégées par usage
public interface ManifestReader {
    Uni<Manifest> findById(String id);
    Uni<List<Manifest>> findByTerminal(String terminal);
}
public interface ManifestWriter {
    Uni<Void> persist(Manifest m);
    Uni<Void> delete(String id);
}
public interface ManifestStatistics {
    Uni<Statistics> computeStatistics(DateRange range);
}
```

### D — Dependency Inversion Principle

```java
// Les classes de haut niveau (service) ne dépendent pas des détails (JDBC, SFTP)
// Elles dépendent d'abstractions

@ApplicationScoped
public class ManifestService {
    // Dépend de l'interface, pas de l'implémentation
    private final ManifestReader reader;
    private final ManifestWriter writer;
    private final SftpConnector sftp;  // interface, pas JschSftpConnector

    @Inject
    public ManifestService(ManifestReader reader, ManifestWriter writer, SftpConnector sftp) {
        this.reader = reader;
        this.writer = writer;
        this.sftp = sftp;
    }
}
```

---

---

## 3. CDI Scopes — Use the Right One

| Scope | Annotation | When to use |
|-------|-----------|-------------|
| Application singleton | `@ApplicationScoped` | Services, repositories, infrastructure adapters — **default choice** |
| Request-scoped | `@RequestScoped` | When state must be per-HTTP-request (rare) |
| Singleton | `@Singleton` | Config classes, heavy-init objects — slightly faster than ApplicationScoped |
| Dependent | `@Dependent` | Default when no annotation — creates a new instance per injection point |

```java
// CORRECT — stateless service, application-scoped singleton
@ApplicationScoped
public class ManifestService {
    // No mutable instance state — all state goes in method args or DB
}

// WRONG — never use @RequestScoped on a service called from async/reactive context
@RequestScoped  // ❌ breaks with Mutiny — context propagation issues
public class ManifestService { }
```

---

## 4. Constructor Injection (Preferred over Field Injection)

```java
// PREFERRED — constructor injection
// Testable without CDI, enforces required dependencies, immutable
@ApplicationScoped
public class ManifestService {

    private final ManifestRepository repository;
    private final CommodityMatcher commodityMatcher;
    private final AppConfig config;

    @Inject  // optional in Quarkus if only one constructor
    public ManifestService(ManifestRepository repository,
                           CommodityMatcher commodityMatcher,
                           AppConfig config) {
        this.repository = repository;
        this.commodityMatcher = commodityMatcher;
        this.config = config;
    }
}

// ACCEPTABLE for simple cases — field injection
@ApplicationScoped
public class ManifestService {
    @Inject ManifestRepository repository;
}

// NEVER — static injection or raw `new` for managed beans
ManifestRepository repo = new ManifestRepositoryImpl(); // ❌
```

---

## 5. Configuration — `@ConfigMapping`

Never use `@ConfigProperty` scattered across classes. Group configuration by concern.

```java
@ConfigMapping(prefix = "ipaki.sftp")
@ApplicationScoped
public interface SftpConfig {

    String host();
    int port();
    String username();

    @WithName("private-key-path")
    String privateKeyPath();

    @WithDefault("30")
    int timeoutSeconds();

    @WithDefault("3")
    int maxRetries();

    // Nested config groups
    Map<String, TerminalConfig> terminals();

    interface TerminalConfig {
        String inboxPath();
        String archivePath();
        String errorPath();
    }
}
```

```properties
# application.properties
ipaki.sftp.host=sftp.terminal.com
ipaki.sftp.port=22
ipaki.sftp.username=ipaki_svc
ipaki.sftp.private-key-path=/opt/keys/sftp_rsa
ipaki.sftp.terminals.APM.inbox-path=/inbox/apm
ipaki.sftp.terminals.APM.archive-path=/archive/apm

# Per-environment overrides
%prod.ipaki.sftp.host=sftp-prod.terminal.com
%test.ipaki.sftp.host=localhost
```

---

## 6. Error Handling — Exception Design

```java
// Domain exception hierarchy — checked vs unchecked
public abstract class DomainException extends RuntimeException {
    private final String errorCode;

    protected DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

// Specific typed exceptions
public class ManifestNotFoundException extends DomainException {
    public ManifestNotFoundException(String blNumber) {
        super("MANIFEST_NOT_FOUND", "Manifest not found: " + blNumber);
    }
}

public class DuplicateBlNumberException extends DomainException {
    public DuplicateBlNumberException(String blNumber) {
        super("DUPLICATE_BL", "BL number already exists: " + blNumber);
    }
}

// Usage in service — explicit, typed, no magic strings
public Uni<ManifestDto> findByBlNumber(String blNumber) {
    return repository.findByBlNumber(blNumber)
            .onItem().ifNull()
            .failWith(() -> new ManifestNotFoundException(blNumber));
}
```

**Rules:**
- Never `throw new RuntimeException("something went wrong")` — always typed exceptions
- Never swallow exceptions silently (`catch (Exception e) { }`)
- Always log the exception at the point where it's **handled**, not where it's thrown

---

## 7. Logging — Structured and Contextual

```java
@ApplicationScoped
public class ManifestService {

    private static final Logger LOG = Logger.getLogger(ManifestService.class);

    public Uni<ManifestDto> processManifest(String blNumber, String terminal) {
        LOG.infof("Processing manifest [bl=%s, terminal=%s]", blNumber, terminal);

        return repository.findByBlNumber(blNumber)
                .invoke(manifest -> LOG.debugf(
                        "Found manifest [bl=%s, containers=%d, status=%s]",
                        blNumber, manifest.getContainerCount(), manifest.getStatus()))
                .onFailure().invoke(ex ->
                        LOG.errorf(ex, "Failed to process manifest [bl=%s]: %s",
                                blNumber, ex.getMessage()))
                .map(this::toDto);
    }
}
```

**Rules:**
- Use `Logger.getLogger(ClassName.class)` — never `LoggerFactory` unless using SLF4J explicitly
- Always include **context** in log messages: resource ID, terminal, operation
- `INFO`: business events (manifest received, processed, error sent)
- `DEBUG`: internal flow details (query results, intermediate states)
- `WARN`: recoverable issues (retry attempted, fallback used)
- `ERROR`: unrecoverable failures with full exception
- Never log **sensitive data**: BL numbers are OK, personal data and credentials are not

---

## 8. Reactive Patterns — Uni/Multi Best Practices

```java
// Chain transformations — never block
public Uni<ManifestDto> processAndNotify(String blNumber) {
    return repository.findByBlNumber(blNumber)           // Uni<Manifest>
            .onItem().ifNull()
            .failWith(() -> new ManifestNotFoundException(blNumber))
            .flatMap(manifest -> enrichWithCommodity(manifest))  // async enrich
            .map(this::toDto)                                     // sync transform
            .invoke(dto -> LOG.infof("Processed [bl=%s]", dto.blNumber()))
            .onFailure(ManifestNotFoundException.class)
            .recoverWithNull();  // return null instead of 404 for bulk ops
}

// NEVER block in a reactive pipeline
public Uni<ManifestDto> bad(String id) {
    var manifest = repository.findById(id).await().indefinitely(); // ❌ BLOCKS the event loop
    return Uni.createFrom().item(toDto(manifest));
}

// CORRECT — stay in the reactive chain
public Uni<ManifestDto> good(String id) {
    return repository.findById(id).map(this::toDto);  // ✅ Non-blocking
}
```

---

## 9. Immutability Rules

```java
// GOOD — immutable domain object
@Entity
public class Manifest {
    @Id
    private final String id;
    private final String blNumber;
    private ManifestStatus status;  // status is the only mutable part

    // No public setters except for controlled state transitions
    public Manifest activate() {
        if (this.status != ManifestStatus.DRAFT) {
            throw new IllegalStateException("Can only activate DRAFT manifests");
        }
        return new Manifest(this.id, this.blNumber, ManifestStatus.ACTIVE);
    }
}

// GOOD — immutable DTO using Java record
public record ManifestDto(String id, String blNumber, ManifestStatus status) {}

// BAD — mutable DTO with setters
public class ManifestDto {    // ❌ avoidable
    private String id;
    public void setId(String id) { this.id = id; }
}
```

---

## 10. Quarkus-Specific Conventions

```java
// Health checks — always implement for production services
@ApplicationScoped
public class SftpHealthCheck implements HealthCheck {

    @Inject SftpConfig config;

    @Override
    @Liveness
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("sftp-connectivity")
                .status(checkSftpConnectivity())
                .withData("host", config.host())
                .build();
    }
}

// Metrics — use MicroProfile Metrics for business KPIs
@ApplicationScoped
public class ManifestService {

    @Inject
    MeterRegistry registry;

    private final Counter manifestsProcessed;

    public ManifestService(MeterRegistry registry) {
        this.registry = registry;
        this.manifestsProcessed = registry.counter("manifests.processed",
                "service", "manifest");
    }

    public Uni<ManifestDto> processManifest(String blNumber, String terminal) {
        return doProcess(blNumber, terminal)
                .invoke(dto -> manifestsProcessed.increment());
    }
}
```

---

## 11. Code Review Checklist

- [ ] No `new` instantiation of managed beans — all injected via CDI
- [ ] Configuration via `@ConfigMapping` — no `@ConfigProperty` scattered in services
- [ ] Typed domain exceptions — no raw `RuntimeException` or magic strings
- [ ] Logging includes context (IDs, terminal, status) — no generic "error occurred"
- [ ] No blocking calls inside Uni/Multi chains
- [ ] DTOs are immutable records — no public setters
- [ ] Services are stateless (`@ApplicationScoped`) — no mutable instance fields
- [ ] Constructor injection for services with ≥ 2 dependencies
- [ ] Health checks implemented for all external dependencies
- [ ] No business logic in resource/controller classes — delegated to service

---

## See also
- `quarkus-api` — REST resource coding patterns
- `quarkus-validation` — input validation patterns
- `quarkus-microservices` — cross-service design patterns
