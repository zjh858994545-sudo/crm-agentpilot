package com.agentpilot.security;

import com.agentpilot.security.config.AgentPilotSecurityProperties;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Base64;

@Service
public class RbacPrincipalService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
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

    public record UserProvisioningResult(UserProfile profile, String apiToken) {
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
                            SELECT u.id AS id, u.tenant_id AS tenant_id, u.sales_rep_id AS sales_rep_id
                            FROM agentpilot_user u
                            JOIN agentpilot_tenant t ON t.id = u.tenant_id AND t.status = 'ACTIVE'
                            WHERE u.api_token_sha256 = ?
                              AND u.status = 'ACTIVE'
                              AND (? = TRUE OR u.username NOT IN ('linxiaofeng', 'zhouyuqing', 'admin', 'chenming'))
                            """,
                    (rs, rowNum) -> {
                        Long userId = rs.getLong("id");
                        String tenantId = rs.getString("tenant_id");
                        Long salesRepId = rs.getLong("sales_rep_id");
                        List<String> roles = roleCodes(userId);
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
                        return new AgentPilotPrincipal(userId, tenantId, salesRepId, roles, permissions);
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
                """
                        SELECT COUNT(*)
                        FROM agentpilot_user u
                        JOIN agentpilot_tenant t ON t.id = u.tenant_id AND t.status = 'ACTIVE'
                        WHERE u.status = 'ACTIVE'
                        """,
                Long.class
        );
    }

    public long roleCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agentpilot_role", Long.class);
    }

    public long activeTenantCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agentpilot_tenant WHERE status = 'ACTIVE'",
                Long.class
        );
    }

    public boolean tenantActive(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agentpilot_tenant WHERE id = ? AND status = 'ACTIVE'",
                Long.class,
                tenantId
        );
        return count != null && count > 0;
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
                            SELECT u.id AS id,
                                   u.tenant_id AS tenant_id,
                                   u.username AS username,
                                   u.display_name AS display_name,
                                   u.sales_rep_id AS sales_rep_id,
                                   u.status AS status,
                                   u.last_authenticated_at AS last_authenticated_at,
                                   u.last_authenticated_ip AS last_authenticated_ip
                            FROM agentpilot_user u
                            JOIN agentpilot_tenant t ON t.id = u.tenant_id AND t.status = 'ACTIVE'
                            WHERE u.id = ?
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
        return listProfiles(null);
    }

    public List<UserProfile> listProfiles(String tenantId) {
        Object[] args = tenantId == null || tenantId.isBlank() ? new Object[]{} : new Object[]{tenantId};
        String whereClause = tenantId == null || tenantId.isBlank() ? "" : "AND u.tenant_id = ?\n";
        return jdbcTemplate.query(
                ("""
                        SELECT u.id AS id,
                               u.tenant_id AS tenant_id,
                               u.username AS username,
                               u.display_name AS display_name,
                               u.sales_rep_id AS sales_rep_id,
                               u.status AS status,
                               u.last_authenticated_at AS last_authenticated_at,
                               u.last_authenticated_ip AS last_authenticated_ip
                        FROM agentpilot_user u
                        JOIN agentpilot_tenant t ON t.id = u.tenant_id AND t.status = 'ACTIVE'
                        WHERE 1 = 1
                        %s
                        ORDER BY u.tenant_id, u.id
                        """).formatted(whereClause),
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
                args
        );
    }

    @Transactional
    public UserProvisioningResult createUser(
            String tenantId,
            String username,
            String displayName,
            Long salesRepId,
            List<String> roleCodes
    ) {
        String normalizedTenantId = requireText(tenantId, "tenantId");
        requireActiveTenant(normalizedTenantId);
        String normalizedUsername = requireText(username, "username");
        String normalizedDisplayName = requireText(displayName, "displayName");
        Long normalizedSalesRepId = salesRepId == null ? 1L : salesRepId;
        List<Long> roleIds = resolveRoleIds(roleCodes);
        Long userId = nextUserId();
        String apiToken = newApiToken();
        jdbcTemplate.update(
                """
                        INSERT INTO agentpilot_user (
                            id, tenant_id, username, display_name, api_token_sha256, sales_rep_id, status, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                userId,
                normalizedTenantId,
                normalizedUsername,
                normalizedDisplayName,
                sha256(apiToken),
                normalizedSalesRepId
        );
        replaceRoles(userId, roleIds);
        return new UserProvisioningResult(findProfileByUserId(userId).orElseThrow(), apiToken);
    }

    @Transactional
    public UserProfile updateUser(
            Long userId,
            String tenantId,
            String displayName,
            Long salesRepId,
            List<String> roleCodes
    ) {
        requireUserInTenant(userId, tenantId);
        int updated = jdbcTemplate.update(
                """
                        UPDATE agentpilot_user
                        SET display_name = ?,
                            sales_rep_id = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                          AND tenant_id = ?
                        """,
                requireText(displayName, "displayName"),
                salesRepId == null ? 1L : salesRepId,
                userId,
                tenantId
        );
        if (updated != 1) {
            throw new IllegalArgumentException("User not found or outside current tenant");
        }
        replaceRoles(userId, resolveRoleIds(roleCodes));
        return findProfileByUserId(userId).orElseThrow();
    }

    @Transactional
    public UserProfile changeUserStatus(Long userId, String tenantId, String status) {
        requireUserInTenant(userId, tenantId);
        String normalizedStatus = requireText(status, "status").toUpperCase();
        if (!Set.of("ACTIVE", "DISABLED").contains(normalizedStatus)) {
            throw new IllegalArgumentException("Unsupported user status: " + status);
        }
        int updated = jdbcTemplate.update(
                """
                        UPDATE agentpilot_user
                        SET status = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                          AND tenant_id = ?
                        """,
                normalizedStatus,
                userId,
                tenantId
        );
        if (updated != 1) {
            throw new IllegalArgumentException("User not found or outside current tenant");
        }
        return findProfileByUserId(userId).orElseThrow();
    }

    @Transactional
    public UserProvisioningResult regenerateToken(Long userId, String tenantId) {
        requireUserInTenant(userId, tenantId);
        String apiToken = newApiToken();
        int updated = jdbcTemplate.update(
                """
                        UPDATE agentpilot_user
                        SET api_token_sha256 = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                          AND tenant_id = ?
                        """,
                sha256(apiToken),
                userId,
                tenantId
        );
        if (updated != 1) {
            throw new IllegalArgumentException("User not found or outside current tenant");
        }
        return new UserProvisioningResult(findProfileByUserId(userId).orElseThrow(), apiToken);
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

    private Long nextUserId() {
        String databaseProduct = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                connection.getMetaData().getDatabaseProductName()
        );
        String nextValueSql = databaseProduct != null && databaseProduct.toLowerCase().contains("h2")
                ? "SELECT NEXT VALUE FOR agentpilot_user_id_seq"
                : "SELECT nextval('agentpilot_user_id_seq')";
        Long next = jdbcTemplate.queryForObject(nextValueSql, Long.class);
        return next == null ? 10000L : next;
    }

    private void replaceRoles(Long userId, List<Long> roleIds) {
        jdbcTemplate.update("DELETE FROM agentpilot_user_role WHERE user_id = ?", userId);
        for (Long roleId : roleIds) {
            jdbcTemplate.update(
                    "INSERT INTO agentpilot_user_role (user_id, role_id) VALUES (?, ?)",
                    userId,
                    roleId
            );
        }
    }

    private List<Long> resolveRoleIds(List<String> requestedRoles) {
        List<String> normalizedRoles = normalizeRoles(requestedRoles);
        List<Long> roleIds = new ArrayList<>();
        for (String roleCode : normalizedRoles) {
            try {
                roleIds.add(jdbcTemplate.queryForObject(
                        "SELECT id FROM agentpilot_role WHERE code = ?",
                        Long.class,
                        roleCode
                ));
            } catch (EmptyResultDataAccessException ex) {
                throw new IllegalArgumentException("Unknown role: " + roleCode);
            }
        }
        return roleIds;
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of("sales");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String role : roles) {
            if (StringUtils.hasText(role)) {
                normalized.add(role.trim());
            }
        }
        return normalized.isEmpty() ? List.of("sales") : List.copyOf(normalized);
    }

    private void requireActiveTenant(String tenantId) {
        if (!tenantActive(tenantId)) {
            throw new IllegalArgumentException("Tenant is not active: " + tenantId);
        }
    }

    private void requireUserInTenant(Long userId, String tenantId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        requireText(tenantId, "tenantId");
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agentpilot_user WHERE id = ? AND tenant_id = ?",
                Long.class,
                userId,
                tenantId
        );
        if (count == null || count == 0) {
            throw new IllegalArgumentException("User not found or outside current tenant");
        }
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String newApiToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "ap_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
