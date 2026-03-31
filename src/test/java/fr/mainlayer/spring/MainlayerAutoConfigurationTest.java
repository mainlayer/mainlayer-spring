package fr.mainlayer.spring;

import fr.mainlayer.spring.aspect.MainlayerPaymentAspect;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link MainlayerAutoConfiguration}.
 *
 * <p>Uses Spring Boot's {@link WebApplicationContextRunner} together with
 * {@link MockWebServer} to exercise the full auto-configuration path without
 * a running server.
 */
class MainlayerAutoConfigurationTest {

    private MockWebServer mockWebServer;

    @BeforeEach
    void startServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        mockWebServer.shutdown();
    }

    // ------------------------------------------------------------------
    // Context wiring
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Auto-configuration registers MainlayerClient bean when api-key is set")
    void registersClientBeanWhenApiKeyPresent() {
        contextRunner()
                .withPropertyValues(
                        "mainlayer.api-key=ml_test_key",
                        "mainlayer.resource-id=res_abc",
                        "mainlayer.base-url=" + baseUrl())
                .run(ctx -> assertThat(ctx).hasSingleBean(MainlayerClient.class));
    }

    @Test
    @DisplayName("Auto-configuration is skipped when api-key is absent")
    void skipsConfigurationWhenNoApiKey() {
        contextRunner()
                .run(ctx -> assertThat(ctx).doesNotHaveBean(MainlayerClient.class));
    }

    @Test
    @DisplayName("Auto-configuration registers MainlayerFilter bean in web context")
    void registersFilterInWebContext() {
        contextRunner()
                .withPropertyValues(
                        "mainlayer.api-key=ml_test_key",
                        "mainlayer.base-url=" + baseUrl())
                .run(ctx -> assertThat(ctx).hasSingleBean(MainlayerFilter.class));
    }

    @Test
    @DisplayName("MainlayerFilter is absent when mainlayer.enabled=false")
    void filterAbsentWhenDisabled() {
        contextRunner()
                .withPropertyValues(
                        "mainlayer.api-key=ml_test_key",
                        "mainlayer.enabled=false",
                        "mainlayer.base-url=" + baseUrl())
                .run(ctx -> assertThat(ctx).doesNotHaveBean(MainlayerFilter.class));
    }

    @Test
    @DisplayName("Auto-configuration registers MainlayerPaymentAspect bean")
    void registersAspectBean() {
        contextRunner()
                .withPropertyValues(
                        "mainlayer.api-key=ml_test_key",
                        "mainlayer.base-url=" + baseUrl())
                .run(ctx -> assertThat(ctx).hasSingleBean(MainlayerPaymentAspect.class));
    }

    @Test
    @DisplayName("User-provided MainlayerClient bean is not replaced")
    void doesNotOverrideUserProvidedClient() {
        contextRunner()
                .withPropertyValues(
                        "mainlayer.api-key=ml_test_key",
                        "mainlayer.base-url=" + baseUrl())
                .withBean("mainlayerClient", MainlayerClient.class,
                        () -> new MainlayerClient(propertiesWithUrl(baseUrl())))
                .run(ctx -> assertThat(ctx).hasSingleBean(MainlayerClient.class));
    }

    @Test
    @DisplayName("Properties are bound from application configuration")
    void propertiesAreBound() {
        contextRunner()
                .withPropertyValues(
                        "mainlayer.api-key=ml_live_abc123",
                        "mainlayer.resource-id=res_xyz",
                        "mainlayer.base-url=" + baseUrl(),
                        "mainlayer.connect-timeout-ms=3000",
                        "mainlayer.read-timeout-ms=8000")
                .run(ctx -> {
                    MainlayerProperties props = ctx.getBean(MainlayerProperties.class);
                    assertThat(props.getApiKey()).isEqualTo("ml_live_abc123");
                    assertThat(props.getResourceId()).isEqualTo("res_xyz");
                    assertThat(props.getConnectTimeoutMs()).isEqualTo(3000);
                    assertThat(props.getReadTimeoutMs()).isEqualTo(8000);
                });
    }

    @Test
    @DisplayName("Default base URL is https://api.mainlayer.xyz")
    void defaultBaseUrlIsMainlayer() {
        contextRunner()
                .withPropertyValues("mainlayer.api-key=ml_test_key")
                .run(ctx -> {
                    MainlayerProperties props = ctx.getBean(MainlayerProperties.class);
                    assertThat(props.getBaseUrl()).isEqualTo("https://api.mainlayer.xyz");
                });
    }

    @Test
    @DisplayName("mainlayer.enabled defaults to true")
    void enabledDefaultsToTrue() {
        contextRunner()
                .withPropertyValues(
                        "mainlayer.api-key=ml_test_key",
                        "mainlayer.base-url=" + baseUrl())
                .run(ctx -> {
                    MainlayerProperties props = ctx.getBean(MainlayerProperties.class);
                    assertThat(props.isEnabled()).isTrue();
                });
    }

    // ------------------------------------------------------------------
    // MainlayerClient — entitlement API calls
    // ------------------------------------------------------------------

    @Test
    @DisplayName("checkEntitlement returns true when API responds 200 entitled=true")
    void checkEntitlementReturnsTrueOnSuccess() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"entitled\": true, \"reason\": \"ok\"}"));

        MainlayerClient client = clientWithMockServer();
        assertThat(client.checkEntitlement("agent_token_abc", "res_test")).isTrue();

        RecordedRequest req = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getPath()).isEqualTo("/v1/entitlements/check");
        assertThat(req.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer ml_test_key");
    }

    @Test
    @DisplayName("checkEntitlement returns false when API responds 200 entitled=false")
    void checkEntitlementReturnsFalseWhenNotEntitled() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"entitled\": false, \"reason\": \"quota_exceeded\"}"));

        MainlayerClient client = clientWithMockServer();
        assertThat(client.checkEntitlement("agent_token_abc", "res_test")).isFalse();
    }

    @Test
    @DisplayName("checkEntitlement returns false on 401 Unauthorized")
    void checkEntitlementReturnsFalseOn401() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(401));

        MainlayerClient client = clientWithMockServer();
        assertThat(client.checkEntitlement("bad_token", "res_test")).isFalse();
    }

    @Test
    @DisplayName("checkEntitlement returns false on 403 Forbidden")
    void checkEntitlementReturnsFalseOn403() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(403));

        MainlayerClient client = clientWithMockServer();
        assertThat(client.checkEntitlement("no_access_token", "res_test")).isFalse();
    }

    @Test
    @DisplayName("checkEntitlement returns false on network error")
    void checkEntitlementReturnsFalseOnNetworkError() throws IOException {
        // Shutdown the server to simulate a network failure
        mockWebServer.shutdown();

        MainlayerClient client = clientWithMockServer();
        assertThat(client.checkEntitlement("token", "res_test")).isFalse();

        // Restart for @AfterEach cleanup
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @Test
    @DisplayName("checkEntitlement throws when no resourceId configured or provided")
    void checkEntitlementThrowsWithoutResourceId() {
        MainlayerProperties props = new MainlayerProperties();
        props.setApiKey("ml_test_key");
        props.setBaseUrl(baseUrl());
        // Intentionally no resourceId

        MainlayerClient client = new MainlayerClient(props);
        assertThatThrownBy(() -> client.checkEntitlement("token", null))
                .isInstanceOf(MainlayerClient.MainlayerConfigurationException.class)
                .hasMessageContaining("resource-id");
    }

    @Test
    @DisplayName("recordUsage returns true on 2xx response")
    void recordUsageReturnsTrueOn200() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        MainlayerClient client = clientWithMockServer();
        assertThat(client.recordUsage("token", "res_test", java.util.Map.of("k", "v"))).isTrue();
    }

    @Test
    @DisplayName("recordUsage returns false on server error")
    void recordUsageReturnsFalseOn500() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        MainlayerClient client = clientWithMockServer();
        assertThat(client.recordUsage("token", "res_test", java.util.Map.of())).isFalse();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private WebApplicationContextRunner contextRunner() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MainlayerAutoConfiguration.class));
    }

    private String baseUrl() {
        return "http://localhost:" + mockWebServer.getPort();
    }

    private MainlayerClient clientWithMockServer() {
        MainlayerProperties props = propertiesWithUrl(baseUrl());
        return new MainlayerClient(props);
    }

    private MainlayerProperties propertiesWithUrl(String url) {
        MainlayerProperties props = new MainlayerProperties();
        props.setApiKey("ml_test_key");
        props.setResourceId("res_test");
        props.setBaseUrl(url);
        return props;
    }
}
