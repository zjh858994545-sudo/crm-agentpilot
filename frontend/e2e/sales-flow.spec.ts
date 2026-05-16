import { expect, test } from '@playwright/test';

async function loginAs(page, testId: 'login-sales' | 'login-manager' | 'login-admin') {
  await page.goto('/');
  await page.getByTestId(testId).click();
}

test.describe('CRM-AgentPilot product workflow', () => {
  test('renders the Agent execution process as a sales workbench', async ({ page }) => {
    await loginAs(page, 'login-sales');
    await page.goto('/agent');

    await expect(page.getByText('Agent 执行过程')).toBeVisible();
    await expect(page.getByText('接收销售问题')).toBeVisible();
    await expect(page.getByText('选择执行路径')).toBeVisible();
    await expect(page.getByText('执行工具')).toBeVisible();
    await expect(page.getByText('输出结果')).toBeVisible();
    await expect(page.getByText('执行证据与确认中心')).toBeVisible();
  });

  test('carries URL context into Agent and keeps the sales role scoped', async ({ page }) => {
    const prompt = '帮我分析美家房产，重点看续费风险。';

    await loginAs(page, 'login-sales');
    await page.goto(`/agent?source=lead&customerId=1001&prompt=${encodeURIComponent(prompt)}`);

    await expect(page.getByText(/已带入上游业务上下文/)).toBeVisible();
    await expect(page.getByText(/客户上下文/)).toBeVisible();
    await expect(page.getByText('#1001')).toBeVisible();
    await expect(page.getByRole('textbox')).toHaveValue(prompt);
    await expect(page.locator('a[href="/system"]')).toHaveCount(0);
  });

  test('keeps system pages behind the admin role menu', async ({ page }) => {
    await loginAs(page, 'login-sales');
    await expect(page.locator('a[href="/system"]')).toHaveCount(0);

    await page.getByTestId('identity-switcher').click();
    await page.getByTitle(/系统管理员/).click();
    await expect(page.locator('a[href="/system"]')).toHaveCount(1);
    await page.locator('a[href="/system"]').click();
    await expect(page).toHaveURL(/\/system/);
  });

  test('runs the complete Agent confirmation route against a live backend', async ({ page }) => {
    test.skip(process.env.E2E_FULL_DEMO !== '1', 'Set E2E_FULL_DEMO=1 when a backend is running.');

    await loginAs(page, 'login-sales');
    await page.goto('/agent?source=lead&customerId=1001&prompt=%E5%B8%AE%E6%88%91%E5%88%9B%E5%BB%BA%E6%98%8E%E5%A4%A9%E4%B8%8A%E5%8D%8810%E7%82%B9%E8%B7%9F%E8%BF%9B%E7%BE%8E%E5%AE%B6%E6%88%BF%E4%BA%A7%E7%BB%AD%E8%B4%B9%E7%9A%84%E4%BB%BB%E5%8A%A1%E3%80%82');

    await expect(page.getByText(/已带入上游业务上下文/)).toBeVisible();
    await page.getByRole('button', { name: /发送/ }).click();

    await expect(page.getByText(/待确认写操作/).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Agent 执行过程/)).toBeVisible();
    await expect(page.getByText(/确认写入 CRM/)).toBeVisible();
    await page.getByRole('button', { name: /确认写入 CRM/ }).click();
    await expect(page.getByText(/已确认|已完成/).first()).toBeVisible({ timeout: 30_000 });
  });
});
