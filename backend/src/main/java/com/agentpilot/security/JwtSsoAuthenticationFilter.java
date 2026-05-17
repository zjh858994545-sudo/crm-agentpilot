package com.agentpilot.security;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.security.config.JwtSsoProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class JwtSsoAuthenticationFilter extends OncePerRequestFilter {
    private static final List<String> ADMIN_PERMISSIONS = List.of(
            "agent:use",
            "crm:read",
            "crm:write",
            "knowledge:read",
            "knowledge:write",
            "product:read",
            "evaluation:run",
            "events:read",
            "events:write",
            "ops:read",
            "ops:write"
    );
    private static final List<String> MANAGER_PERMISSIONS = List.of(
            "agent:use",
            "crm:read",
            "crm:write",
            "knowledge:read",
            "product:read",
            "evaluation:run",
            "events:read"
    );
    private static final List<String> SALES_PERMISSIONS = List.of(
            "agent:use",
            "crm:read",
            "crm:write",
            "knowledge:read",
            "product:read"
    );

    private final JwtSsoProperties properties;
    private final JwtDecoder jwtDecoder;
    private final ObjectMapper objectMapper;

    public JwtSsoAuthenticationFilter(
            JwtSsoProperties properties,
            JwtDecoder jwtDecoder,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.jwtDecoder = jwtDecoder;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveBearerToken(request);
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            validateAudience(jwt);

            Long userId = requireLong(jwt, properties.getUserIdClaim(), "user id");
            String tenantId = requireString(jwt, properties.getTenantClaim(), "tenant id");
            Long salesRepId = requireLong(jwt, properties.getSalesRepClaim(), "sales rep id");
            List<String> permissions = resolvePermissions(jwt);

            AgentPilotPrincipal principal = new AgentPilotPrincipal(userId, tenantId, salesRepId, permissions);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    permissions.stream().map(SimpleGrantedAuthority::new).toList()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(response, "Invalid enterprise JWT");
        }
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private void validateAudience(Jwt jwt) {
        if (!StringUtils.hasText(properties.getAudience())) {
            return;
        }
        if (!jwt.getAudience().contains(properties.getAudience())) {
            throw new IllegalArgumentException("JWT audience does not match CRM-AgentPilot audience");
        }
    }

    private Long requireLong(Jwt jwt, String claimName, String label) {
        if (!StringUtils.hasText(claimName)) {
            throw new IllegalArgumentException("JWT " + label + " claim is not configured");
        }
        Object value = jwt.getClaims().get(claimName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Long.parseLong(text.trim());
        }
        throw new IllegalArgumentException("JWT " + label + " claim is required");
    }

    private String requireString(Jwt jwt, String claimName, String label) {
        if (!StringUtils.hasText(claimName)) {
            throw new IllegalArgumentException("JWT " + label + " claim is not configured");
        }
        Object value = jwt.getClaims().get(claimName);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        throw new IllegalArgumentException("JWT " + label + " claim is required");
    }

    private List<String> resolvePermissions(Jwt jwt) {
        List<String> directPermissions = claimAsStrings(jwt, properties.getPermissionsClaim());
        Set<String> permissions = new LinkedHashSet<>(directPermissions);
        if (permissions.isEmpty()) {
            List<String> roles = claimAsStrings(jwt, properties.getRolesClaim());
            if (roles.isEmpty()) {
                throw new IllegalArgumentException("JWT must include roles or permissions");
            }
            for (String role : roles) {
                permissions.addAll(permissionsForRole(role));
            }
        }
        if (permissions.isEmpty()) {
            throw new IllegalArgumentException("JWT did not resolve to any CRM-AgentPilot permissions");
        }
        return List.copyOf(permissions);
    }

    private List<String> claimAsStrings(Jwt jwt, String claimName) {
        if (!StringUtils.hasText(claimName)) {
            return List.of();
        }
        Object value = jwt.getClaims().get(claimName);
        if (value instanceof Collection<?> values) {
            return values.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            String[] parts = text.split(",");
            List<String> result = new ArrayList<>();
            for (String part : parts) {
                String item = part.trim();
                if (StringUtils.hasText(item)) {
                    result.add(item);
                }
            }
            return result;
        }
        return List.of();
    }

    private List<String> permissionsForRole(String role) {
        String normalized = role == null ? "" : role.toLowerCase(Locale.ROOT).replace('-', '_').trim();
        return switch (normalized) {
            case "system_admin", "admin", "administrator" -> ADMIN_PERMISSIONS;
            case "sales_manager", "manager", "supervisor" -> MANAGER_PERMISSIONS;
            case "sales", "sales_rep", "seller" -> SALES_PERMISSIONS;
            default -> throw new IllegalArgumentException("Unsupported JWT role: " + role);
        };
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail("UNAUTHORIZED", message));
    }
}
