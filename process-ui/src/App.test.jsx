import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { it, expect, vi } from 'vitest';
import App from './App.jsx';

const page = content => ({ content, totalElements: content.length, page: 0, size: 25, totalPages: 1 });
const process = { id: 'p1', processType: 'Invoice', status: 'PROCESSED', completedPercent: 100, numSubProcesses: 1, numCompletedSubProcesses: 1, completedAt: '2026-10-02T10:01:00Z' };
function mockApi() {
  vi.stubGlobal('fetch', vi.fn(async url => {
    let data;
    if (url.endsWith('/info')) data = { definitionSource: 'database', definitionsWritable: true, cronAvailable: true };
    else if (url.endsWith('/definitions')) data = [{ processType: 'Invoice', leaf: true, predecessorArgs: 'BOTH', config: {} }];
    else if (url.includes('/trace')) data = { nodes: [
      { id: 'process:p1', kind: 'PROCESS', label: 'Invoice', status: 'PROCESSED', time: '2026-10-02T10:00:00Z', data: process },
    ], edges: [] };
    else if (url.endsWith('/processes/p1')) data = { process };
    else if (url.includes('/processes?') && !url.includes('parentId') && !url.includes('predecessorId')) data = page([process]);
    else data = page([]);
    return { ok: true, text: async () => JSON.stringify(data) };
  }));
}
async function connect() {
  fireEvent.change(screen.getByLabelText('Administrator key'), { target: { value: 'not-persisted-key' } });
  fireEvent.click(screen.getByRole('button', { name: 'Connect', exact: true }));
  await screen.findByText('Invoice');
}
it('connects, shows process status, follows a trace and inspects process completion', async () => {
  mockApi(); render(<App/>); await connect();
  expect(screen.getByText('PROCESSED', { selector: '.badge' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'View trace →' }));
  await screen.findByRole('button', { name: 'PROCESS: Invoice' });
  fireEvent.click(screen.getByRole('button', { name: 'PROCESS: Invoice' }));
  await screen.findByRole('heading', { name: 'Completion' });
  expect(screen.getByText(/^Completed /)).toBeInTheDocument();
  expect(screen.queryByText('Event payload')).not.toBeInTheDocument();
  expect(screen.getByText('Subprocesses')).toBeInTheDocument();
  expect(screen.getByText('Successors from ProcessCompleted')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Disconnect' }));
  await screen.findByLabelText('Administrator key');
  expect(screen.getByLabelText('Administrator key')).toHaveValue('');
  expect(localStorage.length).toBe(0);
});
it('shows authentication failures instead of a misleading empty dashboard', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 401, text: async () => '{"message":"A valid administrator key is required"}' })));
  render(<App/>);
  fireEvent.change(screen.getByLabelText('Administrator key'), { target: { value: 'wrong' } });
  fireEvent.click(screen.getByRole('button', { name: 'Connect', exact: true }));
  await waitFor(() => expect(screen.getAllByRole('alert').some(e => e.textContent.includes('administrator key'))).toBe(true));
});
it('builds a weekly Quartz expression while keeping the expression field editable', async () => {
  mockApi(); render(<App/>); await connect();
  fireEvent.click(screen.getByRole('button', { name:'Cron triggers', exact:true }));
  fireEvent.click(screen.getByText('Build a Quartz expression'));
  fireEvent.change(screen.getByLabelText('Helper schedule'), { target: { value: 'weekly' } });
  fireEvent.change(screen.getByLabelText('Schedule minute'), { target: { value: '30' } });
  fireEvent.change(screen.getByLabelText('Schedule hour'), { target: { value: '14' } });
  fireEvent.change(screen.getByLabelText('Schedule weekday'), { target: { value: 'FRI' } });
  fireEvent.click(screen.getByRole('button', { name:'Use expression: 0 30 14 ? * FRI' }));
  expect(screen.getByLabelText('Quartz cron expression')).toHaveValue('0 30 14 ? * FRI');
  fireEvent.change(screen.getByLabelText('Quartz cron expression'), { target: { value: '0 0/10 * * * ?' } });
  expect(screen.getByLabelText('Quartz cron expression')).toHaveValue('0 0/10 * * * ?');
});
