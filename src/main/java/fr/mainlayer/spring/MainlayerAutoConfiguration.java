package fr.mainlayer.spring;

import fr.mainlayer.spring.aspect.MainlayerPaymentAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestClient;

import jakarta.servlet.Filter;

/**
 * Spring Boot auto-configuration for the Mainlayer payment platform.
 *
 * <p>This class wires up all Mainlayer infrastructure beans automatically when
 * the starter is on the classpath.  Application code may override any bean by
 * declaring its own bean of the same type.
 *
 * <h2>Beans registered</h2>
 * <ul>
 *   <li>{@link MainlayerClient} — the HTTP client talking to the Mainlayer API</li>
 *   <li>{@link MainlayerFilter} (web apps only) — servlet filter for path-level
 *       entitlement enforcement</li>
 *   <li>{@link MainlayerPaymentAspect} (AOP apps only) — aspect for
 *       {@code @RequireMainlayerPayment}</li>
 * </ul>
 *
 * <h2>Minimal configuration</h2>
 * <pre>
 * mainlayer.api-key=ml_live_xxxxxxxxxxxxxxxxxxx
 * mainlayer.resource-id=res_abc123
 * </pre>
 *
 * <h2>Disable entirely</h2>
 * <pre>
 * mainlayer.enabled=false
 * </pre>
 */
@AutoConfiguration
@EnableConfigurationProperties(MainlayerProperties.class)
@ConditionalOnProperty(prefix = "mainlayer", name = "api-key")
public class MainlayerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MainlayerAutoConfiguration.class);

    // ------------------------------------------------------------------
    // Core client bean
    // ------------------------------------------------------------------

    /**
     * Creates the {@link MainlayerClient} bean using the configured properties.
     * Applications may override this bean by declaring their own
     * {@link MainlayerClient} bean.
     */
    @Bean
    @ConditionalOnMissingBean(MainlayerClient.class)
    public MainlayerClient mainlayerClient(MainlayerProperties properties) {
        log.info("Configuring MainlayerClient: baseUrl={}, resourceId={}",
                properties.getBaseUrl(), properties.getResourceId());
        return new MainlayerClient(properties);
    }

    // ------------------------------------------------------------------
    // Servlet filter (traditional / MVC web apps)
    // ------------------------------------------------------------------

    /**
     * Web-only sub-configuration that registers the {@link MainlayerFilter} as
     * a servlet filter with the highest precedence.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(Filter.class)
    @ConditionalOnProperty(prefix = "mainlayer", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class MainlayerFilterConfiguration {

        @Bean
        @ConditionalOnMissingBean(MainlayerFilter.class)
        public MainlayerFilter mainlayerFilter(MainlayerClient client,
                                               MainlayerProperties properties) {
            return MainlayerFilter.builder(client, properties).build();
        }

        @Bean
        public FilterRegistrationBean<MainlayerFilter> mainlayerFilterRegistration(
                MainlayerFilter filter) {
            FilterRegistrationBean<MainlayerFilter> registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
            registration.addUrlPatterns("/*");
            registration.setName("mainlayerFilter");
            log.debug("Registered MainlayerFilter at order={}", registration.getOrder());
            return registration;
        }
    }

    // ------------------------------------------------------------------
    // AOP aspect (any Spring app with AspectJ on the classpath)
    // ------------------------------------------------------------------

    /**
     * AOP sub-configuration that creates the {@link MainlayerPaymentAspect}.
     * Activated only when AspectJ is on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnProperty(prefix = "mainlayer", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class MainlayerAspectConfiguration {

        @Bean
        @ConditionalOnMissingBean(MainlayerPaymentAspect.class)
        public MainlayerPaymentAspect mainlayerPaymentAspect(MainlayerClient client) {
            log.debug("Registering MainlayerPaymentAspect");
            return new MainlayerPaymentAspect(client);
        }
    }

    // ------------------------------------------------------------------
    // RestClient customiser (exposes the builder for tests / overrides)
    // ------------------------------------------------------------------

    /**
     * Exposes a pre-configured {@link RestClient.Builder} scoped to the
     * Mainlayer base URL so that advanced consumers can extend it (e.g. add
     * custom interceptors).
     */
    @Bean("mainlayerRestClientBuilder")
    @ConditionalOnMissingBean(name = "mainlayerRestClientBuilder")
    public RestClient.Builder mainlayerRestClientBuilder(MainlayerProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl());
    }
}
