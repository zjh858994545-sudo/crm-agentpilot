# Enterprise SSO and JWT Authentication

Current production-ready baseline supports both database RBAC token authentication and enterprise JWT authentication. RBAC tokens are useful for internal tools and service accounts. Enterprise JWT is the preferred path for commercial deployments because user lifecycle, MFA, password policy, and offboarding are controlled by the customer's identity provider.

## Current State

- `X-AgentPilot-Token` or `Authorization: Bearer <token>` is accepted.
- Database tokens are stored as SHA-256 hashes in `agentpilot_user`.
- Authentication loads `userId`, `tenantId`, `salesRepId`, roles, and permissions from the database.
- When `AGENTPILOT_JWT_ENABLED=true`, `Authorization: Bearer <jwt>` is decoded with Spring Security's JWT decoder.
- JWT authentication validates issuer metadata, audience, tenant claim, tenant allow-list, sales-rep claim, and either roles or permissions.
- JWT roles are mapped to the same `AgentPilotPrincipal` used by RBAC tokens, so `@PreAuthorize` and CRM data-scope checks are shared.
- Demo fallback identity is only for local permissive mode.

## Supported SSO/JWT State

Supported identity providers:

- Azure AD / Microsoft Entra ID.
- Alibaba Cloud IDaaS.
- Okta.
- Keycloak for private deployments.

JWT claims required by AgentPilot:

```json
{
  "iss": "https://idp.example.com",
  "sub": "external-user-id",
  "aud": "crm-agentpilot",
  "email": "seller@example.com",
  "name": "Seller Name",
  "tenant_id": "tenant-acme",
  "sales_rep_id": 1001,
  "roles": ["sales"],
  "permissions": ["agent:use", "crm:read", "crm:write"]
}
```

## Backend Implementation Plan

Already implemented:

1. `spring-boot-starter-oauth2-resource-server`.
2. `JwtSsoAuthenticationFilter` before the database token filter.
3. Audience validation and configurable claim names.
4. Role-to-permission mapping for `sales`, `sales_manager`, and `system_admin`.
5. Database RBAC token support remains available for service accounts and migration periods.

Configuration:

```yaml
agentpilot:
  security:
    jwt:
      enabled: ${AGENTPILOT_JWT_ENABLED:false}
      issuer-uri: ${AGENTPILOT_JWT_ISSUER_URI:}
      audience: ${AGENTPILOT_JWT_AUDIENCE:crm-agentpilot}
      user-id-claim: ${AGENTPILOT_JWT_USER_ID_CLAIM:user_id}
      tenant-claim: ${AGENTPILOT_JWT_TENANT_CLAIM:tenant_id}
      sales-rep-claim: ${AGENTPILOT_JWT_SALES_REP_CLAIM:sales_rep_id}
      roles-claim: ${AGENTPILOT_JWT_ROLES_CLAIM:roles}
      permissions-claim: ${AGENTPILOT_JWT_PERMISSIONS_CLAIM:permissions}
      allowed-tenants: ${AGENTPILOT_JWT_ALLOWED_TENANTS:}
```

Still recommended before external production rollout:

1. Add login audit rows with `issuer`, `subject`, `tenantId`, `salesRepId`, and IP.
2. Add tenant allow-list checks so a token from an unknown tenant cannot create implicit access.
3. Add a staging IdP integration test with a real JWKS endpoint.

## Frontend Implementation Plan

Already implemented:

1. The API client sends `Authorization: Bearer <jwt>` when `agentpilot.bearerToken` or `VITE_AGENTPILOT_BEARER_TOKEN` is present.
2. The login screen accepts either an internal `X-AgentPilot-Token`, a raw JWT, or a `Bearer <jwt>` value.
3. The workspace profile is still loaded from `/api/auth/me`, so menu visibility and data scope are derived from the backend principal rather than frontend guesses.

Recommended for external enterprise rollout:

1. Add an OIDC client such as `oidc-client-ts`.
2. Login redirects to enterprise IdP.
3. Store only short-lived access token in memory when possible.
4. Send `Authorization: Bearer <jwt>` to backend.
5. Refresh via IdP flow, not a custom password login.

## Migration Plan

1. Keep current RBAC token path for admins.
2. Enable JWT in staging for one test tenant.
3. Map external IdP users to existing `agentpilot_user` rows.
4. Run access tests for sales, manager, and system admin.
5. Disable demo token in production.
6. Rotate old API tokens after SSO rollout.

## Security Notes

- Never trust frontend-supplied `tenantId`, `userId`, or `salesRepId`.
- Validate JWT signature, issuer, audience, expiration, and tenant allow-list.
- Treat an empty allow-list as acceptable only for local/staging validation; commercial deployments should explicitly list onboarded tenant IDs.
- Use short token TTL.
- Service accounts should have narrow permissions.
- Sensitive model API keys stay server-side only.
