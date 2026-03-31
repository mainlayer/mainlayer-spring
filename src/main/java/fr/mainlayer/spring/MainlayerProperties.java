package fr.mainlayer.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the Mainlayer Spring Boot starter.
 *
 * <p>Bind these in {@code application.properties} or {@code application.yml}:
 * <pre>
 * spring.mainlayer.api-key=ml_live_...
 * spring.mainlayer.resource-id=res_abc123
 * spring.mainlayer.base-url=https://api.mainlayer.fr   # optional override
 * spring.mainlayer.connect-timeout-ms=5000
 * spring.mainlayer.read-timeout-ms=10000
 * spring.mainlayer.enabled=true
 * </pre>
 */
@ConfigurationProperties(prefix = "mainlayer")
public class MainlayerProperties {

    /**
     * Mainlayer API key.  Obtain from the Mainlayer dashboard.
     * Example: {@code ml_live_xxxxxxxxxxxxxxxxxxx}
     */
    private String apiKey;

    /**
     * Resource identifier that represents the protected service or endpoint
     * within the Mainlayer platform.
     */
    private String resourceId;

    /**
     * Base URL for the Mainlayer API.  Override only when using a private
     * deployment or a staging environment.
     */
    @DefaultValue("https://api.mainlayer.fr")
    private String baseUrl;

    /**
     * HTTP connection timeout in milliseconds.
     */
    @DefaultValue("5000")
    private int connectTimeoutMs;

    /**
     * HTTP read timeout in milliseconds.
     */
    @DefaultValue("10000")
    private int readTimeoutMs;

    /**
     * Global kill-switch.  Set to {@code false} to disable the filter and
     * aspect entirely (useful in local development without a paid key).
     */
    @DefaultValue("true")
    private boolean enabled;

    /**
     * Comma-separated list of URL patterns to enforce payment on.
     * Example: {@code /api/premium/**,/api/data/**}
     */
    private String protectedPaths;

    /**
     * Custom header name for entitlement tokens. Default: Authorization
     */
    @DefaultValue("Authorization")
    private String entitlementHeader;

    /**
     * Cache configuration
     */
    private CacheConfig cache = new CacheConfig();

    /**
     * Retry configuration
     */
    private RetryConfig retry = new RetryConfig();

    /**
     * Logging configuration
     */
    private LoggingConfig logging = new LoggingConfig();

    // --- Getters and setters ---

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProtectedPaths() {
        return protectedPaths;
    }

    public void setProtectedPaths(String protectedPaths) {
        this.protectedPaths = protectedPaths;
    }

    public String getEntitlementHeader() {
        return entitlementHeader;
    }

    public void setEntitlementHeader(String entitlementHeader) {
        this.entitlementHeader = entitlementHeader;
    }

    public CacheConfig getCache() {
        return cache;
    }

    public void setCache(CacheConfig cache) {
        this.cache = cache;
    }

    public RetryConfig getRetry() {
        return retry;
    }

    public void setRetry(RetryConfig retry) {
        this.retry = retry;
    }

    public LoggingConfig getLogging() {
        return logging;
    }

    public void setLogging(LoggingConfig logging) {
        this.logging = logging;
    }

    // --- Inner config classes ---

    public static class CacheConfig {
        @DefaultValue("true")
        private boolean enabled;

        @DefaultValue("60")
        private int ttlSeconds;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    public static class RetryConfig {
        @DefaultValue("2")
        private int maxAttempts;

        @DefaultValue("100")
        private int initialDelayMs;

        @DefaultValue("2")
        private double backoffMultiplier;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(int initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }
    }

    public static class LoggingConfig {
        @DefaultValue("false")
        private boolean enabled;

        @DefaultValue("INFO")
        private String level;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }
    }
}
