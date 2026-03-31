package fr.mainlayer.spring;

import fr.mainlayer.spring.MainlayerClient.MainlayerPaymentRequiredException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Servlet filter that checks Mainlayer payment entitlement before allowing a
 * request to reach protected endpoints.
 *
 * <p>The filter inspects the {@code Authorization} header (scheme
 * {@code Bearer}), calls the Mainlayer entitlement API, and returns
 * {@code 402 Payment Required} when the check fails.
 *
 * <h2>Protected path configuration</h2>
 * <p>By default <em>all</em> paths are protected.  Supply an include/exclude
 * list via {@link Builder} or Spring properties:
 *
 * <pre>{@code
 * mainlayer.filter.included-paths=/api/premium/**,/api/v2/**
 * mainlayer.filter.excluded-paths=/api/public/**,/actuator/**
 * }</pre>
 *
 * <h2>Disable globally</h2>
 * <pre>{@code
 * mainlayer.enabled=false
 * }</pre>
 */
public class MainlayerFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MainlayerFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final MainlayerClient client;
    private final MainlayerProperties properties;
    private final List<String> includedPaths;
    private final List<String> excludedPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private MainlayerFilter(Builder builder) {
        this.client = builder.client;
        this.properties = builder.properties;
        this.includedPaths = builder.includedPaths;
        this.excludedPaths = builder.excludedPaths;
    }

    // ------------------------------------------------------------------
    // OncePerRequestFilter
    // ------------------------------------------------------------------

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();

        // Explicit exclusions take precedence
        for (String pattern : excludedPaths) {
            if (pathMatcher.match(pattern, path)) {
                log.trace("Mainlayer filter skipped (excluded): {}", path);
                return true;
            }
        }

        // If includedPaths is configured, only filter matching paths
        if (!includedPaths.isEmpty()) {
            for (String pattern : includedPaths) {
                if (pathMatcher.match(pattern, path)) {
                    return false; // should filter this path
                }
            }
            return true; // path not included — skip
        }

        return false; // filter everything by default
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("Mainlayer filter: missing or malformed Authorization header for {}",
                    request.getRequestURI());
            sendPaymentRequired(response, "Payment required: missing Authorization header");
            return;
        }

        String callerToken = authHeader.substring(BEARER_PREFIX.length()).trim();

        if (callerToken.isBlank()) {
            sendPaymentRequired(response, "Payment required: empty bearer token");
            return;
        }

        try {
            boolean entitled = client.checkEntitlement(callerToken, null);
            if (!entitled) {
                log.info("Mainlayer entitlement denied for path={}", request.getRequestURI());
                sendPaymentRequired(response, "Payment required: entitlement check failed");
                return;
            }
        } catch (MainlayerPaymentRequiredException e) {
            log.warn("Mainlayer payment required: {}", e.getMessage());
            sendPaymentRequired(response, e.getMessage());
            return;
        } catch (Exception e) {
            log.error("Mainlayer entitlement check encountered an unexpected error", e);
            sendPaymentRequired(response, "Payment required: entitlement service unavailable");
            return;
        }

        chain.doFilter(request, response);
    }

    // ------------------------------------------------------------------
    // Response helpers
    // ------------------------------------------------------------------

    private void sendPaymentRequired(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String body = """
                {"error": "payment_required", "message": "%s"}
                """.formatted(message.replace("\"", "'"));
        response.getWriter().write(body.strip());
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static Builder builder(MainlayerClient client, MainlayerProperties properties) {
        return new Builder(client, properties);
    }

    public static final class Builder {
        private final MainlayerClient client;
        private final MainlayerProperties properties;
        private List<String> includedPaths = Collections.emptyList();
        private List<String> excludedPaths = Collections.emptyList();

        private Builder(MainlayerClient client, MainlayerProperties properties) {
            this.client = client;
            this.properties = properties;
        }

        /**
         * Only apply the filter to requests matching one of these Ant-style
         * path patterns.  When empty, every path is protected (default).
         */
        public Builder includedPaths(List<String> paths) {
            this.includedPaths = paths == null ? Collections.emptyList() : List.copyOf(paths);
            return this;
        }

        /**
         * Never apply the filter to requests matching one of these Ant-style
         * path patterns.  Exclusions take precedence over inclusions.
         */
        public Builder excludedPaths(List<String> paths) {
            this.excludedPaths = paths == null ? Collections.emptyList() : List.copyOf(paths);
            return this;
        }

        public MainlayerFilter build() {
            return new MainlayerFilter(this);
        }
    }
}
