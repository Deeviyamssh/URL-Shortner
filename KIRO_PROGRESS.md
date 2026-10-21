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

Proceed to FEAT-002 (tests for URL creation, redirects, auth, ownership, analytics).
