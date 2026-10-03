import { test, expect } from '@playwright/test';

const date = '2026-10-02T10:00:00Z';
const root = { id: 'import-1', processType: 'Import', status: 'PROCESSED', completedPercent: 100,
  createdAt: date, numSubProcesses: 2, numCompletedSubProcesses: 2, input: '{"source":"feed-A"}', output: '{"rows":100}', errors: [] };
const children = [1, 2].map(n => ({ ...root, id: `chunk-${n}`, processType: 'Chunk', parentId: root.id, numSubProcesses: 0, numCompletedSubProcesses: 0 }));
const successor = { ...root, id: 'index-1', processType: 'Index', predecessorId: root.id, status: 'EXECUTING', createdAt:'2026-10-02T10:01:01Z', completedPercent: 20, numSubProcesses: 0, numCompletedSubProcesses: 0 };
const event = { processId: root.id, generatedAt: '2026-10-02T10:01:00Z', payload: '{"processId":"import-1","successful":true}' };
const history = { id: 'run-1', triggerId: 'cron:daily:run-1', source: 'CRON', eventName: 'ProcessCreate', status: 'COMPLETED',
  startedAt: date, triggerTime: date, finishedAt: '2026-10-02T10:00:01Z', payload: '{"processDefName":"Import"}' };
const paged = content => ({ content, totalElements: content.length, page: 0, size: 25, totalPages: 1 });

test.beforeEach(async ({ page }) => {
  const definitions = ['Import', 'Chunk', 'Index'].map(processType => ({ processType, leaf: processType !== 'Import', predecessorArgs: 'BOTH', config: {} }));
  const schedules = [{ id: 'daily', name: 'Daily import', cronExpression: '0 0/5 * * * ?', timezone: 'UTC', enabled: true,
    eventName: 'ProcessCreate', eventPayloadJson: '{"processDefName":"Import","args":{"source":"feed-A"}}' }];
  await page.route('**/process-management/api/**', async route => {
    const url = new URL(route.request().url()), path = url.pathname.replace('/process-management/api', '');
    const method = route.request().method();
    expect(route.request().headers()['x-chenile-tenant-id']).toBe('acme');
    expect(route.request().headers()['x-process-management-key']).toBe('synthetic-browser-test-key');
    let body;
    if (path === '/info') body = { definitionSource: 'database', definitionsWritable: true, cronAvailable: true };
    else if (path === '/definitions') {
      if (method === 'POST') { body = route.request().postDataJSON(); definitions.push(body); } else body = definitions;
    } else if (path === '/crontabs') {
      if (method === 'POST') { body = { id: 'new-cron', ...route.request().postDataJSON(), eventName: 'ProcessCreate' }; schedules.push(body); }
      else body = paged(schedules);
    } else if (path.startsWith('/crontabs/')) body = { id: 'daily', ...route.request().postDataJSON() };
    else if (path === '/triggers') body = { triggerId: 'manual:acme:one', eventId: 'ProcessCreate', dispatched: true, duplicate: false };
    else if (path === '/executions') body = paged([history]);
    else if (path === '/processes') body = paged(url.searchParams.has('parentId') ? children : url.searchParams.has('predecessorId') ? [successor] : [root, successor]);
    else if (path.startsWith('/processes/')) body = { process: [root, ...children, successor].find(p => p.id === path.split('/').at(-1)), completionEvents: [event] };
    else if (path === '/trace') body = { nodes: [
      { id:'trigger:run-1', kind:'TRIGGER', label:'ProcessCreate · CRON', status:'COMPLETED', time:date, data:history },
      ...[root, ...children, successor].map(p => ({ id:`process:${p.id}`, kind:'PROCESS', label:p.processType, status:p.status, time:p.createdAt, data:p })),
      { id:'completion:import-1', kind:'COMPLETION', label:'ProcessCompleted', status:'GENERATED', time:event.generatedAt, data:event },
    ], edges:[
      { source:'trigger:run-1', target:'process:import-1', kind:'PROCESS_CREATE' },
      ...children.map(p => ({ source:'process:import-1', target:`process:${p.id}`, kind:'SUBPROCESS' })),
      { source:'process:import-1', target:'completion:import-1', kind:'EMITS' },
      { source:'completion:import-1', target:'process:index-1', kind:'SUCCESSOR' },
    ] };
    await route.fulfill({ status: method === 'POST' && (path === '/definitions' || path === '/crontabs') ? 201 : 200, json: body });
  });
  await page.goto('/');
  await page.getByLabel('Tenant', { exact: true }).fill('acme');
  await page.getByLabel('Administrator key').fill('synthetic-browser-test-key');
  await page.getByRole('button', { name: 'Connect', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Process dashboard' })).toBeVisible();
});

test('inspects children, completion events, successors and a complete execution', async ({ page }, testInfo) => {
  const errors = []; page.on('pageerror', e => errors.push(e.message));
  await page.getByRole('button', { name:'Import', exact:true }).click();
  await expect(page.getByRole('heading', { name:'Completion events' })).toBeVisible();
  await expect(page.getByRole('button', { name:'Chunk · chunk-1' })).toBeVisible();
  await expect(page.getByRole('button', { name:'Index · index-1' })).toBeVisible();
  await page.getByRole('button', { name:'View end-to-end execution →' }).click();
  await expect(page.getByRole('button', { name:'COMPLETION: ProcessCompleted' })).toBeVisible();
  await expect(page.getByRole('heading', { name:'Execution timeline' })).toBeVisible();
  await page.screenshot({ path: `/private/tmp/chenile-process-ui-${testInfo.project.name}.png`, fullPage:true });
  await page.getByRole('button', { name:'COMPLETION: ProcessCompleted' }).click();
  await expect(page.getByRole('heading', { name:'Completion events' })).toBeVisible();
  expect(errors).toEqual([]);
});

test('adds schedules and definitions and generates ProcessCreate events', async ({ page }) => {
  await page.getByRole('button', { name:'Definitions', exact:true }).click();
  await page.getByLabel('Process type', { exact:true }).fill('Export');
  await page.getByRole('button', { name:'Add definition', exact:true }).click();
  await expect(page.getByText('Process definition saved; local cache invalidated.')).toBeVisible();
  await expect(page.getByRole('cell', { name:'Export', exact:true })).toBeVisible();
  await page.getByRole('button', { name:'Cron triggers', exact:true }).click();
  const form = page.getByRole('heading', { name:'Add cron trigger' }).locator('..');
  await form.getByLabel('Name', { exact:true }).fill('Hourly export');
  await form.getByRole('combobox', { name:'Process definition', exact:true }).selectOption('Export');
  await form.getByRole('button', { name:'Save schedule' }).click();
  await expect(page.getByText('Cron trigger saved.')).toBeVisible();
  const manual = page.getByRole('heading', { name:'Generate an event now' }).locator('..');
  await manual.getByRole('combobox', { name:'Process definition', exact:true }).selectOption('Import');
  await manual.getByRole('button', { name:'Generate ProcessCreate' }).click();
  await expect(page.getByText('Trigger dispatched. Inspect Trigger history for its process and trace.')).toBeVisible();
  await page.getByRole('button', { name:'Trigger history', exact:true }).click();
  await expect(page.getByRole('cell', { name:'ProcessCreate CRON' })).toBeVisible();
  await page.getByRole('button', { name:'Processes & trace →' }).click();
  await expect(page.getByRole('heading', { name:'Execution timeline' })).toBeVisible();
});
