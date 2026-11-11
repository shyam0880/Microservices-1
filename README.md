# Microservices-1

A simple microservices project built with Spring Boot. It has two independent services — **UserService** and **OrderService** — that communicate with each other over HTTP.

---

## What's going on here?

Instead of building one big application, this project splits responsibilities into two smaller services:

- **UserService** manages users (create, read, update, delete).
- **OrderService** manages orders. Before creating an order, it checks with UserService whether the user actually exists.

Each service has its own database tables but both connect to the same PostgreSQL database (`microservices`). They run on different ports so they don't conflict.

---

## Services

### UserService
- Runs on **port 8090**
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
- Runs on **port 8080**
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

## Service Communication Flow

```
[ Client ]
    |
    | POST /api/orders
    |
[ OrderService :8080 ]
    |
    | GET /api/users/{userId}/exists
    |
[ UserService :8090 ]
    |
    |-- User found?
         |-- YES --> Order gets saved
         |-- NO  --> 400 Bad Request
         |-- DOWN --> 503 Service Unavailable
```

---

## How the services talk to each other

When you create an order, OrderService sends a request to UserService at:
```
GET http://localhost:8090/api/users/{userId}/exists
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
  base-url: http://localhost:8090
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

## Tech Stack

- Java 21
- Spring Boot 4.1.2
- Spring Data JPA
- Spring WebFlux (`WebClient` for inter-service calls)
- PostgreSQL

---

## Running the project

**Prerequisites:** Java 21, Maven, PostgreSQL running locally

1. Create a database named `microservices` in PostgreSQL.

2. Both services use these default credentials — update `application.yaml` in each service if yours differ:
   ```
   username: postgres
   password: password
   ```

3. Start UserService first (OrderService depends on it):
   ```bash
   cd UserService
   ./mvnw spring-boot:run
   ```

4. Then start OrderService:
   ```bash
   cd OrderService
   ./mvnw spring-boot:run
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
