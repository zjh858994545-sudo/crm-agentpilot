package com.agentpilot.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static AgentPilotPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AgentPilotPrincipal principal)) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        return principal;
    }

    public static Long userId() {
        return require().userId();
    }

    public static String tenantId() {
        return require().tenantId();
    }

    public static Long salesRepId() {
        return require().salesRepId();
    }

    public static List<String> roles() {
        return require().roles();
    }

    public static boolean hasRole(String role) {
        return roles().contains(role);
    }

    public static boolean hasAnyRole(String... roles) {
        List<String> currentRoles = roles();
        return Arrays.stream(roles).anyMatch(currentRoles::contains);
    }

    public static boolean canAccessSalesRep(Long targetSalesRepId) {
        return isManagerOrAdmin() || Objects.equals(salesRepId(), targetSalesRepId);
    }

    public static Long scopedSalesRepId(Long requestedSalesRepId) {
        if (requestedSalesRepId == null || Objects.equals(requestedSalesRepId, salesRepId())) {
            return salesRepId();
        }
        if (isManagerOrAdmin()) {
            return requestedSalesRepId;
        }
        throw new AccessDeniedException("salesRepId is outside current data scope");
    }

    public static boolean isManagerOrAdmin() {
        return hasAnyRole("sales_manager", "system_admin");
    }
}
