# Mainlayer Spring Boot Starter

Spring Boot auto-configuration for [Mainlayer](https://mainlayer.xyz) — payment infrastructure for apps and AI agents.

Add one dependency and two properties, and your Spring Boot application gains:

- A **servlet filter** that enforces payment on configured URL patterns
- An **AOP aspect** for the `@RequireMainlayerPayment` annotation
- A **`MainlayerClient`** bean for manual entitlement checks

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
| `mainlayer.base-url` | `https://api.mainlayer.xyz` | API base URL |
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

## Links

- [Mainlayer Documentation](https://docs.mainlayer.xyz)
- [Mainlayer Dashboard](https://app.mainlayer.xyz)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
