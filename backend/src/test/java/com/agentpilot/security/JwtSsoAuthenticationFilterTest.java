package com.agentpilot.security;

import com.agentpilot.security.config.JwtSsoProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSsoAuthenticationFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesEnterpriseJwtIntoTenantScopedPrincipal() throws Exception {
        JwtSsoProperties properties = properties();
        JwtDecoder decoder = token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject("enterprise-user-42")
                .audience(List.of("crm-agentpilot"))
                .claim("user_id", 42L)
                .claim("tenant_id", "tenant-alpha")
                .claim("sales_rep_id", 7L)
                .claim("roles", List.of("sales_manager"))
                .build();
        JwtSsoAuthenticationFilter filter = new JwtSsoAuthenticationFilter(properties, decoder, objectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/customers");
        request.addHeader("Authorization", "Bearer enterprise.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<AgentPilotPrincipal> principalRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            principalRef.set((AgentPilotPrincipal) authentication.getPrincipal());
        };

        filter.doFilter(request, response, chain);

        AgentPilotPrincipal principal = principalRef.get();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.tenantId()).isEqualTo("tenant-alpha");
        assertThat(principal.salesRepId()).isEqualTo(7L);
        assertThat(principal.roles()).containsExactly("sales_manager");
        assertThat(principal.permissions()).contains("agent:use", "crm:read", "crm:write", "evaluation:run");
    }

    @Test
    void rejectsJwtWithUnexpectedAudience() throws Exception {
        JwtSsoProperties properties = properties();
        JwtDecoder decoder = token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject("enterprise-user-42")
                .audience(List.of("other-system"))
                .claim("user_id", 42L)
                .claim("tenant_id", "tenant-alpha")
                .claim("sales_rep_id", 7L)
                .claim("roles", List.of("sales"))
                .build();
        JwtSsoAuthenticationFilter filter = new JwtSsoAuthenticationFilter(properties, decoder, objectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/customers");
        request.addHeader("Authorization", "Bearer enterprise.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
    }

    @Test
    void rejectsJwtWithoutRoleOrPermissionClaims() throws Exception {
        JwtSsoProperties properties = properties();
        JwtDecoder decoder = token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .audience(List.of("crm-agentpilot"))
                .claim("user_id", 42L)
                .claim("tenant_id", "tenant-alpha")
                .claim("sales_rep_id", 7L)
                .build();
        JwtSsoAuthenticationFilter filter = new JwtSsoAuthenticationFilter(properties, decoder, objectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/customers");
        request.addHeader("Authorization", "Bearer enterprise.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("Filter chain should not be called for an unscoped JWT");
        });

        assertThat(response.getStatus()).isEqualTo(401);
    }

    private JwtSsoProperties properties() {
        JwtSsoProperties properties = new JwtSsoProperties();
        properties.setEnabled(true);
        properties.setAudience("crm-agentpilot");
        properties.setUserIdClaim("user_id");
        properties.setTenantClaim("tenant_id");
        properties.setSalesRepClaim("sales_rep_id");
        properties.setRolesClaim("roles");
        properties.setPermissionsClaim("permissions");
        return properties;
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
