# E-Commerce Microservices Platform

Production-style portfolio project demonstrating a modular e-commerce backend built with **Java 21**, **Spring Boot 4.x**, and **Spring Cloud**.

The platform is delivered **incrementally by phase**. Current status: **Phase 0–7** (platform through Payment Service) are complete.

## Purpose

Provide a clean, portfolio-ready foundation for a microservices e-commerce system covering catalog, users, orders, payments, and notifications—backed by centralized configuration, service discovery, an API gateway, messaging, caching, and resilience patterns as later phases are implemented.

## Planned Architecture

```text
                     ┌─────────────────┐
                     │  Config Server  │
                     │     :8888       │
                     └────────┬────────┘
                              │
                     ┌────────▼────────┐
 Clients ──────────► │   API Gateway   │
                     │     :8080       │
                     └────────┬────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
     ┌────────▼──────┐ ┌──────▼──────┐ ┌──────▼──────────┐
     │ user-service  │ │product-svc  │ │ order-service   │
     │    :8081      │ │   :8082     │ │    :8083        │
     └───────────────┘ └─────────────┘ └───────┬─────────┘
                                               │
                                    ┌──────────┼──────────┐
                                    │          │          │
                           ┌────────▼──┐ ┌─────▼────┐ ┌───▼──────────────┐
                           │ payment   │ │ notif.   │ │ Discovery (Eureka)│
                           │  :8084    │ │  :8085   │ │      :8761       │
                           └───────────┘ └──────────┘ └──────────────────┘

Shared infrastructure (later phases): MySQL, MongoDB, Redis, Kafka
```

Cross-cutting capabilities:

- Spring Cloud Config for centralized configuration (**Phase 1 — implemented**)
- Netflix Eureka for service discovery (**Phase 2 — implemented**)
- Spring Cloud Gateway for routing and edge concerns (**Phase 3 — implemented**)
- Resilience4j for circuit breaking / retries (**Phase 6 — Product Service calls from Order Service**)
- Spring Security + JWT for authentication/authorization (**Phases 4–6**)
- Kafka for asynchronous events (planned)
- Redis for caching where appropriate (**Phase 5 — product-service**)
- OpenAPI / Swagger for API documentation (**Phases 4–6**)
- Testcontainers for integration tests (**Phase 5**)

## Services

| Service | Port | Planned role |
| --- | --- | --- |
| `config-server` | 8888 | Centralized configuration (**Phase 1**) |
| `discovery-server` | 8761 | Eureka service registry (**Phase 2**) |
| `api-gateway` | 8080 | Edge routing / API entrypoint (**Phase 3**) |
| `user-service` | 8081 | Users, auth-related APIs (**Phase 4**) |
| `product-service` | 8082 | Product catalog (**Phase 5**) |
| `order-service` | 8083 | Order lifecycle (**Phase 6**) |
| `payment-service` | 8084 | Payment processing (**Phase 7**) |
| `notification-service` | 8085 | Email/SMS/push notifications |

## Technologies

