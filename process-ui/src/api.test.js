import { describe, it, expect, vi } from 'vitest';
import { createApi, query, jsonObject } from './api.js';

describe('management API client', () => {
  it('encodes identifiers and retains zero-valued pages', () => {
    expect(query('/trace', { triggerId: 'cron:one/two ?', page: 0, empty: '' })).toBe('/trace?triggerId=cron%3Aone%2Ftwo+%3F&page=0');
  });
  it('sends administrator and tenant headers without putting the key in the URL', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, text: async () => '{"accepted":true}' });
    vi.stubGlobal('fetch', fetch);
    await expect(createApi({ tenant: 'acme', key: 'secret' })('/triggers', { method: 'POST', body: { payload: {} } })).resolves.toEqual({ accepted: true });
    expect(fetch).toHaveBeenCalledWith('/process-management/api/triggers', expect.objectContaining({ method: 'POST',
      headers: expect.objectContaining({ 'X-Process-Management-Key': 'secret', 'x-chenile-tenant-id': 'acme' }) }));
  });
  it('reports API errors and malformed proxy responses', async () => {
    const fetch = vi.fn().mockResolvedValueOnce({ ok: false, text: async () => '{"message":"Wrong tenant"}' })
      .mockResolvedValueOnce({ ok: true, text: async () => '<html>UI, not API</html>' });
    vi.stubGlobal('fetch', fetch);
    const api = createApi({ tenant: 'acme', key: 'key' });
    await expect(api('/processes')).rejects.toThrow('Wrong tenant');
    await expect(api('/processes')).rejects.toThrow('proxy configuration');
  });
  it('requires JSON objects for argument/config forms', () => {
    expect(jsonObject('{"rows":2}')).toEqual({ rows: 2 });
    for (const bad of ['null', '[]', '42', 'no json']) expect(() => jsonObject(bad)).toThrow();
  });
});
