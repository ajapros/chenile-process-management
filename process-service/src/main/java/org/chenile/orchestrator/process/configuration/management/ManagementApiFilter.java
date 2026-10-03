package org.chenile.orchestrator.process.configuration.management;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.chenile.core.context.ContextContainer;
import org.chenile.core.context.HeaderUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Separate, opt-in administrator surface; this key grants administration of ALL tenants. */
public class ManagementApiFilter extends OncePerRequestFilter {
    private final byte[] key;
    public ManagementApiFilter(String key) {
        if (key == null || key.length() < 32) throw new IllegalArgumentException("chenile.process.management.api-key must contain at least 32 characters");
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader("X-Process-Management-Key");
        if (supplied == null || !MessageDigest.isEqual(key, supplied.getBytes(StandardCharsets.UTF_8))) {
            reject(response, 401, "A valid administrator key is required"); return;
        }
        String tenant = request.getHeader(HeaderUtils.TENANT_ID_KEY);
        if (tenant == null || !tenant.matches("[A-Za-z0-9_.-]{1,128}")) {
            reject(response, 400, "A tenant header is required (letters, digits, underscore, dot or hyphen)"); return;
        }
        var context = ContextContainer.CONTEXT_CONTAINER;
        var snapshot = context.snapshot();
        try { context.put(HeaderUtils.TENANT_ID_KEY, tenant); chain.doFilter(request, response); }
        finally { context.restore(snapshot); }
    }
    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