| Area | Choice |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot **4.1.1** |
| Spring Cloud | **2025.1.3** (verified via [start.spring.io](https://start.spring.io) for Boot 4.1.1) |
| Build | Maven (each service independently buildable) |
| Containers | Docker / Docker Compose |
| Data stores | MySQL (`user-service`, `order-service`, `payment-service`, `notification-service`), MongoDB + Redis (`product-service`) |
| Messaging (planned) | Apache Kafka (not required for current phases) |
| Resilience | Resilience4j on Order→Product and Payment→Order synchronous calls |
| Security | Spring Security + JWT (`user-service`, `order-service`, `payment-service`, `notification-service`) |
| API docs | OpenAPI / Swagger |
| Testing | JUnit 5 + Mockito + Testcontainers |
| CI/CD | GitHub Actions (`.github/workflows/ci.yml`) |

## Current Implementation Status

| Phase | Scope | Status |
| --- | --- | --- |
| **Phase 0** | Root layout, 8 service skeletons, ports, Actuator, README, Compose placeholder | **Complete** |
| **Phase 1** | Spring Cloud Config Server + local `config-repo` | **Complete** |
| **Phase 2** | Eureka Discovery Server (standalone registry) | **Complete** |
| **Phase 3** | Spring Cloud Gateway + Eureka client + discovery routes | **Complete** |
| **Phase 4** | User Service (JPA, MySQL, JWT, Eureka, Config client) | **Complete** |
| **Phase 5** | Product Service (MongoDB, Redis cache, Eureka, Config client) | **Complete** |
| **Phase 6** | Order Service (JPA, MySQL, Feign→Product, Resilience4j, JWT) | **Complete** |
| **Phase 7** | Payment Service (JPA, MySQL, Feign→Order, Resilience4j, JWT) | **Complete** |
| **Phase 8** | Notification Service (JPA, MySQL, JWT, ownership APIs) | **Complete** |
| **Phase 9** | Docker + full runtime (`docker-compose.yml`) | **Complete** |
| **Phase 10** | Testing + lightweight observability (Actuator health, logs, request IDs) | **Complete** |
| **Phase 11** | Deployment docs + GitHub Actions CI | **Complete** |

### Phase 1 — Config Server

Spring Cloud Config Server serves shared and service-specific configuration from a **local native filesystem repository** at `config-repo/`.

```text
config-repo/
├── application.yml          # common / shared settings
├── user-service.yml
├── product-service.yml
├── order-service.yml
├── payment-service.yml
└── notification-service.yml
```

**What Config Server does**

- Acts as a single HTTP endpoint for application configuration
- Merges shared (`application.yml`) and application-specific files
- Lets you change config in one place instead of editing every service

**Why centralized configuration is useful**

- Consistent defaults across microservices
- Environment-specific overlays without rebuilding every JAR
- Safer operational changes (ports, feature flags, non-secret defaults)
- Secrets stay out of Git (use env vars / a secret manager later)

**How services will consume configuration later**

A later step will add the Config **client** starter to each service and point it at `http://localhost:8888` (via `spring.config.import` / `spring.cloud.config.uri`). Clients are **not** wired in Phase 1.

**Security note:** `config-repo` contains only non-secret defaults. Do not commit passwords, API keys, or tokens.

### Phase 2 — Eureka Discovery Server

`discovery-server` runs a **standalone Netflix Eureka Server** on port **8761**.

**What Eureka Service Discovery is**

Eureka is a service registry. Microservices register their network location (host/port) and look up other services by name instead of hard-coding URLs.

**Why it is used in this project**

- Decouples callers from fixed hostnames and ports
- Supports horizontal scaling (multiple instances under one service name)
- Lets the future API Gateway route by service id via the registry
- Matches common production Spring Cloud topologies for portfolios and real systems

**Current Phase 2 scope**

- Eureka Server only (dashboard + registry endpoint)
- `register-with-eureka: false` and `fetch-registry: false` (standalone; the server does not act as a client or peer replica)
- Other services are **not** registered yet (client registration comes later)

**Eureka server port:** `8761`

### Phase 3 — API Gateway

`api-gateway` is the **single public entrypoint** for HTTP clients on port **8080**, built with **Spring Cloud Gateway (WebMVC)** and registered as a **Eureka client**.

**Purpose of the API Gateway**

- Provides one URL for external clients instead of many service ports
- Routes requests to the correct microservice by path
- Centralizes cross-cutting edge concerns later (auth, rate limiting, logging)

**Why Gateway is used**

- Hides internal service topology from clients
- Uses Eureka + `lb://` URIs so routes target logical service names, not hard-coded hosts
- Keeps domain services focused on business APIs

**Relationship between Gateway and Eureka**

- Eureka (`discovery-server`) holds the registry of running service instances
- The Gateway registers itself and **fetches** the registry from `http://localhost:8761/eureka/`
- Routes such as `lb://USER-SERVICE` resolve instances dynamically via Spring Cloud LoadBalancer

**Configured routes**

| Path | Target |
| --- | --- |
| `/api/users/**` | `lb://USER-SERVICE` |
| `/api/products/**` | `lb://PRODUCT-SERVICE` |
| `/api/orders/**` | `lb://ORDER-SERVICE` |
| `/api/payments/**` | `lb://PAYMENT-SERVICE` |

Target services do not need to be running for Phase 3; routes are configured for future use. Proxy calls return errors until services register with Eureka.

**Public health endpoint:** `GET /actuator/health` (no JWT/security in this phase)

### Phase 4 — User Service

First business microservice: user registration, authentication (JWT), and profile management.

**Responsibility**

- Owns user accounts in a dedicated MySQL database (`ecommerce_users`)
- Exposes auth and user APIs under `/api/users/**`
- Registers with Eureka as `USER-SERVICE`
- Loads configuration from Config Server

**Database:** `ecommerce_users` (isolated; not shared with other services)

**Authentication flow**

1. Client calls `POST /api/users/auth/register` with name, email, password
2. Service validates input, checks duplicate email, BCrypt-hashes password, saves user with role `USER`
3. Client calls `POST /api/users/auth/login` with email/password
4. Service authenticates via Spring Security and returns a JWT + user profile (password never returned)

**JWT flow**

1. Login returns `Bearer` token
2. Client sends `Authorization: Bearer <token>` on protected endpoints
3. `JwtAuthenticationFilter` validates token signature/expiry and loads user details
4. Claims include subject (email), `role`, and `userId`

**Required environment variables (never commit secrets)**

| Variable | Purpose |
| --- | --- |
| `JWT_SECRET` | HMAC secret (min 32 characters) |
| `DB_PASSWORD` | MySQL password |
| `DB_USERNAME` | MySQL username (default `root`) |
| `DB_HOST` / `DB_PORT` | Optional (default `localhost:3306`) |

**API endpoints**

| Method | Path | Access |
| --- | --- | --- |
| POST | `/api/users/auth/register` | Public |
| POST | `/api/users/auth/login` | Public |
| GET | `/api/users/me` | Authenticated |
| GET | `/api/users/{id}` | Owner or ADMIN |
| PUT | `/api/users/{id}` | Owner or ADMIN |
| DELETE | `/api/users/{id}` | Owner or ADMIN |
| GET | `/actuator/health` | Public |
| GET | `/swagger-ui.html` | Public |

### Phase 5 — Product Service

Catalog microservice backed by **MongoDB** with **Redis** caching via Spring Cache.

**Responsibility**

- Owns product catalog in MongoDB database `ecommerce_catalog` (collection `products`)
- Exposes product CRUD/status/stock APIs under `/api/products/**`
- Registers with Eureka as `PRODUCT-SERVICE`
- Loads configuration from Config Server
- Caches product-by-id and list queries in Redis

**MongoDB usage**

- Document model with `BigDecimal` price (`DECIMAL128`), unique SKU index
- Query support: category, active flag, name regex, pagination, sorting
- No MySQL for products

**Redis caching strategy**

| Cache | Key | TTL (default) |
| --- | --- | --- |
| `products` | product id | `CACHE_PRODUCTS_TTL_MS` / 300000 ms (5 min) |
| `productLists` | filter + page + sort | `CACHE_PRODUCT_LISTS_TTL_MS` / 120000 ms (2 min) |

**Cache invalidation**

On create / update / delete / status change / stock change:

- Evict `products` entry for that id (mutations)
- Evict all `productLists` entries

**API endpoints**

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/products` | Create |
| GET | `/api/products/{id}` | Cached |
| GET | `/api/products` | Pagination/filter/sort; cached |
| PUT | `/api/products/{id}` | Update |
| DELETE | `/api/products/{id}` | Delete |
| PATCH | `/api/products/{id}/status` | `{ "active": true\|false }` |
| PATCH | `/api/products/{id}/stock` | `{ "stockQuantity": n }` |

List example: `GET /api/products?page=0&size=20&sort=createdAt,desc&category=Electronics&active=true&name=Keyboard`

## Prerequisites

- JDK 21+
- Apache Maven 3.9+
- Docker / Docker Compose (for later phases)

## How to Build Each Service

```bash
for s in config-server discovery-server api-gateway user-service product-service order-service payment-service notification-service; do
  (cd "$s" && mvn clean verify) || exit 1
done
```

## How to Run Config Server

From the repository root, run Maven inside `config-server` so the native search path `file:../config-repo` resolves correctly:

```bash
cd config-server
mvn spring-boot:run
```

Health check:

```bash
curl http://localhost:8888/actuator/health
```

## How to Verify Config Server

Fetch merged configuration for a service (default profile):

```bash
curl http://localhost:8888/user-service/default
```

You should see JSON containing property sources from `user-service.yml` and shared `application.yml` (for example `app.user.registration-enabled` and `platform.name`).

Other useful endpoints:

```bash
curl http://localhost:8888/application/default
curl http://localhost:8888/product-service/default
curl http://localhost:8888/order-service/default
curl http://localhost:8888/payment-service/default
curl http://localhost:8888/notification-service/default
```

## How to Run Discovery Server (Eureka)

```bash
cd discovery-server
mvn spring-boot:run
```

Health check:

```bash
curl http://localhost:8761/actuator/health
```

## How to Verify Eureka

Open the Eureka dashboard in a browser:

[http://localhost:8761](http://localhost:8761)

You should see the Eureka UI. With no clients registered yet, the “Instances currently registered with Eureka” list is empty (expected for Phase 2).

Optional HTTP checks:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8761/
curl http://localhost:8761/actuator/health
```

## How to Run API Gateway

Start Eureka first, then the Gateway:

```bash
cd discovery-server
mvn spring-boot:run
```

In another terminal:

```bash
cd api-gateway
mvn spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

## How to Test API Gateway

1. Confirm Gateway startup logs show Eureka registration (e.g. `DiscoveryClient` / `Registered` / `Fetching registry`).
2. Open [http://localhost:8761](http://localhost:8761) and verify `API-GATEWAY` appears under registered instances.
3. Call the health endpoint:

```bash
curl http://localhost:8080/actuator/health
```

4. Optional route smoke test (expected to fail until `user-service` registers and exposes APIs):

```bash
curl -i http://localhost:8080/api/users/health
```

## How to Run MySQL (User Service)

```bash
docker compose up -d mysql-users
```

Default root password: `rootpass` (override with `MYSQL_ROOT_PASSWORD`).

## How to Run MongoDB and Redis (Product Service)

```bash
docker compose up -d mongodb-catalog redis-cache
```

- MongoDB: `mongodb://localhost:27017/ecommerce_catalog`
- Redis: `localhost:6379` (optional `REDIS_PASSWORD`)

## How to Run User Service

Start Config Server and Eureka first, then:

```bash
export JWT_SECRET='local-dev-jwt-secret-key-min-32-chars'
export DB_PASSWORD='rootpass'
export DB_USERNAME='root'

cd user-service
mvn spring-boot:run
```

Health:

```bash
curl http://localhost:8081/actuator/health
```

Register:

```bash
curl -X POST http://localhost:8081/api/users/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "password": "password123"
  }'
```

Login:

```bash
curl -X POST http://localhost:8081/api/users/auth/login \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "john.doe@example.com",
    "password": "password123"
  }'
```

Use the returned token:

```bash
curl http://localhost:8081/api/users/me \
  -H "Authorization: Bearer <token>"
```

## Testing User Service

```bash
cd user-service
mvn clean test
```

Includes unit tests (Mockito) and integration tests (MockMvc + H2 test profile).

## How to Run Product Service

Start Config Server and Eureka, then MongoDB + Redis:

```bash
docker compose up -d mongodb-catalog redis-cache

cd config-server && mvn spring-boot:run
# other terminal
cd discovery-server && mvn spring-boot:run
# other terminal
cd product-service && mvn spring-boot:run
```

Optional env overrides:

```bash
export MONGODB_URI='mongodb://localhost:27017/ecommerce_catalog'
export REDIS_HOST=localhost
export REDIS_PORT=6379
export CACHE_PRODUCTS_TTL_MS=300000
export CACHE_PRODUCT_LISTS_TTL_MS=120000
```

Health:

```bash
curl http://localhost:8082/actuator/health
```

Sample API calls:

```bash
# Create
curl -X POST http://localhost:8082/api/products \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Wireless Mouse",
    "description": "Ergonomic mouse",
    "sku": "MOUSE-001",
    "price": 29.99,
    "stockQuantity": 100,
    "category": "Electronics",
    "imageUrl": "https://cdn.example.com/mouse.png",
    "active": true
  }'

# Get by id (cached)
curl http://localhost:8082/api/products/<id>

# List
curl 'http://localhost:8082/api/products?page=0&size=20&sort=createdAt,desc'

# Update
curl -X PUT http://localhost:8082/api/products/<id> \
  -H 'Content-Type: application/json' \
  -d '{"name":"Pro Wireless Mouse","price":34.99}'
```

## Testing Product Service

```bash
cd product-service
mvn clean test
```

### Phase 6 — Order Service

`order-service` owns the order lifecycle. It persists orders in its own MySQL database (`ecommerce_orders`), authenticates with the same JWT secret as User Service, and communicates **synchronously** with Product Service via Eureka discovery (OpenFeign). It does **not** access Product MongoDB or User MySQL directly.

#### Responsibility

- Create orders with multiple line items
- Snapshot product name and unit price at order time
- Reserve / release stock through Product Service APIs
- Enforce order status transitions and cancellation rules
- Authorize owners vs ADMIN for read/update/cancel

#### Database design

Database: **`ecommerce_orders`** (separate from `ecommerce_users`).

```text
orders (1) ──────< (N) order_items
```

| Table | Key columns |
| --- | --- |
| `orders` | `id`, `user_id`, `order_number`, `total_amount`, `status`, `shipping_address`, `created_at`, `updated_at` |
| `order_items` | `id`, `order_id` (FK), `product_id`, `product_name_snapshot`, `unit_price_snapshot`, `quantity`, `subtotal` |

Monetary columns use `DECIMAL(19,2)` / `BigDecimal` (never `double`/`float`).

#### Order / OrderItem relationship

- One `Order` has many `OrderItem`s (`CascadeType.ALL`, orphan removal).
- Each item stores **snapshots** (`productNameSnapshot`, `unitPriceSnapshot`) so historical orders remain correct if catalog prices/names change later.

#### Synchronous Product Service communication

```text
Client → API Gateway → order-service
                           │
                           ├─ Feign GET  /api/products/{id}          (via Eureka: product-service)
                           ├─ validate active + stock
                           ├─ Feign POST /api/products/{id}/stock/reserve
                           └─ @Transactional persist order in MySQL
```

- Feign client: `@FeignClient(name = "product-service")` — **no** hardcoded `http://localhost:8082`.
- Product calls happen **outside** the Order DB transaction. Local `@Transactional` covers Order Service MySQL only.
- If reservation fails mid-way, previously reserved stock is compensated via `/stock/release`.
- Fallback / circuit open must **not** create an order; callers receive `503 SERVICE_UNAVAILABLE`.

#### Stock reservation flow

1. For each item: fetch product → require exists, active, quantity > 0, stock ≥ requested.
2. Reserve stock for each item via Product Service.
3. Persist order + items (snapshots, totals).
4. On any failure after reservations: release reserved quantities (best effort).

#### Order status lifecycle

```text
PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
    \         \            /
     └────────┴── CANCELLED (only before SHIPPED)
```

Invalid transitions are rejected. Users may cancel their own orders while status is `PENDING`, `CONFIRMED`, or `PROCESSING`.

#### Resilience4j (Product Service only)

Configured instance name: `productService`

| Pattern | Setting (reasonable defaults) |
| --- | --- |
| Circuit breaker | window 10, min calls 5, failure rate 50%, open wait 10s |
| Retry | max 3 attempts, exponential backoff (200ms × 2) |
| Timeout | Feign connect 2s / read 3s (+ TimeLimiter config 3s) |
| Fallback | throws `ProductServiceUnavailableException` — **never** confirms an order |

Business errors (`ProductNotFound`, `InsufficientStock`, `InactiveProduct`) are ignored by the circuit breaker / retry.

#### APIs

| Method | Path | Auth |
| --- | --- | --- |
| `POST` | `/api/orders` | Authenticated user |
| `GET` | `/api/orders/{id}` | Owner or ADMIN |
| `GET` | `/api/orders` | ADMIN |
| `GET` | `/api/orders/my-orders` | Authenticated user (own orders) |
| `PATCH` | `/api/orders/{id}/status` | ADMIN |
| `POST` | `/api/orders/{id}/cancel` | Owner or ADMIN (pre-shipment) |

OpenAPI UI: `http://localhost:8083/swagger-ui.html`  
Health: `http://localhost:8083/actuator/health`

Gateway route: `/api/orders/**` → `lb://ORDER-SERVICE`

#### How to run

Prerequisites: Config Server, Eureka, MySQL (`mysql-users` container — JDBC creates `ecommerce_orders`), Product Service (Mongo + Redis), and matching `JWT_SECRET`.

```bash
docker compose up -d mysql-users mongodb-catalog redis-cache

export JWT_SECRET='local-dev-jwt-secret-key-min-32-chars'
export DB_USERNAME='root'
export DB_PASSWORD='rootpass'

# start config-server, discovery-server, product-service, then:
cd order-service
mvn spring-boot:run
```

Health check:

```bash
curl http://localhost:8083/actuator/health
```

#### Sample requests

Create order (JWT from User Service login):

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{
    "items": [
      { "productId": "<mongo-product-id>", "quantity": 2 },
      { "productId": "<another-id>", "quantity": 1 }
    ],
    "shippingAddress": "123 Market Street, City"
  }'
```

My orders:

```bash
curl http://localhost:8080/api/orders/my-orders \
  -H "Authorization: Bearer <token>"
```

Update status (ADMIN):

```bash
curl -X PATCH http://localhost:8080/api/orders/<id>/status \
  -H "Authorization: Bearer <admin-token>" \
  -H 'Content-Type: application/json' \
  -d '{"status":"CONFIRMED"}'
```

Cancel:

```bash
curl -X POST http://localhost:8080/api/orders/<id>/cancel \
  -H "Authorization: Bearer <token>"
```

#### Testing Order Service

```bash
cd order-service
mvn clean test
```

Includes Mockito unit tests and MockMvc + H2 integration tests.

### Phase 7 — Payment Service

`payment-service` records payments against orders in MySQL (`ecommerce_payments`), reuses JWT auth, and calls Order Service via Eureka/OpenFeign. Clients never supply amount, userId, currency, paymentReference, or status.

**Status lifecycle:** `PENDING → PROCESSING → PAID → REFUNDED` (also `PENDING|PROCESSING → FAILED`).

**APIs:** `POST /api/payments`, `GET /api/payments/{id}`, `GET /api/payments/my-payments`, `GET /api/payments` (ADMIN), `PATCH /api/payments/{id}/status` (ADMIN).

```bash
export JWT_SECRET='local-dev-jwt-secret-key-min-32-chars'
export DB_USERNAME='root'
export DB_PASSWORD='rootpass'
cd payment-service && mvn spring-boot:run
curl http://localhost:8084/actuator/health
```

Sample create:

```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{"orderId":1,"method":"CASH_ON_DELIVERY"}'
```

## Repository Layout

```text
ecommerce-microservices/
├── config-server/
├── config-repo/
├── discovery-server/
├── api-gateway/
├── user-service/
├── product-service/
├── order-service/
├── payment-service/
├── notification-service/
├── docker-compose.yml
├── README.md
└── .gitignore
```

## Next Step

**Phase 8 — Notification Service**: in-app notifications with JWT ownership, ADMIN create via `NotificationCreator`, Flyway MySQL.

**Phase 9 — Docker**: full `docker-compose.yml` for infra + all 8 apps; see `DEPLOYMENT.md`.

**Phase 10 — Testing + Observability**: Maven suites green; Actuator health + request IDs; Resilience4j health on order/payment.

**Phase 11 — CI/CD**: `.github/workflows/ci.yml` (test, package, compose validate/build); deployment guide in `DEPLOYMENT.md`.
