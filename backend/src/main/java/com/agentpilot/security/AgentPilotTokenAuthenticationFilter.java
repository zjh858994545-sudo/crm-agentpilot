package com.agentpilot.security;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.security.config.AgentPilotSecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AgentPilotTokenAuthenticationFilter extends OncePerRequestFilter {
    private final AgentPilotSecurityProperties properties;
    private final ObjectMapper objectMapper;

    public AgentPilotTokenAuthenticationFilter(AgentPilotSecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            if (!properties.strict()) {
                authenticateDemoUser();
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (!constantTimeEquals(token, properties.getApiToken())) {
            writeUnauthorized(response, "Invalid AgentPilot API token");
            return;
        }

        authenticateDemoUser();
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String headerToken = request.getHeader("X-AgentPilot-Token");
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken.trim();
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        return null;
    }

    private boolean constantTimeEquals(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void authenticateDemoUser() {
        AgentPilotPrincipal principal = new AgentPilotPrincipal(
                properties.getDemoUserId(),
                properties.getDemoSalesRepId(),
                List.copyOf(properties.getPermissions())
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                properties.getPermissions().stream().map(SimpleGrantedAuthority::new).toList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail("UNAUTHORIZED", message));
    }
}
