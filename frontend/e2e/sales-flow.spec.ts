import { expect, test } from '@playwright/test';

test.describe('CRM-AgentPilot sales workflow', () => {
  test('opens customer and lead details from URL and carries context into Agent', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText(/CRM-AgentPilot/).first()).toBeVisible();

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

    await page.getByRole('link', { name: '系统能力', exact: true }).click();
    await expect(page.getByRole('heading', { name: /系统能力/ })).toBeVisible();
    await expect(page.getByText(/产品化能力清单/)).toBeVisible();

    await page.getByRole('link', { name: '运行审计', exact: true }).click();
    await expect(page.getByRole('heading', { name: /运行审计/ })).toBeVisible();

    await page.getByRole('link', { name: '质量评估', exact: true }).click();
    await expect(page.getByRole('heading', { name: /质量评估/ })).toBeVisible();
  });

  test('runs the complete demo route against a live backend', async ({ page }) => {
    test.skip(process.env.E2E_FULL_DEMO !== '1', 'Set E2E_FULL_DEMO=1 when a backend is running.');

    await page.goto('/');
    await expect(page.getByRole('heading', { name: /今天先处理谁/ })).toBeVisible();
    await expect(page.getByText(/业务闭环/)).toBeVisible();

    await page.goto('/customers?customerId=1001');
    await expect(page.getByRole('heading', { name: /先理解客户/ })).toBeVisible();
    await expect(page.getByText(/客户详情 · 美家房产/)).toBeVisible();
    await expect(page.getByText('跟进时间线', { exact: true })).toBeVisible();

    await page.goto('/leads?leadId=3001&customerId=1001');
    await expect(page.getByRole('heading', { name: /先排优先级/ })).toBeVisible();
    await expect(page.getByText(/商机解释 · 美家房产/)).toBeVisible();
    await page.getByRole('button', { name: /让 Agent 处理/ }).last().click();

    await expect(page).toHaveURL(/\/agent\?.*customerId=1001/);
    await expect(page.getByText(/已带入上游业务上下文/)).toBeVisible();
    await page.getByRole('textbox').fill('帮我创建明天上午10点跟进美家房产续费的任务。');
    await page.getByRole('button', { name: /发送/ }).click();

    await expect(page.getByText(/待确认写操作/).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole('button', { name: /确认写入 CRM/ })).toBeVisible();
    await page.getByRole('button', { name: /确认写入 CRM/ }).click();
    await expect(page.getByText(/已确认/).first()).toBeVisible({ timeout: 30_000 });

    await page.getByRole('link', { name: '运行审计', exact: true }).click();
    await expect(page.getByRole('heading', { name: /运行审计/ })).toBeVisible();
    await expect(page.getByText(/Agent Run 审计列表/)).toBeVisible();
    await expect(page.getByText(/Run 总数/)).toBeVisible();

    await page.getByRole('link', { name: '质量评估', exact: true }).click();
    await expect(page.getByRole('heading', { name: /质量评估/ })).toBeVisible();
    await page.getByRole('button', { name: /运行评测/ }).click();
    await expect(page.getByText(/本次报告/)).toBeVisible({ timeout: 60_000 });
    await expect(page.getByRole('cell', { name: /RAG Recall@5/ }).first()).toBeVisible();
  });
});
