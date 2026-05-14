package com.agentpilot.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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

    public static Long salesRepId() {
        return require().salesRepId();
    }
}
