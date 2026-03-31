package fr.mainlayer.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or class as requiring a valid Mainlayer payment entitlement
 * before it can be invoked.
 *
 * <p>When placed on a method, the AOP aspect intercepts the call, extracts the
 * caller token from the current HTTP request (or from the annotated method's
 * parameter annotated with {@link CallerToken}), performs an entitlement check
 * against the Mainlayer API, and either proceeds or throws
 * {@link fr.mainlayer.spring.MainlayerClient.MainlayerPaymentRequiredException}.
 *
 * <p>When placed on a class, all public methods inherit the requirement.
 *
 * <h2>Usage — controller method</h2>
 * <pre>{@code
 * @GetMapping("/premium-feature")
 * @RequireMainlayerPayment
 * public ResponseEntity<String> premiumFeature(HttpServletRequest request) {
 *     return ResponseEntity.ok("Access granted!");
 * }
 * }</pre>
 *
 * <h2>Usage — service method with explicit token</h2>
 * <pre>{@code
 * @RequireMainlayerPayment(resourceId = "res_special")
 * public Result process(@CallerToken String agentToken, Input input) {
 *     // ...
 * }
 * }</pre>
 *
 * <h2>Usage — class-level</h2>
 * <pre>{@code
 * @Service
 * @RequireMainlayerPayment
 * public class PremiumService {
 *     // every public method is protected
 * }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireMainlayerPayment {

    /**
     * Override the resource ID for this specific method or class.
     * When empty the value configured via {@code mainlayer.resource-id} is used.
     */
    String resourceId() default "";

    /**
     * Name of the HTTP header that carries the caller's bearer token.
     * Defaults to {@code Authorization}.
     */
    String tokenHeader() default "Authorization";

    /**
     * HTTP status code returned to the client when entitlement is denied
     * (only relevant when the aspect is used in a web context).
     * Defaults to {@code 402 Payment Required}.
     */
    int denyStatus() default 402;

    /**
     * When {@code true} the aspect records a usage event on every successful
     * entitlement check.  Set to {@code false} if you want to record usage
     * separately at a coarser granularity.
     */
    boolean recordUsage() default true;
}
