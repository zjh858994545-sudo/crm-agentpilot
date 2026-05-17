package com.agentpilot.security.config;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.security.AgentPilotTokenAuthenticationFilter;
import com.agentpilot.security.JwtSsoAuthenticationFilter;
import com.agentpilot.security.RbacPrincipalService;
import com.agentpilot.security.ratelimit.ApiRateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public AgentPilotTokenAuthenticationFilter agentPilotTokenAuthenticationFilter(
            AgentPilotSecurityProperties properties,
            ObjectMapper objectMapper,
            RbacPrincipalService rbacPrincipalService
    ) {
        return new AgentPilotTokenAuthenticationFilter(properties, objectMapper, rbacPrincipalService);
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtSsoProperties properties) {
        if (!properties.isEnabled()) {
            return token -> {
                throw new JwtException("JWT SSO is disabled");
            };
        }
        if (!StringUtils.hasText(properties.getIssuerUri())) {
            return token -> {
                throw new JwtException("JWT SSO requires agentpilot.security.jwt.issuer-uri");
            };
        }
        return JwtDecoders.fromIssuerLocation(properties.getIssuerUri());
    }

    @Bean
    public JwtSsoAuthenticationFilter jwtSsoAuthenticationFilter(
            JwtSsoProperties properties,
            JwtDecoder jwtDecoder,
            ObjectMapper objectMapper,
            RbacPrincipalService rbacPrincipalService
    ) {
        return new JwtSsoAuthenticationFilter(properties, jwtDecoder, objectMapper, rbacPrincipalService::tenantActive);
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Password login is disabled. Use X-AgentPilot-Token.");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtSsoAuthenticationFilter jwtSsoAuthenticationFilter,
            AgentPilotTokenAuthenticationFilter tokenFilter,
            ApiRateLimitFilter apiRateLimitFilter,
            ObjectMapper objectMapper
    ) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(), ApiResponse.fail("UNAUTHORIZED", "Authentication is required"));
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(403);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(), ApiResponse.fail("FORBIDDEN", "Permission denied"));
                        })
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/health",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()
                        .requestMatchers(
                                "/actuator/metrics/**",
                                "/actuator/prometheus"
                        ).authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtSsoAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tokenFilter, JwtSsoAuthenticationFilter.class);
        http.addFilterAfter(apiRateLimitFilter, AgentPilotTokenAuthenticationFilter.class);
        return http.build();
    }
}
