# Distributed Rate Limiter & Analytics Engine

A production-ready middleware architecture for Spring Boot APIs featuring atomic, Redis-backed sliding window rate limiting and high-efficiency analytics tracking using HyperLogLog.

---

## 🔒 Security & Client Tier Authentication

Requests are authenticated via the `X-API-KEY` header. Clients are mapped to one of three service tiers, each with dynamic rate limit quotas per endpoint:

| Tier | `/api/v1/data` quota | `/api/v1/profile` quota | Key Identifier |
| :--- | :--- | :--- | :--- |
| **PREMIUM** | 50 requests / 60s | 15 requests / 60s | `key_premium_abc123` |
| **STANDARD** | 10 requests / 60s | 3 requests / 60s | `key_standard_xyz789` |
| **ANONYMOUS** | 2 requests / 60s | 1 request / 60s | *No Key / Client IP* |

---

## 🛠️ Running the Application

### Option A: Local Run
1. Start your local Redis instance on port `6379`.
2. Run the application from your IDE or terminal:
   ```bash
   mvn spring-boot:run
   ```

### Option B: Docker Compose Run
If you have Docker installed, you can spin up the application and Redis together with one command:
```bash
docker compose up --build -d
```
This builds the Spring Boot multi-stage image and links it to the Redis container on port `8080`.

---

## 🧪 Validating Rate Limits & Analytics

### 1. Test Anonymous (No Key) Tier (Limit: 2 requests / 60s)
Execute requests without the header:
```bash
# First 2 requests succeed
curl -i http://localhost:8080/api/v1/data
curl -i http://localhost:8080/api/v1/data

# 3rd request is blocked with HTTP 429
curl -i http://localhost:8080/api/v1/data
```
**Expected Response:**
```json
{
  "error": "Too Many Requests",
  "message": "Rate limit exceeded for tier ANONYMOUS. Maximum allowed: 2 requests per 60 seconds. Please try again after 60 seconds."
}
```

### 2. Test Standard Key Tier (Limit: 10 requests / 60s)
Pass the Standard API Key:
```bash
# Call it up to 10 times in a row
curl -i -H "X-API-KEY: key_standard_xyz789" http://localhost:8080/api/v1/data
```

### 3. Test Premium Key Tier (Limit: 50 requests / 60s)
Pass the Premium API Key:
```bash
# Allows up to 50 requests in a sliding window
curl -i -H "X-API-KEY: key_premium_abc123" http://localhost:8080/api/v1/data
```

### 4. Verify Daily Active Consumers (DAU) Metrics
Query telemetry metrics:
```bash
curl http://localhost:8080/admin/metrics/dau
```
**Expected Response:**
```json
{
  "date": "2026-06-28",
  "unique_users": 3
}
```
*(User count will accurately track the premium key, standard key, and anonymous IP address as distinct active users in the HyperLogLog).*

---

## 📂 Codebase Details

- **Core Algorithm**: Located in [`sliding_window_rate_limit.lua`](src/main/resources/scripts/sliding_window_rate_limit.lua). Runs atomically on Redis to avoid concurrency race conditions.
- **Client Tier Management**: Managed inside [`UserTier.java`](src/main/java/com/example/ratelimiter/model/UserTier.java) and [`ApiKeyService.java`](src/main/java/com/example/ratelimiter/service/ApiKeyService.java).
- **Interception Middleware**: Located in [`RateLimitInterceptor.java`](src/main/java/com/example/ratelimiter/interceptor/RateLimitInterceptor.java). Determines client tier, maps the correct quotas, and filters traffic.
- **Telemetry**: Located in [`AnalyticsListener.java`](src/main/java/com/example/ratelimiter/listener/AnalyticsListener.java). Observes approved requests asynchronously (`@Async`) and registers them in Redis HyperLogLog.
