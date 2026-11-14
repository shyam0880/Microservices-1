# Microservices-1

A simple microservices project built with Spring Boot. It has three services — **UserService**, **OrderService**, and an **ApiGateway** — where the gateway is the single entry point for all client requests.

---

## What's going on here?

Instead of building one big application, this project splits responsibilities into smaller services:

- **UserService** manages users (create, read, update, delete).
- **OrderService** manages orders. Before creating an order, it checks with UserService whether the user actually exists.
- **ApiGateway** sits in front of both services. The client talks only to the gateway — it routes the request to the right service internally.

Each service has its own database tables but both UserService and OrderService connect to the same PostgreSQL database (`microservices`).

---

## Services

### UserService
- Runs on **port 8081**
- Handles everything related to users

| Method | Endpoint | What it does |
|--------|----------|--------------|
| GET | `/api/users` | Get all users |
| GET | `/api/users/{id}` | Get a user by ID |
| GET | `/api/users/{id}/exists` | Check if a user exists (used by OrderService) |
| POST | `/api/users` | Create a new user |
| PUT | `/api/users/{id}` | Update a user |
| DELETE | `/api/users/{id}` | Delete a user |

**User fields:** `name`, `phoneNo`, `address`

---

### OrderService
- Runs on **port 8082**
- Handles everything related to orders
- Calls UserService to validate the user before saving an order

| Method | Endpoint | What it does |
|--------|----------|--------------|
| GET | `/api/orders` | Get all orders |
| GET | `/api/orders/{id}` | Get an order by ID |
| POST | `/api/orders` | Create a new order |
| DELETE | `/api/orders/{id}` | Delete an order |

**Order fields:** `userId`, `productName`, `quantity`, `totalPrice`

---

### ApiGateway
- Runs on **port 8080**
- The only port the client ever calls
- Routes requests to the correct service based on the URL path

| Path | Routes to |
|------|-----------|
| `/api/users/**` | UserService on port 8081 |
| `/api/orders/**` | OrderService on port 8082 |

So instead of calling `localhost:8081/api/users`, the client just calls `localhost:8080/api/users` and the gateway handles the rest.

---

## Service Communication Flow

```
[ Client ]
    |
    | Any request (e.g. POST /api/orders)
    |
[ ApiGateway :8080 ]
    |
    |-- /api/users/** --> [ UserService :8081 ]
    |
    |-- /api/orders/** --> [ OrderService :8082 ]
                               |
                               | GET /api/users/{userId}/exists
                               |
                           [ UserService :8081 ]
                               |
                               |-- User found?
                                    |-- YES  --> Order gets saved
                                    |-- NO   --> 400 Bad Request
                                    |-- DOWN --> 503 Service Unavailable
```

---

## How the services talk to each other

When you create an order, OrderService sends a request directly to UserService at:
```
GET http://localhost:8081/api/users/{userId}/exists
```
If the user doesn't exist, the order is rejected with a `400 Bad Request`. If UserService is down, you get a `503 Service Unavailable`.

---

## WebClient Setup (OrderService)

OrderService uses Spring WebFlux's `WebClient` to make HTTP calls to UserService. Here's how it's wired up:

**1. Dependency — `pom.xml`**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```
This brings in `WebClient`. Without this, the app won't even start.

**2. Config — `WebConfig.java`**
```java
@Configuration
public class WebConfig {
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
```
Registers a shared `WebClient.Builder` bean so it can be injected anywhere.

**3. Base URL — `application.yaml`**
```yaml
user-service:
  base-url: http://localhost:8081
```
The URL is kept in config so you can change it without touching code.

**4. Client — `UserServiceClient.java`**
```java
@Component
public class UserServiceClient {

    private final WebClient webClient;

    public UserServiceClient(WebClient.Builder webClientBuilder,
                             @Value("${user-service.base-url}") String userServiceBaseUrl) {
        this.webClient = webClientBuilder.baseUrl(userServiceBaseUrl).build();
    }

    public boolean userExists(Long userId) {
        Boolean exists = webClient.get()
                .uri("/api/users/{id}/exists", userId)
                .retrieve()
                .bodyToMono(Boolean.class)
                .timeout(Duration.ofSeconds(5))
                .block();
        return Boolean.TRUE.equals(exists);
    }
}
```
- Injects the builder and sets the base URL once at construction time.
- `.timeout(5s)` — if UserService doesn't respond in 5 seconds, it throws `UserServiceUnavailableException`.
- `.block()` — makes the async call synchronous since OrderService doesn't use reactive programming end-to-end.

**5. Used in — `OrderServiceImplementation.java`**
```java
public OrderResponse createOrder(Order order) {
    if (!userServiceClient.userExists(order.getUserId())) {
        throw new UserNotFoundException("User with id " + order.getUserId() + " does not exist");
    }
    // save and return order
}
```

---

## ApiGateway Setup

ApiGateway uses Spring Cloud Gateway (WebFlux-based) to route incoming requests.

**1. Dependency — `pom.xml`**
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
</dependency>
```

**2. Routes — `application.yaml`**
```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: user-service-route
              uri: http://localhost:8081
              predicates:
                - Path=/api/users/**
            - id: order-service-route
              uri: http://localhost:8082
              predicates:
                - Path=/api/orders/**
server:
  port: 8080
```
No Java code needed — routing is fully config-driven. Each route has an `id`, a target `uri`, and a `predicate` that matches the URL path.

---

## Resilience4j Setup (ApiGateway)

When a downstream service (UserService or OrderService) is down or too slow, the gateway doesn't just hang or throw a raw error — it trips a circuit breaker and returns a clean fallback response immediately.

**1. Dependency — `ApiGateway/pom.xml`**
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
</dependency>
```
This wires Resilience4j into the gateway's reactive pipeline.

**2. Circuit Breaker filters + fallback routes — `application.yaml`**
```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: user-service-route
              uri: http://localhost:8081
              predicates:
                - Path=/api/users/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: userServiceCB
                    fallbackUri: forward:/fallback/user-service

