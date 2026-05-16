package com.agentpilot.security;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

@Service
public class RbacPrincipalService {
    private final JdbcTemplate jdbcTemplate;

    public RbacPrincipalService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AgentPilotPrincipal> findByApiToken(String apiToken) {
        if (apiToken == null || apiToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = sha256(apiToken);
        try {
            AgentPilotPrincipal principal = jdbcTemplate.queryForObject(
                    """
                            SELECT id, sales_rep_id
                            FROM agentpilot_user
                            WHERE api_token_sha256 = ? AND status = 'ACTIVE'
                            """,
                    (rs, rowNum) -> {
                        Long userId = rs.getLong("id");
                        Long salesRepId = rs.getLong("sales_rep_id");
                        List<String> permissions = jdbcTemplate.queryForList(
                                """
                                        SELECT DISTINCT p.code
                                        FROM agentpilot_permission p
                                        JOIN agentpilot_role_permission rp ON rp.permission_id = p.id
                                        JOIN agentpilot_user_role ur ON ur.role_id = rp.role_id
                                        WHERE ur.user_id = ?
                                        ORDER BY p.code
                                        """,
                                String.class,
                                userId
                        );
                        return new AgentPilotPrincipal(userId, salesRepId, permissions);
                    },
                    tokenHash
            );
            return Optional.ofNullable(principal);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public long activeUserCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agentpilot_user WHERE status = 'ACTIVE'",
                Long.class
        );
    }

    public long roleCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agentpilot_role", Long.class);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
