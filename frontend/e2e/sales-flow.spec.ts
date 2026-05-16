import { expect, test, type Page } from '@playwright/test';

const tokens = {
  sales: 'agentpilot-sales-1',
  manager: 'agentpilot-manager',
  admin: 'agentpilot-admin'
} as const;

async function loginAs(page: Page, role: keyof typeof tokens) {
  await page.goto('/');
  await page.evaluate(() => window.localStorage.clear());
  await page.goto('/');
  await page.getByTestId('token-login-input').fill(tokens[role]);
  await page.getByTestId('token-login-submit').click();
  await expect(page.getByText('工作台在线')).toBeVisible();
}

async function clearPendingConfirmations(page: Page) {
  const response = await page.request.get('/api/agent/confirmations/page?status=PENDING&page=1&pageSize=100', {
    headers: { 'X-AgentPilot-Token': tokens.sales }
  });
  const body = await response.json();
  const confirmations = body.data?.items ?? [];
  for (const confirmation of confirmations) {
    await page.request.post(`/api/agent/confirmations/${confirmation.id}/reject`, {
      headers: { 'X-AgentPilot-Token': tokens.sales },
      data: { userId: 1 }
    });
  }
}

test.describe('CRM-AgentPilot product workflow', () => {
  test('renders the Agent execution process as a sales workbench', async ({ page }) => {
    await loginAs(page, 'sales');
    await page.goto('/agent');

    await expect(page.getByText('AI 执行过程')).toBeVisible();
    await expect(page.getByText('接收销售问题')).toBeVisible();
    await expect(page.getByText('选择执行路径')).toBeVisible();
    await expect(page.getByText('执行工具')).toBeVisible();
    await expect(page.getByText('输出结果')).toBeVisible();
    await expect(page.getByText('执行证据与确认中心')).toBeVisible();
  });

  test('carries URL context into Agent and keeps the sales role scoped', async ({ page }) => {
    const prompt = '帮我分析美家房产，重点看续费风险。';

    await loginAs(page, 'sales');
    await page.goto(`/agent?source=lead&customerId=1001&prompt=${encodeURIComponent(prompt)}`);

    await expect(page.getByText(/已带入上游业务上下文/)).toBeVisible();
    await expect(page.getByText(/客户上下文/)).toBeVisible();
    await expect(page.getByText('#1001')).toBeVisible();
    await expect(page.getByRole('textbox')).toHaveValue(prompt);
    await expect(page.locator('a[href="/system"]')).toHaveCount(0);
  });

  test('keeps system pages behind the admin role menu', async ({ page }) => {
    await loginAs(page, 'sales');
    await expect(page.locator('a[href="/system"]')).toHaveCount(0);

    await loginAs(page, 'admin');
    await expect(page.locator('a[href="/system"]')).toHaveCount(1);
    await page.locator('a[href="/system"]').click();
    await expect(page).toHaveURL(/\/system/);
  });

  test('runs the complete Agent confirmation route against a live backend', async ({ page }) => {
    test.skip(process.env.E2E_FULL_DEMO !== '1', 'Set E2E_FULL_DEMO=1 when a backend is running.');
    test.setTimeout(120_000);

    await loginAs(page, 'sales');
    await clearPendingConfirmations(page);

    await page.goto('/customers?customerId=1001');
    await expect(page.getByText(/先理解客户，再决定跟进动作/)).toBeVisible();
    await expect(page.getByText(/美家房产/).first()).toBeVisible();

    await page.goto('/leads?leadId=3001&customerId=1001');
    await expect(page.getByText(/先排优先级，再推动下一步动作/)).toBeVisible();
    await expect(page.getByText(/美家房产/).first()).toBeVisible();

    await page.goto('/agent?source=lead&customerId=1001&prompt=%E5%B8%AE%E6%88%91%E5%88%9B%E5%BB%BA%E6%98%8E%E5%A4%A9%E4%B8%8A%E5%8D%8810%E7%82%B9%E8%B7%9F%E8%BF%9B%E7%BE%8E%E5%AE%B6%E6%88%BF%E4%BA%A7%E7%BB%AD%E8%B4%B9%E7%9A%84%E4%BB%BB%E5%8A%A1%E3%80%82');

    await expect(page.getByText(/已带入上游业务上下文/)).toBeVisible();
    await page.getByRole('button', { name: /发送/ }).click();

    await expect(page.getByText(/AI 执行过程/)).toBeVisible();
    const confirmButton = page.locator('.confirmation-panel .ant-btn-primary, .pending-item .ant-btn-primary').first();
    await expect(confirmButton).toBeVisible({ timeout: 60_000 });
    await confirmButton.scrollIntoViewIfNeeded();
    await confirmButton.click();
    await expect(page.locator('.pending-item, .confirmation-panel')).toHaveCount(0, { timeout: 60_000 });

    await loginAs(page, 'admin');
    await page.goto('/runs');
    await expect(page.getByText(/Agent Run 审计列表/)).toBeVisible();
    await expect(page.getByText(/匹配记录/)).toBeVisible();
  });
});
