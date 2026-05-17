# Enterprise SSO and JWT Roadmap

Current production-ready baseline is database RBAC token authentication. It is suitable for internal tools and service integration. Enterprise deployment should evolve toward SSO/JWT so user lifecycle, MFA, password policy, and offboarding are controlled by the customer's identity provider.

## Current State

- `X-AgentPilot-Token` or `Authorization: Bearer <token>` is accepted.
- Database tokens are stored as SHA-256 hashes in `agentpilot_user`.
- Authentication loads `userId`, `tenantId`, `salesRepId`, roles, and permissions from the database.
- Demo fallback token is only for local permissive mode.

## Target SSO/JWT State

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

1. Add `spring-boot-starter-oauth2-resource-server`.
2. Configure issuer and audience:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${AGENTPILOT_JWT_ISSUER_URI:}

agentpilot:
  security:
    jwt:
      enabled: ${AGENTPILOT_JWT_ENABLED:false}
      audience: ${AGENTPILOT_JWT_AUDIENCE:crm-agentpilot}
      tenant-claim: ${AGENTPILOT_JWT_TENANT_CLAIM:tenant_id}
      sales-rep-claim: ${AGENTPILOT_JWT_SALES_REP_CLAIM:sales_rep_id}
```

3. Add a `JwtAuthenticationConverter` that maps claims to `AgentPilotPrincipal`.
4. Keep database RBAC token support for service accounts and migration period.
5. Add login audit rows with `issuer`, `subject`, `tenantId`, `salesRepId`, and IP.
6. Add tenant allow-list checks so a token from an unknown tenant cannot create implicit access.

## Frontend Implementation Plan

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
- Use short token TTL.
- Service accounts should have narrow permissions.
- Sensitive model API keys stay server-side only.

