INSERT INTO agentpilot_permission (id, code, name) VALUES
(10, 'ops:read', 'Read operations and retention status'),
(11, 'ops:write', 'Run operations maintenance actions');

INSERT INTO agentpilot_role_permission (role_id, permission_id) VALUES
(3, 10),
(3, 11);
