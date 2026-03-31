# Mainlayer Spring Boot Starter

Official Spring Boot auto-configuration for **Mainlayer** — payment infrastructure for AI agents and modern APIs.

Add one dependency and two properties, and your Spring Boot application gains:

- A **servlet filter** that enforces payment on configured URL patterns
- An **AOP aspect** for the `@RequireMainlayerPayment` annotation
- A **`MainlayerClient`** bean for manual entitlement checks
- Built-in retry logic, caching, and comprehensive error handling
- Full support for reactive (WebFlux) and servlet stacks
- Production-ready logging and monitoring

## Installation

Add the starter to your `pom.xml`:

```xml
<dependency>
    <groupId>fr.mainlayer</groupId>
    <artifactId>mainlayer-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Or with Gradle:

```groovy
implementation 'fr.mainlayer:mainlayer-spring-boot-starter:1.0.0'
```

## Configuration

Add to `application.properties`:

```properties
mainlayer.api-key=ml_live_your_api_key
mainlayer.resource-id=res_your_resource_id
```

Or `application.yml`:

```yaml
mainlayer:
  api-key: ${MAINLAYER_API_KEY}
  resource-id: ${MAINLAYER_RESOURCE_ID}
```

### All Properties

| Property | Default | Description |
|----------|---------|-------------|
| `mainlayer.api-key` | *(required)* | Your Mainlayer API key |
| `mainlayer.resource-id` | *(required)* | The resource to protect |
| `mainlayer.base-url` | `https://api.mainlayer.fr` | API base URL |
| `mainlayer.connect-timeout-ms` | `5000` | HTTP connect timeout |
| `mainlayer.read-timeout-ms` | `10000` | HTTP read timeout |
| `mainlayer.enabled` | `true` | Set to `false` to disable all payment checks |

## Usage

### Option 1: Annotation-based (AOP)

Annotate any Spring-managed method with `@RequireMainlayerPayment`:

```java
import fr.mainlayer.spring.annotation.RequireMainlayerPayment;
import org.springframework.web.bind.annotation.*;

@RestController
public class DataController {

    @GetMapping("/api/premium-data")
    @RequireMainlayerPayment
    public String getPremiumData() {
        return "This content requires a valid Mainlayer entitlement.";
    }
}
```

The caller must supply a valid bearer token in the `Authorization` header.
If the entitlement is missing or expired, the aspect returns `402 Payment Required`.

### Option 2: Servlet Filter (path-based)

The filter is registered automatically and inspects every request.
Configure which paths require payment in `application.properties`:

```properties
mainlayer.protected-paths=/api/premium/**,/api/data/**
```

### Option 3: Manual check

Inject `MainlayerClient` and call `checkEntitlement` directly:

```java
import fr.mainlayer.spring.MainlayerClient;

@Service
public class AccessService {

    private final MainlayerClient mainlayer;

    public AccessService(MainlayerClient mainlayer) {
        this.mainlayer = mainlayer;
    }

    public boolean userCanAccess(String resourceId, String payerId) {
        return mainlayer.checkEntitlement(resourceId, payerId);
    }
}
```

## Demo Application

See [`examples/DemoApplication.java`](examples/DemoApplication.java) for a complete Spring Boot
application demonstrating all three integration styles.

```bash
export MAINLAYER_API_KEY=ml_live_your_api_key
export MAINLAYER_RESOURCE_ID=res_your_resource_id
./mvnw spring-boot:run
```

## Testing

Run tests with Maven:

```bash
./mvnw test
```

All major functionality is covered by 15+ JUnit 5 tests using MockWebServer for HTTP simulation.

## Configuration Properties

All properties are prefixed with `mainlayer.`:

| Property | Default | Description |
|----------|---------|-------------|
| `api-key` | *(required)* | Your Mainlayer API key |
| `resource-id` | *(required)* | Default resource ID to protect |
| `base-url` | `https://api.mainlayer.fr` | API base URL |
| `connect-timeout-ms` | `5000` | HTTP connect timeout |
| `read-timeout-ms` | `10000` | HTTP read timeout |
| `enabled` | `true` | Enable/disable all payment checks |
| `protected-paths` | *(optional)* | Comma-separated URL patterns for servlet filter |
| `cache.enabled` | `true` | Enable entitlement caching |
| `cache.ttl-seconds` | `60` | Cache TTL in seconds |
| `retry.max-attempts` | `2` | Max retry attempts for failed requests |
| `retry.initial-delay-ms` | `100` | Initial delay between retries |
| `logging.enabled` | `false` | Enable request/response logging |

## Advanced Usage

### Custom Entitlement Header

Override the default `Authorization` header:

```yaml
mainlayer:
  entitlement-header: X-Mainlayer-Token
```

### Reactive (WebFlux) Support

The starter works with both servlet and reactive stacks:

```java
@RestController
public class ReactiveController {

    @GetMapping("/api/data")
    @RequireMainlayerPayment
    public Mono<ResponseEntity<String>> getData(
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Mono.just(ResponseEntity.ok("Paid data"));
    }
}
```

### Custom Error Response

Create a `@ControllerAdvice` to customize error responses:

```java
@ControllerAdvice
public class MainlayerErrorHandler {

    @ExceptionHandler(MainlayerPaymentRequiredException.class)
    public ResponseEntity<Map<String, String>> handlePaymentRequired(
            MainlayerPaymentRequiredException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(
            Map.of(
                "error", "payment_required",
                "message", "This resource requires payment.",
                "pay_url", "https://pay.mainlayer.fr"
            )
        );
    }
}
```

### Caching Entitlements

By default, entitlements are cached for 60 seconds:

```yaml
mainlayer:
  cache:
    enabled: true
    ttl-seconds: 300
```

Disable caching for real-time checks:

```yaml
mainlayer:
  cache:
    enabled: false
```

## Troubleshooting

### "Entitlement not found" errors

Ensure the `Authorization` header is being sent:

```bash
curl -H "Authorization: Bearer ent_token_123" http://localhost:8080/api/data
```

### Connection timeouts

Increase timeouts in properties:

```yaml
mainlayer:
  connect-timeout-ms: 10000
  read-timeout-ms: 30000
```

### Enable debug logging

```yaml
logging:
  level:
    fr.mainlayer.spring: DEBUG
```

## Security Notes

- API keys should never be committed; use environment variables
- All communication with Mainlayer is over HTTPS
- Entitlements are cached locally but not logged
- Webhook signatures should be verified before processing
- Use HTTPS in production to protect tokens in transit

## License

MIT License. See LICENSE file for details.

## Support

- Documentation: https://docs.mainlayer.fr
- Issues: https://github.com/mainlayer/mainlayer-spring-boot-starter/issues
- Contact: support@mainlayer.xyz

## Links

- [Mainlayer Documentation](https://docs.mainlayer.fr)
- [Mainlayer Dashboard](https://app.mainlayer.fr)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
