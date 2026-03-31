package fr.mainlayer.spring.aspect;

import fr.mainlayer.spring.MainlayerClient;
import fr.mainlayer.spring.MainlayerClient.MainlayerPaymentRequiredException;
import fr.mainlayer.spring.annotation.CallerToken;
import fr.mainlayer.spring.annotation.RequireMainlayerPayment;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * AOP aspect that enforces {@link RequireMainlayerPayment} on methods and classes.
 *
 * <h2>Token resolution order</h2>
 * <ol>
 *   <li>A method parameter annotated with {@link CallerToken}</li>
 *   <li>The {@code Authorization} (or overridden) HTTP header of the current
 *       servlet request</li>
 * </ol>
 *
 * <p>If no token can be resolved the call is rejected immediately.
 */
@Aspect
public class MainlayerPaymentAspect {

    private static final Logger log = LoggerFactory.getLogger(MainlayerPaymentAspect.class);

    private final MainlayerClient client;

    public MainlayerPaymentAspect(MainlayerClient client) {
        this.client = client;
    }

    // ------------------------------------------------------------------
    // Pointcuts — method-level and class-level annotations
    // ------------------------------------------------------------------

    @Around("@annotation(fr.mainlayer.spring.annotation.RequireMainlayerPayment)")
    public Object aroundAnnotatedMethod(ProceedingJoinPoint pjp) throws Throwable {
        RequireMainlayerPayment annotation = resolveMethodAnnotation(pjp);
        return enforce(pjp, annotation);
    }

    @Around("@within(fr.mainlayer.spring.annotation.RequireMainlayerPayment) " +
            "&& !@annotation(fr.mainlayer.spring.annotation.RequireMainlayerPayment)")
    public Object aroundAnnotatedClass(ProceedingJoinPoint pjp) throws Throwable {
        RequireMainlayerPayment annotation = resolveClassAnnotation(pjp);
        return enforce(pjp, annotation);
    }

    // ------------------------------------------------------------------
    // Core enforcement logic
    // ------------------------------------------------------------------

    private Object enforce(ProceedingJoinPoint pjp, RequireMainlayerPayment annotation)
            throws Throwable {

        String callerToken = resolveCallerToken(pjp, annotation.tokenHeader());

        if (callerToken == null || callerToken.isBlank()) {
            log.warn("Mainlayer payment check failed: no caller token found for {}",
                    pjp.getSignature().toShortString());
            throw new MainlayerPaymentRequiredException(
                    "Payment required: no authorization token provided");
        }

        // Strip "Bearer " prefix if present
        String rawToken = stripBearer(callerToken);

        String resourceId = annotation.resourceId().isBlank() ? null : annotation.resourceId();

        boolean entitled = client.checkEntitlement(rawToken, resourceId);

        if (!entitled) {
            log.warn("Mainlayer payment required for method={}, resource={}",
                    pjp.getSignature().toShortString(), resourceId);
            throw new MainlayerPaymentRequiredException(
                    "Payment required: entitlement check failed");
        }

        if (annotation.recordUsage()) {
            client.recordUsage(rawToken, resourceId,
                    Map.of("method", pjp.getSignature().toShortString()));
        }

        log.debug("Mainlayer entitlement granted for method={}",
                pjp.getSignature().toShortString());

        return pjp.proceed();
    }

    // ------------------------------------------------------------------
    // Helper: resolve annotation from method signature
    // ------------------------------------------------------------------

    private RequireMainlayerPayment resolveMethodAnnotation(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        return method.getAnnotation(RequireMainlayerPayment.class);
    }

    private RequireMainlayerPayment resolveClassAnnotation(ProceedingJoinPoint pjp) {
        return pjp.getTarget().getClass().getAnnotation(RequireMainlayerPayment.class);
    }

    // ------------------------------------------------------------------
    // Helper: resolve caller token
    // ------------------------------------------------------------------

    private String resolveCallerToken(ProceedingJoinPoint pjp, String headerName) {
        // 1. Check for @CallerToken-annotated parameter
        String tokenFromParam = resolveTokenFromParameter(pjp);
        if (tokenFromParam != null) {
            return tokenFromParam;
        }

        // 2. Fall back to the active HTTP request
        return resolveTokenFromRequest(headerName);
    }

    private String resolveTokenFromParameter(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        Object[] args = pjp.getArgs();

        for (int i = 0; i < paramAnnotations.length; i++) {
            for (Annotation a : paramAnnotations[i]) {
                if (a instanceof CallerToken && args[i] instanceof String token) {
                    return token;
                }
            }
        }
        return null;
    }

    private String resolveTokenFromRequest(String headerName) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            return request.getHeader(headerName);
        }
        return null;
    }

    private static String stripBearer(String token) {
        if (token != null && token.toLowerCase().startsWith("bearer ")) {
            return token.substring(7).trim();
        }
        return token;
    }
}
