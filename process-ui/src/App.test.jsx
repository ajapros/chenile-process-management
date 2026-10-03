import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { it, expect, vi } from 'vitest';
import App from './App.jsx';

const page = content => ({ content, totalElements: content.length, page: 0, size: 25, totalPages: 1 });
const process = { id: 'p1', processType: 'Invoice', status: 'PROCESSED', completedPercent: 100, numSubProcesses: 1, numCompletedSubProcesses: 1 };
function mockApi() {
  vi.stubGlobal('fetch', vi.fn(async url => {
    let data;
    if (url.endsWith('/info')) data = { definitionSource: 'database', definitionsWritable: true, cronAvailable: true };
    else if (url.endsWith('/definitions')) data = [{ processType: 'Invoice', leaf: true, predecessorArgs: 'BOTH', config: {} }];
    else if (url.includes('/trace')) data = { nodes: [
      { id: 'process:p1', kind: 'PROCESS', label: 'Invoice', status: 'PROCESSED', time: '2026-10-02T10:00:00Z', data: process },
      { id: 'completion:p1', kind: 'COMPLETION', label: 'ProcessCompleted', status: 'GENERATED', time: '2026-10-02T10:01:00Z', data: { processId: 'p1' } },
    ], edges: [{ source: 'process:p1', target: 'completion:p1', kind: 'EMITS' }] };
    else if (url.endsWith('/processes/p1')) data = { process, completionEvents: [{ processId:'p1', generatedAt:'2026-10-02T10:01:00Z', payload:'{"processId":"p1"}' }] };
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
it('connects, shows process status, follows a trace and drills into completion history', async () => {
  mockApi(); render(<App/>); await connect();
  expect(screen.getByText('PROCESSED', { selector: '.badge' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'View trace →' }));
  await screen.findByRole('button', { name: 'COMPLETION: ProcessCompleted' });
  fireEvent.click(screen.getByRole('button', { name: 'COMPLETION: ProcessCompleted' }));
  await screen.findByRole('heading', { name: 'Completion events' });
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
