# URL Shortener

A production-ready URL shortener built with **Spring Boot 4**, **MySQL sharding**, and **Redis** for caching, distributed ID generation, and rate limiting.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Setup & Running Locally](#setup--running-locally)
- [Environment Variables](#environment-variables)
- [API Reference](#api-reference)
- [How It Works](#how-it-works)
- [Project Structure](#project-structure)

---

## Overview

This service takes a long URL and returns a short code (e.g. `http://localhost:8080/aB3xYz`). When someone visits the short URL, they are redirected to the original long URL via an HTTP 302 redirect.

Key design goals:

- **Distributed-safe ID generation** — Redis `INCR` ensures unique IDs across multiple app instances, with a local counter as fallback.
- **Horizontal write scalability** — URLs are stored across two MySQL shards, routed by `hashCode(shortCode) % 2`.
- **Fast reads** — Redis caches every resolved URL for 24 hours, so most redirects never touch the database.
- **Abuse prevention** — A Redis-backed sliding window rate limiter allows 10 shorten requests per IP per minute.

---

## Architecture

```
Client
  │
  ▼
UrlController
  ├── POST /shorten
  │     ├── RateLimiterService  (Redis INCR + EXPIRE per IP)
  │     ├── RedisIdGenerator    (Redis INCR → global unique long ID)
  │     ├── Base62Generator     (long ID → short alphanumeric code)
  │     ├── ShardedUrlRepository.save()
  │     │     └── ShardRouter   (hashCode(code) % 2 → MySQL shard 0 or 1)
  │     └── RedisCacheService.put()  (cache for 24 h)
  │
  └── GET /{code}
        ├── RedisCacheService.get()  (cache hit → immediate redirect)
        └── ShardedUrlRepository.findLongUrl()  (cache miss → DB lookup → backfill cache)


Infrastructure (Docker)
  ├── mysql-shard-0  (port 3308)
  ├── mysql-shard-1  (port 3309)
  └── redis          (port 6379)
```

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| Framework | Spring Boot 4.0.6 |
| Language | Java 26 |
| Primary Database | MySQL 8.4 (2 shards) |
| Cache / Rate Limiting | Redis 7 |
| ORM / JDBC | Spring Data JPA, Spring JDBC |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Build Tool | Maven (Maven Wrapper included) |
| Containerization | Docker Compose |
| Boilerplate reduction | Lombok |

---

## Prerequisites

Make sure the following are installed on your machine:

- [Java 26+](https://adoptium.net/)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (or Docker Engine + Compose)
- Maven is **not** required — the Maven Wrapper (`./mvnw`) is included

---

## Setup & Running Locally

### 1. Clone the repository

```bash
git clone https://github.com/your-username/Url-Shortener.git
cd Url-Shortener
```

### 2. Configure environment variables

Copy the example env file and fill in your values:

```bash
cp .env.example .env
```

Open `.env` and set a secure password for `MYSQL_ROOT_PASSWORD`. The defaults for everything else work out of the box.

### 3. Start the infrastructure

Spin up both MySQL shards and Redis using Docker Compose:

```bash
docker compose up -d
```

Wait a few seconds for the containers to become healthy. You can verify with:

```bash
docker compose ps
```

All three services (`mysql-shard-0`, `mysql-shard-1`, `redis`) should show `healthy`.

### 4. Run the application

**Linux / macOS:**

```bash
./mvnw spring-boot:run
```

**Windows (Command Prompt / PowerShell):**

```cmd
mvnw.cmd spring-boot:run
```

The application starts on **`http://localhost:8080`**.

### 5. Verify

Open the Swagger UI at [`http://localhost:8080/swagger-ui.html`](http://localhost:8080/swagger-ui.html) to explore and test the API interactively.

Or send a quick test request:

```bash
curl -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.google.com"}'
```

You should receive a response like:

```json
{
  "shortUrl": "http://localhost:8080/aB3xYz"
}
```

---

## Environment Variables

All variables are read from the `.env` file at startup (via `spring.config.import`).

| Variable | Default | Description |
|----------|---------|-------------|
| `MYSQL_HOST` | `localhost` | MySQL host |
| `DB_USERNAME` | `root` | MySQL username |
| `MYSQL_ROOT_PASSWORD` | *(required)* | MySQL root password |
| `MYSQL_DATABASE` | `url_shortener` | Database name |
| `MYSQL_SHARD_0_PORT` | `3308` | Host port for MySQL shard 0 |
| `MYSQL_SHARD_1_PORT` | `3309` | Host port for MySQL shard 1 |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |

---

## API Reference

### Shorten a URL

**`POST /shorten`**

Request body:

```json
{
  "url": "https://some-very-long-website.com/path/to/page"
}
```

Success response (`200 OK`):

```json
{
  "shortUrl": "http://localhost:8080/aB3xYz"
}
```

Rate-limited response (`429 Too Many Requests`):

```
Too many requests. Try again later.
```

> Rate limit: **10 requests per minute per IP address**.

---

### Redirect to original URL

**`GET /{code}`**

Redirects the client to the original long URL with an HTTP `302 Found` response.

```bash
curl -L http://localhost:8080/aB3xYz
# follows the redirect and lands on the original page
```

---

### Debug shard lookup (development only)

**`GET /test/{code}`**

Returns the raw long URL string by querying the correct shard directly. Useful for verifying shard routing.

```bash
curl http://localhost:8080/test/aB3xYz
# https://some-very-long-website.com/path/to/page
```

---

## How It Works

### Short Code Generation

1. `RedisIdGenerator` calls Redis `INCR` on a shared counter key (`url:id:counter`), producing a globally unique monotonically increasing `long`. If Redis is unavailable, it falls back to a local `AtomicLong` seeded with the current timestamp.
2. `Base62Generator` encodes the `long` into a Base62 string (`a-z`, `A-Z`, `0-9`), yielding a compact alphanumeric short code.

### Database Sharding

- Two MySQL instances act as independent shards.
- `ShardRouter` deterministically maps any short code to a shard: `Math.abs(shortCode.hashCode()) % 2`.
- `ShardedUrlRepository` creates a `JdbcTemplate` scoped to the chosen shard's `DataSource` for every read/write.
- Both shards are initialized with the same schema via `docker/mysql/init.sql`.

### Caching

- On every `shorten` call, the `(shortCode → longUrl)` mapping is written to Redis with a **24-hour TTL**.
- On every `resolve` call, Redis is checked first. A cache miss falls through to the shard lookup and backfills the cache.
- Redis errors are swallowed gracefully — the system degrades to DB-only reads/writes.

### Rate Limiting

`RateLimiterService` uses Redis `INCR` + `EXPIRE` to implement a fixed-window counter per IP:

- On the first request in a window, the key is created and given a 1-minute TTL.
- Subsequent requests increment the counter; requests beyond the limit of **10** receive a `429` response.
- If Redis is unavailable, the limiter fails open (requests are allowed through).

---

## Project Structure

```
src/main/java/com/roshan/Url/Shortener/
├── UrlShortenerApplication.java       # Spring Boot entry point
├── config/
│   ├── ClientIpResolver.java          # Extracts real client IP (X-Forwarded-For aware)
│   ├── RedisConfig.java               # Redis template configuration
│   └── ShardDataSourceConfig.java     # Builds DataSource beans for each MySQL shard
├── controller/
│   ├── UrlController.java             # POST /shorten, GET /{code}
│   └── ShardTestController.java       # GET /test/{code} (debug)
├── generator/
│   ├── Base62Generator.java           # Encodes long IDs to Base62 short codes
│   └── RedisIdGenerator.java          # Distributed unique ID generation via Redis INCR
├── model/
│   └── UrlMapping.java                # JPA entity: shortCode, longUrl, createdAt
├── repository/
│   ├── ShardedUrlRepository.java      # JDBC-based shard-aware read/write
│   └── UrlRepository.java             # JPA repository (unused in main flow)
└── service/
    ├── CacheService.java              # Cache abstraction interface
    ├── RedisCacheService.java         # Redis implementation of CacheService
    ├── RateLimiterService.java        # IP-based rate limiting via Redis
    ├── ShardRouter.java               # Routes a short code to the correct DataSource
    └── UrlService.java                # Core shorten/resolve business logic

docker/
└── mysql/
    └── init.sql                       # Schema applied to both MySQL shards on first boot

docker-compose.yml                     # mysql-shard-0, mysql-shard-1, redis
.env.example                           # Template for required environment variables
```
