

**Terminal 1 — User Service (port 8081)**
```bash
cd user-service
mvn spring-boot:run
```

**Terminal 2 — Order Service (port 8082)**
```bash
cd order-service
mvn spring-boot:run
```

**Terminal 3 — Gateway Service (port 8080)**
```bash
cd gateway-service
mvn spring-boot:run
```


## Day 1 — basic exercises

### Exercise A — try User Service directly

```bash
curl http://localhost:8081/api/users/1
# 200 OK, JSON user

curl -i http://localhost:8081/api/users/999
# 404 Not Found, JSON error body
```

### Exercise B — try Order Service (which calls User Service internally)

```bash
curl http://localhost:8082/api/orders/101
# 200 OK — order JSON that includes an embedded "user" object,
# proving Order Service received it from User Service over HTTP.

curl -i http://localhost:8082/api/orders/103
# 404 Not Found — this order references userId 99, which doesn't
# exist in User Service. Order Service correctly propagates that.
```

### Exercise C — prove the network boundary

1. With both services running, call:
   ```bash
   curl http://localhost:8082/api/orders/101
   ```
   You should see the full response, including user details fetched
   from User Service.

2. Stop User Service (Ctrl+C in Terminal 1).

3. Call Order Service again:
   ```bash
   curl -i http://localhost:8082/api/orders/101
   ```
   You'll get **503 Service Unavailable** with a clear JSON error
   body (`USER_SERVICE_UNAVAILABLE`) instead of a crash or a hang.

4. Restart User Service and confirm Order Service recovers
   automatically (no restart needed on the Order Service side).

---

---

## Day 3 — putting a gateway in front

Day 3's instruction is to add *only* what the exercises require on top of
Days 1–2 — User Service and Order Service are untouched from Day 2. What's
new is `gateway-service`.

### Challenge 1 — call both services through the gateway

With all three services running:

```bash
# Through the gateway (port 8080) instead of hitting the backends directly
curl http://localhost:8080/api/users/1
# same 200 OK response as calling User Service on 8081 directly

curl http://localhost:8080/api/orders/101
# same 200 OK response as calling Order Service on 8082 directly

curl -i http://localhost:8080/api/users/999
# 404 Not Found — the gateway just routes; User Service's own error
# handling still applies unchanged

curl -i http://localhost:8080/api/orders/999
# 404 Not Found — same idea for Order Service
```

The routing table lives entirely in
`gateway-service/src/main/resources/application.yml` — no Java route
classes to read. `/api/users/**` and `/api/orders/**` map to the two
backend URLs, which are themselves externalized (same `base-url` pattern
Day 2 established for `order-service`).

**Verify the backends still work standalone** — the gateway doesn't
replace direct access, it adds an option:
```bash
curl http://localhost:8081/api/users/1     # still works directly
curl http://localhost:8082/api/orders/101  # still works directly
```

### Challenge 2 — configuration thinking

See **`CONFIGURATION-NOTES.md`** for the full deliverable: every
environment-specific value across all three services, and an honest list
of what gets harder at 15–20 services (and why service discovery and
centralized config become worth their cost at that scale, even though
neither is built here).

### Database ownership

See **`DATA-OWNERSHIP.md`** for the ownership assignment exercise
(Users/Orders/Payments/Products) and the "should Order Service query
User's table directly" discussion.

### Mini architecture challenge

See **`ARCHITECTURE.md`** for the Client → Gateway → Order → User/Payment
design walkthrough, including what Order Service should do if Payment
Service is unavailable. Payment Service is design-only — not built in
this repo, per the handbook's own scope.

---

## Day 2 — what changed, and why

Day 2 is about improving yesterday's two services, not building new
ones. Nothing below adds a new service or a new capability — it makes
the existing one clearer.

### 1. Intentional response models (Challenge 1)

- `user-service`'s `UserDto` was renamed to **`UserResponse`** and
  documented as the API contract, separate from the internal `User`
  model (which can now gain internal-only fields — e.g. account
  status — without ever leaking into the API).
