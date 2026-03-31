package fr.mainlayer.spring.demo;

import fr.mainlayer.spring.MainlayerClient;
import fr.mainlayer.spring.annotation.RequireMainlayerPayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Example Spring Boot application demonstrating the Mainlayer starter.
 *
 * <p>To run this demo:
 * <pre>
 * export MAINLAYER_API_KEY=ml_live_your_api_key
 * export MAINLAYER_RESOURCE_ID=res_your_resource_id
 * ./mvnw spring-boot:run
 * </pre>
 *
 * <p>Then call a paid endpoint:
 * <pre>
 * curl -H "Authorization: Bearer &lt;caller_token&gt;" http://localhost:8080/api/data
 * </pre>
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    // -------------------------------------------------------------------------
    // Example REST controller with payment-gated endpoints
    // -------------------------------------------------------------------------

    @RestController
    @RequestMapping("/api")
    static class DemoController {

        private static final Logger log = LoggerFactory.getLogger(DemoController.class);

        private final MainlayerClient mainlayer;

        DemoController(MainlayerClient mainlayer) {
            this.mainlayer = mainlayer;
        }

        /**
         * Free endpoint — no payment required.
         * Returns a welcome message and basic information about the API.
         */
        @GetMapping("/hello")
        public ResponseEntity<Map<String, String>> hello() {
            return ResponseEntity.ok(Map.of(
                "message", "Welcome! Paid endpoints require a valid Mainlayer entitlement.",
                "paid_endpoint", "/api/data",
                "docs", "https://docs.mainlayer.xyz"
            ));
        }

        /**
         * Paid endpoint using the {@code @RequireMainlayerPayment} annotation.
         *
         * <p>The Mainlayer AOP aspect intercepts calls to this method and
         * verifies the caller's entitlement via the Authorization header.
         * If the entitlement is missing or expired, a 402 Payment Required
         * response is returned before the method body executes.
         */
        @GetMapping("/data")
        @RequireMainlayerPayment
        public ResponseEntity<Map<String, Object>> getPaidData() {
            log.info("Serving paid data to authorised caller");
            return ResponseEntity.ok(Map.of(
                "data", "Premium data payload — you have a valid entitlement!",
                "timestamp", System.currentTimeMillis(),
                "source", "mainlayer-demo"
            ));
        }

        /**
         * Another paid endpoint demonstrating path variable extraction.
         *
         * <p>The resource ID can also be set per-request via
         * {@code @RequireMainlayerPayment(resourceId = "res_specific_id")}.
         */
        @GetMapping("/items/{itemId}")
        @RequireMainlayerPayment
        public ResponseEntity<Map<String, Object>> getItem(@PathVariable String itemId) {
            log.info("Fetching paid item: {}", itemId);
            return ResponseEntity.ok(Map.of(
                "itemId", itemId,
                "name", "Premium Item #" + itemId,
                "price", 9.99
            ));
        }

        /**
         * Demonstrates manual entitlement verification using the
         * {@link MainlayerClient} directly — useful when you need custom logic.
         */
        @PostMapping("/verify")
        public ResponseEntity<Map<String, Object>> verifyManually(
                @RequestHeader(value = "X-Payer-Id", required = false) String payerId,
                @RequestHeader(value = "X-Resource-Id", required = false) String resourceId) {

            if (payerId == null || resourceId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "X-Payer-Id and X-Resource-Id headers are required"
                ));
            }

            boolean hasAccess = mainlayer.checkEntitlement(resourceId, payerId);
            return ResponseEntity.ok(Map.of(
                "payerId", payerId,
                "resourceId", resourceId,
                "hasAccess", hasAccess
            ));
        }
    }
}
