package com.nexploy.campuslink.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Boots a loud, non-fatal warning when the app is running on the checked-in <em>default</em> JWT
 * secret or demo password (Step 10 hardening). These defaults exist only so the app boots on a
 * fresh dev machine; in any shared or internet-facing deployment they must be overridden via the
 * {@code JWT_SECRET} / {@code DEMO_PASSWORD} environment variables. The check is advisory (a WARN,
 * not a boot failure) so local development and the test profile are never blocked, while an operator
 * gets an unmissable signal in the logs if a real deployment is still on the defaults.
 */
@Component
public class InsecureDefaultsAdvisor {

    /** Must mirror the default in {@code application.yml security.jwt.secret}. */
    static final String DEFAULT_JWT_SECRET = "7fK9mQ2xV8rL4pN6zT1wC5yH3sJ8dA0eR7kM2vP9xB4";
    /** Must mirror the default in {@code application.yml security.demo.password}. */
    static final String DEFAULT_DEMO_PASSWORD = "change-me-now";

    private static final Logger log = LoggerFactory.getLogger(InsecureDefaultsAdvisor.class);

    private final String jwtSecret;
    private final String demoPassword;

    public InsecureDefaultsAdvisor(
        @Value("${security.jwt.secret:}") String jwtSecret,
        @Value("${security.demo.password:}") String demoPassword) {
        this.jwtSecret = jwtSecret;
        this.demoPassword = demoPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warnOnInsecureDefaults() {
        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            log.warn("SECURITY: JWT signing key is the checked-in default. Set the JWT_SECRET "
                + "environment variable to a unique secret before any shared/production deployment.");
        }
        if (DEFAULT_DEMO_PASSWORD.equals(demoPassword)) {
            log.warn("SECURITY: demo account password is the checked-in default. Set the "
                + "DEMO_PASSWORD environment variable before any shared/production deployment.");
        }
    }
}