            - id: order-service-route
              uri: http://localhost:8082
              predicates:
                - Path=/api/orders/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: orderServiceCB
                    fallbackUri: forward:/fallback/order-service

            # Catch-all — must be last, routes are matched in order
            - id: catch-all-fallback-route
              uri: forward:/fallback/service-not-found
              predicates:
                - Path=/api/**
```
Each route has its own named circuit breaker. When it trips, the gateway forwards the request internally to the matching `/fallback/...` endpoint instead of the real service.

**3. Resilience4j config — `application.yaml`**
```yaml
resilience4j:
  timelimiter:
    configs:
      default:
        timeout-duration: 10s
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
```
- **timeout-duration** — if a service takes more than 10s, it counts as a failure.
- **sliding-window-size** — looks at the last 10 calls to decide health.
- **minimum-number-of-calls** — won't trip until at least 5 calls have been made.
- **failure-rate-threshold** — if 50% or more of calls fail, the circuit opens.
- **wait-duration-in-open-state** — stays open for 10s before trying again (half-open state).

**4. ErrorResponse DTO — `ErrorResponse.java`**
```java
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private String path;
    private LocalDateTime timestamp;
}
```
A consistent response shape for all fallback responses, same pattern used in UserService and OrderService.

**5. FallbackController — `FallbackController.java`**
```java
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/user-service")
    public ResponseEntity<ErrorResponse> userServiceFallback(ServerWebExchange exchange) {
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "User Service is currently unavailable. Please try again later.", exchange);
    }

    @RequestMapping("/order-service")
    public ResponseEntity<ErrorResponse> orderServiceFallback(ServerWebExchange exchange) {
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "Order Service is currently unavailable. Please try again later.", exchange);
    }

    @RequestMapping("/service-not-found")
    public ResponseEntity<ErrorResponse> serviceNotFoundFallback(ServerWebExchange exchange) {
        return build(HttpStatus.NOT_FOUND,
                "No service is available for this request path.", exchange);
    }
}
```
- Uses `@RequestMapping` (not `@GetMapping`) so it handles GET, POST, PUT, DELETE — whatever the original request method was.
- Reads the original client path from `GATEWAY_ORIGINAL_REQUEST_URL_ATTR` so the `path` field in the response shows what the client actually called, not `/fallback/...`.

---

## Tech Stack

- Java 21
- Spring Boot 4.1.2
- Spring Cloud 2025.1.4
- Spring Data JPA
- Spring Cloud Gateway (WebFlux)
- Resilience4j (Circuit Breaker + Time Limiter)
- Spring WebFlux (`WebClient` for inter-service calls)
- PostgreSQL

---

## Running the project

**Prerequisites:** Java 21, Maven, PostgreSQL running locally

1. Create a database named `microservices` in PostgreSQL.

2. Both services use these default credentials — update `application.yaml` in each service if yours differ:
   ```
   username: postgres
   password: Hello@123
   ```

3. Start all three services (order matters):
   ```bash
   cd UserService && ./mvnw spring-boot:run
   cd OrderService && ./mvnw spring-boot:run
   cd ApiGateway && ./mvnw spring-boot:run
   ```

4. All requests go through the gateway on port 8080:
   ```
   GET  http://localhost:8080/api/users
   POST http://localhost:8080/api/orders
   ```

---

## Error handling

Both services return a consistent error response:

```json
{
  "status": 404,
  "error": "Order Not Found",
  "message": "Order with id 5 not found",
  "path": "/api/orders/5"
}
```

| Scenario | HTTP Status |
|----------|-------------|
| User or order not found | 404 |
| Creating order with invalid userId | 400 |
| UserService is down | 503 |
| UserService returned an error | 502 |
| Anything unexpected | 500 |

---

## Progress

| Step | What was added | Code at this step | What changed |
|------|---------------|-------------------|--------------|
| 1 | UserService, OrderService, WebClient call, error handling | [view](https://github.com/shyam0880/Microservices-1/tree/step-1-user-order-communication) | [first commit to step 1](https://github.com/shyam0880/Microservices-1/compare/6dff029...step-1-user-order-communication) |
| 2 | API Gateway — routes all client traffic to the right service | [view](https://github.com/shyam0880/Microservices-1/tree/step-2-api-gateway) | [step 1 to step 2](https://github.com/shyam0880/Microservices-1/compare/step-1-user-order-communication...step-2-api-gateway) |
| 3 | Resilience4j — circuit breaker, time limiter, and fallback responses in the gateway | [view](https://github.com/shyam0880/Microservices-1/tree/step-3-gateway-resilience) | [step 2 to step 3](https://github.com/shyam0880/Microservices-1/compare/step-2-api-gateway...step-3-gateway-resilience) |
