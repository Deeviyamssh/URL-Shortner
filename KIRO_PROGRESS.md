# KIRO Progress Log

## FEAT-001 — Backend Defect Fixes
**Status:** COMPLETED  
**Commit:** fix: FEAT-001 backend defects — deps, schema, security, analytics, config

### Files Changed

| File | Change |
|------|--------|
| `pom.xml` | Replaced `springdoc-openapi-ui:1.8.0` with `springdoc-openapi-starter-webmvc-ui:2.5.0`; removed non-existent `testcontainers:redis`; added `commons-lang3:3.14.0` compile dep; removed invalid `flyway-database-postgresql` (not in Boot 3.2.5 BOM — Flyway 9.x bundles PostgreSQL in flyway-core) |
| `src/main/java/com/example/urlshortener/repository/CounterRepository.java` | **DELETED** — was stub file containing only a comment |
| `src/main/resources/db/migration/V3__relax_click_event_nulls.sql` | **CREATED** — drops NOT NULL from ip_address, user_agent, referrer; makes event_id optional; adds partial unique index on event_id |
| `entity/ClickEvent.java` | Added `eventId UUID` field with `@Column(name="event_id", unique=true)`, getter/setter |
| `dto/ClickEventDto.java` | Added `eventId UUID` field with getter/setter |
| `service/UrlService.java` | `recordClick()` sets `eventId(UUID.randomUUID())` before publish; `mapToUrlDetailsResponse()`, `getUrlDetails()`, `updateUrlMapping()` all set `clickCount`; `getGlobalClickEvents()` now takes `owner` as first param and scopes queries to owner's URLs |
| `service/ClickEventConsumer.java` | Sets `clickEvent.setEventId(dto.getEventId())`; wraps `save()` in `DataIntegrityViolationException` catch for idempotent deduplication |
| `resources/application.properties` | Redis host/port now use env vars: `${SPRING_DATA_REDIS_HOST:localhost}`, `${SPRING_DATA_REDIS_PORT:6379}` |
| `security/ApiKeyAuthenticationService.java` | All `System.out.println` replaced with SLF4J `log.debug`; raw `secretKey` value never logged |
| `security/ApiKeyAuthenticationFilter.java` | All `System.out.println` replaced with SLF4J `log.debug`; secret header presence logged (not value) |
| `security/SecurityConfig.java` | Added permits for `/`, `/index.html`, `/favicon.ico`, `/favicon.svg`, `/vite.svg`, `/assets/**`, `/*.js`, `/*.css`, `/*.map` before `anyRequest().denyAll()` |
| `config/WebConfig.java` | Rate limiter scoped to `/api/**` and `/{shortCode}` only; excludes actuator and frontend assets |
| `dto/UrlDetailsResponse.java` | Added `clickCount long` field with getter/setter |
| `repository/ClickEventRepository.java` | Added `countByUrlMapping()`; added 4 owner-scoped `findByUrlMappingIn*` query methods |
| `controller/UrlController.java` | `getAuthenticatedOwner()` returns `null` (not `"anonymous"`) when unauthenticated; `getGlobalClicks()` passes `owner` as first arg to service |
| `exception/GlobalExceptionHandler.java` | Added handlers for `IllegalArgumentException` → HTTP 409, `NoSuchElementException` → HTTP 404 |

### Commands Run

```
mvn clean compile -q          EXIT 0  ✅
mvn dependency:resolve -q     EXIT 0  ✅
mvn test -q                   EXIT 0  ✅ (no test sources; no failures)
```

### Unresolved Issues

- **flyway-database-postgresql**: FEAT-001 spec said to add it versionless (BOM-managed). However, Spring Boot 3.2.5 BOM uses Flyway 9.22.x, and `flyway-database-postgresql` is a Flyway 10.x artifact. Adding it without a version caused a build failure. It was not added — `flyway-core` alone handles PostgreSQL in Flyway 9.x.
- **Tests**: No test source files exist yet. `mvn test` exits 0 but runs zero tests. FEAT-002+ will add targeted tests.
- **Docker/Testcontainers**: Full integration test run requires Docker. Not verified in this phase.

### Next Action

~~Proceed to FEAT-002 (tests for URL creation, redirects, auth, ownership, analytics).~~ — COMPLETED

---

## FEAT-002 — Integration Tests
**Status:** COMPLETED  
**Commit:** test: FEAT-002 integration tests

### Files Changed

| File | Change |
|------|--------|
| `pom.xml` | Upgraded `testcontainers.version` 1.19.8 → 1.20.4 for Docker Engine 29+ compatibility |
| `src/test/resources/application-test.properties` | **CREATED** — `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.enabled=true`, reduced log noise |
| `src/test/resources/testcontainers.properties` | **CREATED** — docker.host pointing to `dockerDesktopLinuxEngine` named pipe |
| `src/test/java/com/example/urlshortener/BaseIntegrationTest.java` | **CREATED** — Abstract base with shared static Testcontainers: `PostgreSQLContainer` (postgres:15-alpine), `RabbitMQContainer` (rabbitmq:4-management-alpine), `GenericContainer` (redis:7-alpine). `@DynamicPropertySource` overrides all service properties. |
| `src/test/java/com/example/urlshortener/UrlIntegrationTest.java` | **CREATED** — 12 integration tests covering context load, URL create, duplicate 409, invalid URL 400, redirect 302, 404, auth 401, list with clickCount, delete ownership, click analytics, global clicks |
| `~/.docker-java.properties` | **CREATED** — `api.version=1.44` to fix Testcontainers BadRequestException with Docker Desktop 29.x |

### Commands Run

```
mvn test-compile -q          EXIT 0  ✅
mvn test                     EXIT 0  ✅  Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
```

### Test Results

```
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 98.46s
[INFO] BUILD SUCCESS
```

All 12 tests pass:
- `contextLoads` — Flyway V1+V2+V3 applied, app healthy
- `createUrl_validRequest_returns200`
- `createUrl_duplicateAlias_returns409`
- `createUrl_invalidUrl_returns400`
- `redirect_validShortCode_returns302` (Location header verified)
- `redirect_unknownShortCode_returns404`
- `auth_noHeaders_returns401` (accepts 401 or 403)
- `auth_wrongSecret_returns401`
- `listUrls_returnsOnlyOwnUrls` (clickCount field verified)
- `deleteUrl_ownership_returns401OrNotFound`
- `clickAnalytics_afterRedirect_recordsClick` (async processing, 1500ms sleep)
- `globalClicks_scopedToOwner`

### Unresolved Issues

- **Docker Desktop 29.x API version**: Testcontainers 1.19.8 failed with `BadRequestException (Status 400)` because Docker Engine 29 raised minimum API version to 1.44. Fixed by upgrading to Testcontainers 1.20.4 AND creating `~/.docker-java.properties` with `api.version=1.44`.
- **Apache HttpClient auth retry**: `TestRestTemplate` (which wraps Apache HttpClient 4) throws `ResourceAccessException` on `DELETE` 401 responses due to automatic auth retry in streaming mode. Fixed by using Java 11's `java.net.http.HttpClient` for the `deleteUrl_ownership` test.

### Next Action

Proceed to FEAT-003 (React + Vite frontend).
