package fr.mainlayer.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * HTTP client for the Mainlayer payment API.
 *
 * <p>This client wraps Spring's {@link RestClient} to communicate with
 * {@code https://api.mainlayer.xyz}.  It handles authentication, request
 * serialisation, and translates API responses into typed result objects.
 *
 * <p>All operations are synchronous.  For reactive stacks inject the
 * optional {@code WebClient}-based variant instead.
 */
public class MainlayerClient {

    private static final Logger log = LoggerFactory.getLogger(MainlayerClient.class);

    private final RestClient restClient;
    private final MainlayerProperties properties;

    public MainlayerClient(MainlayerProperties properties) {
        this.properties = properties;
        this.restClient = buildRestClient(properties);
    }

    /**
     * Package-private constructor that accepts a pre-built {@link RestClient}.
     * Useful in tests where the base URL is pointed at {@code MockWebServer}.
     */
    MainlayerClient(MainlayerProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Checks whether the caller identified by {@code callerToken} is entitled
     * to access the resource described by {@code resourceId}.
     *
     * @param callerToken  the bearer token supplied by the calling agent
     * @param resourceId   the Mainlayer resource ID to check entitlement for;
     *                     falls back to the configured default when {@code null}
     * @return {@code true} if the entitlement check passes
     */
    public boolean checkEntitlement(String callerToken, String resourceId) {
        String resource = resolveResourceId(resourceId);
        log.debug("Checking entitlement: resource={}", resource);

        try {
            EntitlementRequest body = new EntitlementRequest(callerToken, resource);
            ResponseEntity<EntitlementResponse> response = restClient
                    .post()
                    .uri("/v1/entitlements/check")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .toEntity(EntitlementResponse.class);

            boolean entitled = response.getStatusCode() == HttpStatus.OK
                    && response.getBody() != null
                    && Boolean.TRUE.equals(response.getBody().entitled());

            log.debug("Entitlement result: resource={}, entitled={}", resource, entitled);
            return entitled;

        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Mainlayer API key is invalid or expired");
            return false;
        } catch (HttpClientErrorException.Forbidden e) {
            log.debug("Entitlement denied by API: resource={}", resource);
            return false;
        } catch (Exception e) {
            log.error("Entitlement check failed unexpectedly: resource={}", resource, e);
            return false;
        }
    }

    /**
     * Records a usage event for billing purposes.
     *
     * @param callerToken the bearer token of the calling agent
     * @param resourceId  the resource being consumed (nullable — uses default)
     * @param metadata    optional key/value pairs attached to the event
     * @return {@code true} if the event was accepted by the API
     */
    public boolean recordUsage(String callerToken, String resourceId, Map<String, String> metadata) {
        String resource = resolveResourceId(resourceId);
        log.debug("Recording usage event: resource={}", resource);

        try {
            UsageEventRequest body = new UsageEventRequest(callerToken, resource, metadata);
            ResponseEntity<Void> response = restClient
                    .post()
                    .uri("/v1/usage")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            log.error("Failed to record usage event: resource={}", resource, e);
            return false;
        }
    }

    /**
     * Returns the resolved base URL (useful for health checks and diagnostics).
     */
    public String getBaseUrl() {
        return properties.getBaseUrl();
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private String resolveResourceId(String resourceId) {
        if (resourceId != null && !resourceId.isBlank()) {
            return resourceId;
        }
        String defaultId = properties.getResourceId();
        if (defaultId == null || defaultId.isBlank()) {
            throw new MainlayerConfigurationException(
                    "No resourceId provided and mainlayer.resource-id is not configured");
        }
        return defaultId;
    }

    private static RestClient buildRestClient(MainlayerProperties props) {
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ------------------------------------------------------------------
    // Inner record types (request / response)
    // ------------------------------------------------------------------

    record EntitlementRequest(String callerToken, String resourceId) {}

    record EntitlementResponse(boolean entitled, String reason) {}

    record UsageEventRequest(String callerToken, String resourceId, Map<String, String> metadata) {}

    // ------------------------------------------------------------------
    // Exception types
    // ------------------------------------------------------------------

    /**
     * Thrown when the Mainlayer client cannot operate due to missing or
     * invalid configuration.
     */
    public static class MainlayerConfigurationException extends RuntimeException {
        public MainlayerConfigurationException(String message) {
            super(message);
        }
    }

    /**
     * Thrown by the filter / aspect when entitlement is denied.
     */
    public static class MainlayerPaymentRequiredException extends RuntimeException {
        public MainlayerPaymentRequiredException(String message) {
            super(message);
        }

        public MainlayerPaymentRequiredException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
