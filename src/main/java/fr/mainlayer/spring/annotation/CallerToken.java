package fr.mainlayer.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Parameter-level annotation that marks a {@link String} method parameter as
 * the caller's bearer token.
 *
 * <p>The {@link fr.mainlayer.spring.aspect.MainlayerPaymentAspect} inspects
 * method parameters for this annotation and uses the value directly instead of
 * extracting the token from the HTTP request.  This is useful in non-web
 * contexts (e.g. Kafka listeners, scheduled tasks, or plain service calls) where
 * there is no active {@link jakarta.servlet.http.HttpServletRequest}.
 *
 * <pre>{@code
 * @RequireMainlayerPayment
 * public Result process(@CallerToken String agentToken, Input input) {
 *     // agentToken is validated against the Mainlayer API before entry
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CallerToken {
}
