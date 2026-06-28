# Distributed Rate Limiter & Analytics Engine

A production-ready middleware architecture for Spring Boot APIs featuring atomic, Redis-backed sliding window rate limiting and high-efficiency analytics tracking using HyperLogLog.

---

## 🚀 Getting Started

### Prerequisites
- Java 17 or higher
- Maven 3.x or Maven wrapper
- Redis (either running locally on port 6379, or via Docker Compose)

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

### 1. Test Endpoint `/api/v1/data` (Limit: 10 requests / 60s)
Run multiple requests in succession:
```bash
# Allowed requests (first 10 requests)
curl -i http://localhost:8080/api/v1/data

# 11th request (Throttled - HTTP 429)
curl -i http://localhost:8080/api/v1/data
```
**Expected Throttled Response (HTTP 429):**
- Headers: `Retry-After: 60`
- Body:
```json
{
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Maximum allowed: 10 requests per 60 seconds. Please try again after 60 seconds."
}
```

### 2. Test Endpoint `/api/v1/profile` (Limit: 3 requests / 60s)
This endpoint handles writes, and has a stricter limit:
```bash
# Try calling it 4 times in a row
curl -i -X POST http://localhost:8080/api/v1/profile
```
The 4th request will immediately result in an HTTP `429 Too Many Requests`.

### 3. Verify Daily Active Consumers (DAU) Metrics
To verify that successful requests are logged to the HyperLogLog:
```bash
curl http://localhost:8080/admin/metrics/dau
```
**Expected Response:**
```json
{
  "date": "2026-06-28",
  "unique_users": 1
}
```
If you change client IP header parameters (e.g., using `X-Forwarded-For: 192.168.1.50`), the count will dynamically increment.

---

## 📂 Codebase Details

- **Core Algorithm**: Located in [`sliding_window_rate_limit.lua`](src/main/resources/scripts/sliding_window_rate_limit.lua). Runs atomically on Redis to avoid concurrency race conditions.
- **Interception Middleware**: Located in [`RateLimitInterceptor.java`](src/main/java/com/example/ratelimiter/interceptor/RateLimitInterceptor.java). Determines limits, executes the script, and returns HTTP `429` status responses.
- **Telemetry**: Located in [`AnalyticsListener.java`](src/main/java/com/example/ratelimiter/listener/AnalyticsListener.java). Observes approved requests asynchronously (`@Async`) and registers them in Redis HyperLogLog.