- `order-service`'s DTOs were renamed to **`OrderResponse`** and
  **`UserSummary`**. `UserSummary` is Order Service's *own* view of a
  user, defined from Order Service's point of view — it is not User
  Service's `UserResponse` reused, even though the fields currently
  match.
- See **`API-CONTRACTS.md`** for the full documented contract of both
  endpoints — the thing a consumer should be able to read instead of
  your entity classes.

### 2. Deliberate failure handling (failure exercise)

Order Service now distinguishes four outcomes instead of two, and
logs them differently so an operator can tell "slow" apart from
"down":

| Situation | Client sees | Internally logged as |
|---|---|---|
| Order doesn't exist | `404 ORDER_NOT_FOUND` | info |
| Order's user doesn't exist | `404 RELATED_USER_NOT_FOUND` | info |
| User Service down / connection refused | `503 USER_SERVICE_UNAVAILABLE` | warn: "could not connect" |
| User Service too slow (read timeout) | `503 USER_SERVICE_UNAVAILABLE` | warn: "timed out" |
| User Service itself returns 5xx | `503 USER_SERVICE_UNAVAILABLE` | warn: status code |

**Try the "too slow" case yourself:** temporarily set
`user-service.read-timeout-ms` in `order-service/application.yml` to
something tiny (e.g. `1`), restart Order Service, and call
`/api/orders/101` — you'll see the timeout branch trigger in the logs
even though User Service is perfectly healthy. Put the value back
afterward.

### 3. Externalized configuration (Challenge 2)

`UserServiceProperties` (in `order-service`) now binds the base URL
*and* both timeouts from configuration — nothing about how to reach
User Service is hard-coded in Java:

```yaml
# order-service/src/main/resources/application.yml
user-service:
  base-url: http://localhost:8081
  connect-timeout-ms: 3000
  read-timeout-ms: 3000
```

**Prove it's really externalized** — no code changes, just config:
```bash
# 1. Point Order Service at a port nothing is listening on:
cd order-service
mvn spring-boot:run -Dspring-boot.run.arguments=--user-service.base-url=http://localhost:9999

# 2. Call it — you should get 503 USER_SERVICE_UNAVAILABLE immediately
curl -i http://localhost:8082/api/orders/101

# 3. Stop it, restart normally (no arguments), confirm it works again
mvn spring-boot:run
curl http://localhost:8082/api/orders/101
```

### Day 2 quality-check pass

- Endpoint names: `GET /api/users/{id}`, `GET /api/orders/{id}` — clear, resource-oriented.
- Response models are intentional and documented (`API-CONTRACTS.md`).
- HTTP status codes are meaningful and distinguish missing-resource from downstream-failure.
- User Service's location is fully configurable (URL + both timeouts).
- No shared code or database between the two services.

## Suggested Git workflow (Day 2 increment)

```
git add user-service/.../dto/UserResponse.java user-service/.../model/User.java
git commit -m "clarify API contract: rename UserDto to UserResponse, document boundary"

git add order-service/.../dto/OrderResponse.java order-service/.../dto/UserSummary.java
git commit -m "clarify API contract: intentional response models for order-service"

git add order-service/.../client/UserServiceClient.java
git commit -m "distinguish timeout vs connection-refused vs downstream 5xx in logs"

git add order-service/.../config/UserServiceProperties.java order-service/.../config/RestTemplateConfig.java
git commit -m "externalize user-service base url and timeouts via ConfigurationProperties"

git add order-service/src/main/resources/application.yml
git commit -m "document configurable user-service properties"

git add API-CONTRACTS.md README.md
git commit -m "document Day 2 API contracts and exercises"
```

## Suggested Git workflow (Day 3 increment)

```
git add gateway-service/pom.xml gateway-service/src/main/java/.../GatewayServiceApplication.java
git commit -m "create gateway service skeleton"

git add gateway-service/src/main/resources/application.yml
git commit -m "configure gateway routes for user-service and order-service"

git add DATA-OWNERSHIP.md
git commit -m "document data ownership exercise"

git add CONFIGURATION-NOTES.md
git commit -m "document configuration-at-scale notes"

git add ARCHITECTURE.md
git commit -m "document mini enterprise architecture challenge"

git add README.md
git commit -m "document Day 3 gateway exercises"
```
