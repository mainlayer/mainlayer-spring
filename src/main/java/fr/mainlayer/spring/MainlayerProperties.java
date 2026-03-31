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
 * spring.mainlayer.base-url=https://api.mainlayer.xyz   # optional override
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
    @DefaultValue("https://api.mainlayer.xyz")
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
}
