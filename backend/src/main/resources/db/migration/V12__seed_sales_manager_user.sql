INSERT INTO agentpilot_user (
    id, username, display_name, api_token_sha256, sales_rep_id, status
) VALUES
(100, 'chenming', '陈明', '482584dd64f09637cee8f3e7bfe10582788004d4a2119a701b49f56d9c53ed21', 1, 'ACTIVE');

INSERT INTO agentpilot_user_role (user_id, role_id) VALUES
(100, 2);
