# URL Shortener

A full-stack URL shortener built with Spring Boot 3, React + Vite, PostgreSQL, Redis, and RabbitMQ. The React single-page application is served directly from the Spring Boot JAR.

## Architecture

```
Browser
  │
  ▼
Spring Boot (port 8080)
  ├── /            → React SPA (served from static resources)
  ├── /{shortCode} → Public redirect (no auth required)
  └── /api/urls/*  → REST API (API key auth required)
        │
        ├── PostgreSQL  – URL mappings + click events (Flyway-managed schema)
        ├── Redis       – URL redirect cache + rate limiting
        └── RabbitMQ    – Async click-event processing (fire-and-forget)
```

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21 |
| Maven | 3.9+ |
| Node.js | 20+ |
| Docker + Docker Compose | any recent version |

## Quick Start (Docker)

```bash
docker compose up --build
```

The application will be available at `http://localhost:8080` once all services are healthy (PostgreSQL, Redis, and RabbitMQ must pass their health checks before the app starts).

Default dev credentials printed at startup:

```
Access Key: <printed to stdout on first run>
Secret Key: <printed to stdout on first run>
```

> **Important:** Change these in production. See the [Environment Variables](#environment-variables) section.

## Local Development (without Docker)

### 1. Start backing services

```bash
# PostgreSQL
docker run -d --name pg -e POSTGRES_DB=urlshortener -e POSTGRES_USER=user \
  -e POSTGRES_PASSWORD=password -p 5432:5432 postgres:15-alpine

# Redis
docker run -d --name redis -p 6379:6379 redis:7-alpine

# RabbitMQ
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4-management-alpine
```

### 2. Set environment variables

```powershell
# PowerShell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/urlshortener"
$env:SPRING_DATASOURCE_USERNAME="user"
$env:SPRING_DATASOURCE_PASSWORD="password"
$env:SPRING_DATA_REDIS_HOST="localhost"
$env:SPRING_DATA_REDIS_PORT="6379"
$env:SPRING_RABBITMQ_HOST="localhost"
$env:SPRING_RABBITMQ_PORT="5672"
$env:APP_BASE_URL="http://localhost:8080"
```

```bash
# Bash / macOS / Linux
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/urlshortener
export SPRING_DATASOURCE_USERNAME=user
export SPRING_DATASOURCE_PASSWORD=password
export SPRING_DATA_REDIS_HOST=localhost
export SPRING_DATA_REDIS_PORT=6379
export SPRING_RABBITMQ_HOST=localhost
export SPRING_RABBITMQ_PORT=5672
export APP_BASE_URL=http://localhost:8080
```

### 3. Run the backend

```bash
mvn spring-boot:run
```

Flyway automatically applies all migrations on startup.

### 4. Run the frontend dev server

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server runs on `http://localhost:5173` and proxies all `/api` requests to `http://localhost:8080`.

## Environment Variables

| Variable | Default | Required | Description |
|----------|---------|----------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/urlshortener` | Yes (prod) | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `user` | Yes (prod) | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `password` | Yes (prod) | Database password |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Yes (prod) | Redis hostname |
| `SPRING_DATA_REDIS_PORT` | `6379` | No | Redis port |
| `SPRING_RABBITMQ_HOST` | `localhost` | Yes (prod) | RabbitMQ hostname |
| `SPRING_RABBITMQ_PORT` | `5672` | No | RabbitMQ AMQP port |
| `SPRING_RABBITMQ_USERNAME` | `guest` | Yes (prod) | RabbitMQ username |
| `SPRING_RABBITMQ_PASSWORD` | `guest` | Yes (prod) | RabbitMQ password |
| `APP_BASE_URL` | `http://localhost:8080` | Yes (prod) | Public base URL for short links |
| `SERVER_PORT` | `8080` | No | HTTP listen port |

Copy `.env.example` to `.env` and fill in real values. Never commit `.env`.

## API Reference

All `/api/urls` endpoints require `X-Access-Key` and `X-Secret-Key` headers.

### Create a short URL

```bash
curl -s -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -H "X-Access-Key: <your-access-key>" \
  -H "X-Secret-Key: <your-secret-key>" \
  -d '{"originalUrl": "https://example.com", "customAlias": "ex"}'
```

Response:

```json
{
  "shortCode": "ex",
  "shortUrl": "http://localhost:8080/ex",
  "originalUrl": "https://example.com"
}
```

### Redirect (public — no auth required)

```bash
curl -v http://localhost:8080/ex
# HTTP/1.1 302  Location: https://example.com
```

### List your URLs

```bash
curl -s http://localhost:8080/api/urls \
  -H "X-Access-Key: <your-access-key>" \
  -H "X-Secret-Key: <your-secret-key>"
```

### Delete a URL

```bash
curl -s -X DELETE http://localhost:8080/api/urls/1 \
  -H "X-Access-Key: <your-access-key>" \
  -H "X-Secret-Key: <your-secret-key>"
# HTTP 204 No Content
```

### Per-URL click analytics

```bash
curl -s http://localhost:8080/api/urls/1/clicks \
  -H "X-Access-Key: <your-access-key>" \
  -H "X-Secret-Key: <your-secret-key>"
```

### Global click analytics (scoped to your URLs)

```bash
curl -s "http://localhost:8080/api/urls/clicks?limit=50" \
  -H "X-Access-Key: <your-access-key>" \
  -H "X-Secret-Key: <your-secret-key>"
```

Optional query params: `startTime`, `endTime` (ISO-8601), `ipAddress`, `limit` (default 100).

## Running Tests

Tests use Testcontainers and require Docker to be running.

```bash
mvn test
```

Expected output:

```
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

To compile without running tests:

```bash
mvn clean compile -q
```

## Frontend Production Build

The frontend build output is copied into `src/main/resources/static` during the Docker multi-stage build. For a local `mvn package` with the frontend included:

```bash
cd frontend && npm ci && npm run build
# Copy dist/ to src/main/resources/static/ manually, then:
mvn clean package -DskipTests
```

## Railway Deployment

### 1. Provision services in Railway

1. Create a new Railway project.
2. Add a **PostgreSQL** plugin — Railway provides `DATABASE_URL`. Set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` from the plugin's connection details.
3. Add a **Redis** plugin — set `SPRING_DATA_REDIS_HOST` and `SPRING_DATA_REDIS_PORT`.
4. For RabbitMQ, Railway does not offer a native add-on. Use **CloudAMQP** free tier (see below).

### 2. CloudAMQP (RabbitMQ on Railway)

1. Sign up at [cloudamqp.com](https://www.cloudamqp.com) and create a free **Little Lemur** instance.
2. Copy the AMQP URL and parse it into:
   - `SPRING_RABBITMQ_HOST`
   - `SPRING_RABBITMQ_PORT` (usually `5672`)
   - `SPRING_RABBITMQ_USERNAME`
   - `SPRING_RABBITMQ_PASSWORD`

### 3. Set Railway environment variables

| Variable | Value |
|----------|-------|
| `SPRING_DATASOURCE_URL` | From Railway PostgreSQL plugin |
| `SPRING_DATASOURCE_USERNAME` | From Railway PostgreSQL plugin |
| `SPRING_DATASOURCE_PASSWORD` | From Railway PostgreSQL plugin |
| `SPRING_DATA_REDIS_HOST` | From Railway Redis plugin |
| `SPRING_DATA_REDIS_PORT` | `6379` |
| `SPRING_RABBITMQ_HOST` | From CloudAMQP |
| `SPRING_RABBITMQ_PORT` | From CloudAMQP |
| `SPRING_RABBITMQ_USERNAME` | From CloudAMQP |
| `SPRING_RABBITMQ_PASSWORD` | From CloudAMQP |
| `APP_BASE_URL` | Your Railway public URL, e.g. `https://your-app.railway.app` |
| `SERVER_PORT` | `8080` |

### 4. Deploy

Railway detects `railway.toml` and builds the Dockerfile automatically. The health check endpoint is `/actuator/health`.

### 5. Smoke test

```bash
# Replace with your Railway URL
BASE=https://your-app.railway.app

# Health check
curl $BASE/actuator/health

# Create a URL
curl -s -X POST $BASE/api/urls \
  -H "Content-Type: application/json" \
  -H "X-Access-Key: <key>" \
  -H "X-Secret-Key: <secret>" \
  -d '{"originalUrl": "https://example.com"}'

# Test redirect
curl -v $BASE/<shortCode>
```

## Known Limitations

- **RabbitMQ on Railway**: There is no native Railway add-on. CloudAMQP free tier (Little Lemur) is limited to 1 million messages/month and 20 concurrent connections. Sufficient for light production use.
- **Dev credentials printed at startup**: On first run the application prints generated API credentials to stdout. Rotate these before exposing the service publicly — delete the auto-generated key row and restart with a known value, or implement a seed mechanism.
- **Rate limiting**: IP-based (5 req/min) and API-key-based (100 req/hr) limits are applied via Redis. Adjust `rate.limit.*` properties for your traffic profile.
- **No HTTPS termination**: The application speaks plain HTTP. Use a reverse proxy (Railway's built-in, nginx, etc.) for TLS in production.
- **Analytics async delay**: Click events are processed via RabbitMQ asynchronously. There may be a brief delay (typically under 1 second) before a redirect registers in analytics.

## Demo Flow

```bash
BASE=http://localhost:8080

# 1. Get credentials from startup logs, then:
ACCESS=<your-access-key>
SECRET=<your-secret-key>

# 2. Create a short URL
curl -s -X POST $BASE/api/urls \
  -H "Content-Type: application/json" \
  -H "X-Access-Key: $ACCESS" \
  -H "X-Secret-Key: $SECRET" \
  -d '{"originalUrl": "https://example.com", "customAlias": "demo"}'

# 3. Redirect (no auth needed)
curl -Lv $BASE/demo

# 4. Check click analytics
curl -s $BASE/api/urls/1/clicks \
  -H "X-Access-Key: $ACCESS" \
  -H "X-Secret-Key: $SECRET"

# 5. Delete the URL
curl -s -X DELETE $BASE/api/urls/1 \
  -H "X-Access-Key: $ACCESS" \
  -H "X-Secret-Key: $SECRET"
```
