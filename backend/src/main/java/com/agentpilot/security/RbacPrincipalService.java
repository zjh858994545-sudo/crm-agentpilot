package com.agentpilot.security;

import com.agentpilot.security.config.AgentPilotSecurityProperties;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RbacPrincipalService {
    private final JdbcTemplate jdbcTemplate;
    private final AgentPilotSecurityProperties securityProperties;

    public record UserProfile(
            Long userId,
            String tenantId,
            String username,
            String displayName,
            Long salesRepId,
            String status,
            LocalDateTime lastAuthenticatedAt,
            String lastAuthenticatedIp,
            List<String> roles,
            List<String> permissions
    ) {
    }

    public RbacPrincipalService(JdbcTemplate jdbcTemplate, AgentPilotSecurityProperties securityProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.securityProperties = securityProperties;
    }

    public Optional<AgentPilotPrincipal> findByApiToken(String apiToken) {
        if (apiToken == null || apiToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = sha256(apiToken);
        try {
            AgentPilotPrincipal principal = jdbcTemplate.queryForObject(
                    """
                            SELECT id, tenant_id, sales_rep_id
                            FROM agentpilot_user
                            WHERE api_token_sha256 = ?
                              AND status = 'ACTIVE'
                              AND (? = TRUE OR username NOT IN ('linxiaofeng', 'zhouyuqing', 'admin', 'chenming'))
                            """,
                    (rs, rowNum) -> {
                        Long userId = rs.getLong("id");
                        String tenantId = rs.getString("tenant_id");
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
                        return new AgentPilotPrincipal(userId, tenantId, salesRepId, permissions);
                    },
                    tokenHash,
                    securityProperties.isSeedUsersEnabled()
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

    public void recordTokenUse(Long userId, String clientIp) {
        if (userId == null) {
            return;
        }
        jdbcTemplate.update(
                """
                        UPDATE agentpilot_user
                        SET last_authenticated_at = CURRENT_TIMESTAMP,
                            last_authenticated_ip = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                clientIp,
                userId
        );
    }

    public Optional<UserProfile> findProfileByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            UserProfile profile = jdbcTemplate.queryForObject(
                    """
                            SELECT id, tenant_id, username, display_name, sales_rep_id, status, last_authenticated_at, last_authenticated_ip
                            FROM agentpilot_user
                            WHERE id = ?
                            """,
                    (rs, rowNum) -> {
                        Long id = rs.getLong("id");
                        return new UserProfile(
                                id,
                                rs.getString("tenant_id"),
                                rs.getString("username"),
                                rs.getString("display_name"),
                                rs.getLong("sales_rep_id"),
                                rs.getString("status"),
                                rs.getTimestamp("last_authenticated_at") == null ? null : rs.getTimestamp("last_authenticated_at").toLocalDateTime(),
                                rs.getString("last_authenticated_ip"),
                                roleCodes(id),
                                permissionCodes(id)
                        );
                    },
                    userId
            );
            return Optional.ofNullable(profile);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<UserProfile> listProfiles() {
        return jdbcTemplate.query(
                """
                        SELECT id, tenant_id, username, display_name, sales_rep_id, status, last_authenticated_at, last_authenticated_ip
                        FROM agentpilot_user
                        ORDER BY tenant_id, id
                        """,
                (rs, rowNum) -> {
                    Long id = rs.getLong("id");
                    return new UserProfile(
                            id,
                            rs.getString("tenant_id"),
                            rs.getString("username"),
                            rs.getString("display_name"),
                            rs.getLong("sales_rep_id"),
                            rs.getString("status"),
                            rs.getTimestamp("last_authenticated_at") == null ? null : rs.getTimestamp("last_authenticated_at").toLocalDateTime(),
                            rs.getString("last_authenticated_ip"),
                            roleCodes(id),
                            permissionCodes(id)
                    );
                }
        );
    }

    private List<String> roleCodes(Long userId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT DISTINCT r.code
                        FROM agentpilot_role r
                        JOIN agentpilot_user_role ur ON ur.role_id = r.id
                        WHERE ur.user_id = ?
                        ORDER BY r.code
                        """,
                String.class,
                userId
        );
    }

    private List<String> permissionCodes(Long userId) {
        return jdbcTemplate.queryForList(
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
