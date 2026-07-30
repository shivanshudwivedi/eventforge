package io.eventforge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final byte[] expectedApiKey;

    public ApiKeyFilter(EventForgeProperties properties) {
        this.expectedApiKey = properties.apiKey().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader("X-API-Key");
        boolean authorized = supplied != null && MessageDigest.isEqual(
                expectedApiKey,
                supplied.getBytes(StandardCharsets.UTF_8));
        if (!authorized) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing or invalid API key");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
