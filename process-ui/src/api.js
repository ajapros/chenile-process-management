export function query(path, filters = {}) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== '' && value !== undefined && value !== null) params.set(key, String(value));
  });
  return path + (params.size ? `?${params}` : '');
}

export function createApi({ tenant, key }) {
  return async function request(path, { method = 'GET', body, signal } = {}) {
    const response = await fetch(`/process-management/api${path}`, {
      method, signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(20000)]) : AbortSignal.timeout(20000), credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'X-Process-Management-Key': key, 'x-chenile-tenant-id': tenant },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    let data;
    try { data = text ? JSON.parse(text) : null; } catch { data = null; }
    if (!response.ok) throw new Error(data?.message || data?.detail || data?.error || `Request failed (${response.status})`);
    if (text && data === null) throw new Error('The API returned an unexpected response. Check the proxy configuration.');
    return data;
  };
}

export function jsonObject(text) {
  const parsed = JSON.parse(text);
  if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('Enter a JSON object, not a list or scalar.');
  return parsed;
}
