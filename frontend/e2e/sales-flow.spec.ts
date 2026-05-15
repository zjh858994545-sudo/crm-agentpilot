import { expect, test } from '@playwright/test';

test.describe('CRM-AgentPilot sales workflow', () => {
  test('opens customer and lead details from URL and carries context into Agent', async ({ page }) => {
    await page.goto('/customers?customerId=1001');
    await expect(page.getByRole('heading', { name: /先理解客户/ })).toBeVisible();
    await expect(page.getByText(/客户详情 · 美家房产/)).toBeVisible();
    await expect(page.getByText(/让 Agent 分析/).last()).toBeVisible();

    await page.goto('/leads?leadId=3001&customerId=1001');
    await expect(page.getByRole('heading', { name: /先排优先级/ })).toBeVisible();
    await expect(page.getByText(/商机解释 · 美家房产/)).toBeVisible();
    await expect(page.getByText(/推荐处理路径/)).toBeVisible();

    await page.getByRole('button', { name: /让 Agent 处理/ }).last().click();
    await expect(page).toHaveURL(/\/agent\?.*customerId=1001/);
    await expect(page.getByText(/已带入上游业务上下文/)).toBeVisible();
    await expect(page.getByRole('textbox')).toHaveValue(/美家房产/);
  });

  test('renders the workbench, confirmation center, and admin links', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /今天先处理谁/ })).toBeVisible();
    await expect(page.getByText(/待确认写入/).first()).toBeVisible();
    await expect(page.getByText(/业务闭环/)).toBeVisible();

    await page.getByRole('link', { name: /运行审计/ }).click();
    await expect(page.getByRole('heading', { name: /运行审计/ })).toBeVisible();

    await page.getByRole('link', { name: /质量评估/ }).click();
    await expect(page.getByRole('heading', { name: /质量评估/ })).toBeVisible();
  });
});
