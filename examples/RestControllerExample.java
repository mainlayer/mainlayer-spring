package fr.mainlayer.spring.demo;

import fr.mainlayer.spring.MainlayerClient;
import fr.mainlayer.spring.annotation.RequireMainlayerPayment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Example REST controller demonstrating all three Mainlayer integration patterns:
 * 1. Annotation-based protection (@RequireMainlayerPayment)
 * 2. Manual entitlement checks
 * 3. Custom resource ID handling
 */
@RestController
@RequestMapping("/api")
public class RestControllerExample {

    private final MainlayerClient mainlayerClient;

    public RestControllerExample(MainlayerClient mainlayerClient) {
        this.mainlayerClient = mainlayerClient;
    }

    // ===== Pattern 1: Annotation-Based Protection =====

    /**
     * GET /api/data
     *
     * Protected by @RequireMainlayerPayment annotation. The aspect intercepts
     * the call and verifies the Authorization header before allowing execution.
     *
     * Usage:
     *   curl -H "Authorization: Bearer ent_token_123" http://localhost:8080/api/data
     */
    @GetMapping("/data")
    @RequireMainlayerPayment
    public ResponseEntity<Map<String, Object>> getPaidData() {
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "data", "Premium content restricted to paying customers",
            "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * GET /api/items/{itemId}
     *
     * Protected endpoint with path parameters. Resource ID comes from
     * application configuration (mainlayer.resource-id).
     */
    @GetMapping("/items/{itemId}")
    @RequireMainlayerPayment
    public ResponseEntity<Map<String, Object>> getItem(@PathVariable String itemId) {
        return ResponseEntity.ok(Map.of(
            "itemId", itemId,
            "name", "Premium Item #" + itemId,
            "price", 9.99,
            "purchaseRequired", true
        ));
    }

    /**
     * POST /api/compute
     *
     * Complex protected endpoint accepting a request body.
     */
    @PostMapping("/compute")
    @RequireMainlayerPayment
    public ResponseEntity<Map<String, Object>> compute(@RequestBody ComputeRequest request) {
        int result = request.a() + request.b();
        return ResponseEntity.ok(Map.of(
            "input", request,
            "result", result,
            "operationTime", "12ms"
        ));
    }

    // ===== Pattern 2: Manual Verification =====

    /**
     * POST /api/verify
     *
     * Manually check entitlement using the MainlayerClient directly.
     * Useful when you need custom business logic around payment checks.
     *
     * Body: { "caller_token": "ent_...", "resource_id": "res_..." }
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyManually(@RequestBody VerifyRequest request) {
        try {
            boolean entitled = mainlayerClient.checkEntitlement(
                request.callerToken(),
                request.resourceId()
            );

            if (!entitled) {
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of(
                    "error", "payment_required",
                    "message", "Caller is not entitled to access this resource"
                ));
            }

            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "Entitlement verified"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "verification_failed",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/check-access?token=&resource=
     *
     * Query-parameter based entitlement check.
     */
    @GetMapping("/check-access")
    public ResponseEntity<Map<String, Object>> checkAccess(
            @RequestParam String token,
            @RequestParam(required = false) String resource) {
        try {
            boolean hasAccess = mainlayerClient.checkEntitlement(token, resource);
            return ResponseEntity.ok(Map.of(
                "hasAccess", hasAccess,
                "token", maskToken(token)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    // ===== Pattern 3: Custom Resource Handling =====

    /**
     * GET /api/premium/feature-a
     *
     * Checks entitlement for a specific resource (feature-a).
     * Could check different resources based on request path or headers.
     */
    @GetMapping("/premium/feature-a")
    public ResponseEntity<Map<String, Object>> featureA(
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "error", "missing_token"
            ));
        }

        boolean hasAccess = mainlayerClient.checkEntitlement(token, "feature-a");

        if (!hasAccess) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of(
                "error", "payment_required",
                "feature", "feature-a",
                "paymentUrl", "https://pay.mainlayer.fr"
            ));
        }

        return ResponseEntity.ok(Map.of(
            "feature", "feature-a",
            "data", "Exclusive feature A content"
        ));
    }

    /**
     * GET /api/premium/feature-b
     *
     * Similar to feature-a but checks entitlement for feature-b.
     */
    @GetMapping("/premium/feature-b")
    public ResponseEntity<Map<String, Object>> featureB(
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "error", "missing_token"
            ));
        }

        boolean hasAccess = mainlayerClient.checkEntitlement(token, "feature-b");

        if (!hasAccess) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of(
                "error", "payment_required",
                "feature", "feature-b"
            ));
        }

        return ResponseEntity.ok(Map.of(
            "feature", "feature-b",
            "data", "Exclusive feature B content"
        ));
    }

    // ===== Utility Endpoints =====

    /**
     * GET /api/health
     *
     * Health check including Mainlayer API connectivity.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "service", "mainlayer-demo",
            "mainlayerConnected", mainlayerClient.healthCheck(),
            "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * GET /api/hello
     *
     * Unprotected endpoint for testing.
     */
    @GetMapping("/hello")
    public ResponseEntity<Map<String, String>> hello() {
        return ResponseEntity.ok(Map.of(
            "message", "Welcome! This endpoint is public.",
            "protectedEndpoints", "/api/data, /api/items/*, /api/compute"
        ));
    }

    // ===== Error Handling =====

    /**
     * Handle payment required errors globally.
     */
    @ExceptionHandler
    public ResponseEntity<Map<String, String>> handlePaymentRequired(
            fr.mainlayer.spring.MainlayerClient.MainlayerPaymentRequiredException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of(
            "error", "payment_required",
            "message", e.getMessage(),
            "paymentUrl", "https://pay.mainlayer.fr"
        ));
    }

    // ===== Request/Response Types =====

    record ComputeRequest(int a, int b) {}

    record VerifyRequest(String callerToken, String resourceId) {}

    // ===== Helpers =====

    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
