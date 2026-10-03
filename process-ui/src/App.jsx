import React, { useEffect, useMemo, useState } from 'react';
import { createApi, query, jsonObject } from './api.js';
import { layoutGraph, timelineNodes } from './graph.js';

const pretty = value => JSON.stringify(value, null, 2);
const time = value => value ? new Date(value).toLocaleString() : 'Not recorded';
const emptyDefinition = { processType: '', leaf: true, args: '', parentProcessType: '', predecessorProcessType: '', predecessorArgs: 'BOTH', config: {} };

function useResource(api, path, revision, poll = true) {
  const [result, setResult] = useState({ data: null, loading: true, error: '' });
  useEffect(() => {
    if (!path) { setResult({ data: null, loading: false, error: '' }); return; }
    let active = true, controller, inFlight = false;
    setResult({ data: null, loading: true, error: '' });
    const load = async () => {
      if (inFlight) return;
      inFlight = true; controller = new AbortController();
      try {
        const data = await api(path, { signal: controller.signal });
        if (active) setResult({ data, loading: false, error: '' });
      } catch (error) {
        if (active && error.name !== 'AbortError') setResult(r => ({ ...r, loading: false, error: error.message }));
      } finally { inFlight = false; }
    };
    load();
    const timer = poll ? setInterval(load, 10000) : null;
    return () => { active = false; controller?.abort(); if (timer) clearInterval(timer); };
  }, [api, path, revision, poll]);
  return result;
}

function Notice({ resource }) {
  if (resource.error) return <p className="notice error" role="alert">{resource.error}</p>;
  if (resource.loading) return <p className="notice" role="status">Loading…</p>;
  return null;
}
function Badge({ children }) {
  const value = String(children || 'UNKNOWN');
  const style = /ERROR|FAIL|DEAD/.test(value) ? 'bad' : /PROCESSED$|COMPLETED|GENERATED/.test(value) ? 'good' : 'active';
  return <span className={`badge ${style}`}>{value.replaceAll('_', ' ')}</span>;
}
function Json({ value }) { return <pre>{typeof value === 'string' ? value : pretty(value)}</pre>; }
function Pager({ data, page, setPage }) {
  if (!data) return null;
  return <div className="pager"><span>{data.totalElements} records · Page {page + 1} of {Math.max(1, data.totalPages)}</span>
    <button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
    <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button></div>;
}
function Table({ columns, rows, empty = 'No records yet.' }) {
  return <div className="table-wrap"><table><thead><tr>{columns.map(c => <th key={c.label}>{c.label}</th>)}</tr></thead>
    <tbody>{rows.map(row => <tr key={row.id || row.processType}>{columns.map(c => <td key={c.label}>{c.render(row)}</td>)}</tr>)}</tbody></table>
    {!rows.length && <p className="empty">{empty}</p>}</div>;
}

export default function App() {
  const [connection, setConnection] = useState(null);
  const [tenant, setTenant] = useState('default');
  const [key, setKey] = useState('');
  return <div className="shell"><aside><div className="brand"><span className="brand-mark">C</span><div>CHENILE<small>Process operations</small></div></div>
    <p className="aside-caption">OPERATIONS CONSOLE</p><p className="aside-note">Follow every trigger.<br/>Understand every process.</p>
    {connection && <div className="connection"><span className="live-dot"/> {connection.tenant}<button onClick={() => { setConnection(null); setKey(''); }}>Disconnect</button></div>}
    <div className="aside-footer">Framework-managed execution<br/>Cron → Process → Successor</div></aside>
    {!connection ? <main className="connect"><p className="eyebrow">PROCESS MANAGEMENT</p><h1>Your execution, connected.</h1>
      <p className="muted">Inspect schedules, define processes and follow execution from the first event to the last successor.</p>
      <form className="card connect-form" onSubmit={e => { e.preventDefault(); setConnection({ tenant, key }); setKey(''); }}>
        <h2>Connect to the API</h2><label>Tenant<input required pattern="[A-Za-z0-9_.-]{1,128}" value={tenant} onChange={e => setTenant(e.target.value)} /></label>
        <label>Administrator key<input required type="password" autoComplete="off" value={key} onChange={e => setKey(e.target.value)} /></label>
        <p className="muted">The key is held in memory only. This is an administrator console; the key grants access to all tenants. Use HTTPS.</p>
        <button className="primary">Connect</button></form></main>
      : <Console key={`${connection.tenant}:${connection.key}`} connection={connection} />}</div>;
}

function Console({ connection }) {
  const api = useMemo(() => createApi(connection), [connection]);
  const [section, setSection] = useState('Processes');
  const [revision, setRevision] = useState(0);
  const [selected, setSelected] = useState(null);
  const [traceQuery, setTraceQuery] = useState(null);
  const [executionFilter, setExecutionFilter] = useState({});
  const info = useResource(api, '/info', revision, false);
  const definitions = useResource(api, '/definitions', revision, false);
  const refresh = () => setRevision(n => n + 1);
  const inspect = id => { setSelected(id); setSection('Processes'); };
  const trace = filters => { setTraceQuery(filters); setSection('Execution'); };
  const history = filters => { setExecutionFilter(filters); setSection('Trigger history'); };
  const [mutation, setMutation] = useState({ busy: false, error: '', success: '' });
  const mutate = async (path, options, success) => {
    setMutation({ busy: true, error: '', success: '' });
    try { const result = await api(path, options); const message = typeof success === 'function' ? success(result) : success;
      setMutation({ busy: false, error: '', success: message }); refresh(); return result; }
    catch (error) { setMutation({ busy: false, error: error.message, success: '' }); throw error; }
  };
  const tabs = ['Processes', 'Cron triggers', 'Trigger history', 'Definitions', 'Execution'];
  return <main><header><div><p className="eyebrow">TENANT / {connection.tenant}</p><h1>Process operations</h1></div>
    <div className="header-actions"><span className="muted">Queries refresh every 10s</span><button onClick={refresh}>↻ Refresh</button></div></header>
    <nav aria-label="Sections">{tabs.map(tab => <button key={tab} className={section === tab ? 'selected' : ''} onClick={() => setSection(tab)}>{tab}</button>)}</nav>
    <Notice resource={info} />
    {mutation.error && <p className="notice error" role="alert">{mutation.error}</p>}
    {mutation.success && <p className="notice success" role="status">{mutation.success}</p>}
    {section === 'Processes' && <Processes api={api} revision={revision} selected={selected} inspect={inspect} close={() => setSelected(null)} trace={trace} />}
    {section === 'Cron triggers' && <Crons api={api} revision={revision} definitions={definitions} info={info.data} mutate={mutate} busy={mutation.busy} history={history} />}
    {section === 'Trigger history' && <Executions api={api} revision={revision} filters={executionFilter} reset={() => setExecutionFilter({})} trace={trace} />}
    {section === 'Definitions' && <Definitions resource={definitions} info={info.data} mutate={mutate} busy={mutation.busy} />}
    {section === 'Execution' && <Execution api={api} revision={revision} filters={traceQuery} setFilters={setTraceQuery} inspect={inspect} history={history} />}
  </main>;
}

function Processes({ api, revision, selected, inspect, close, trace }) {
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState({ processType: '', status: '' });
  const list = useResource(api, query('/processes', { ...filters, page }), revision);
  return <><div className="section-heading"><div><h2>Process dashboard</h2><p className="muted">All processes, including subprocesses and completion-driven successors.</p></div></div>
    <div className="filters"><label>Process type<input value={filters.processType} placeholder="All types" onChange={e => { setFilters({ ...filters, processType: e.target.value }); setPage(0); }} /></label>
      <label>Status<select value={filters.status} onChange={e => { setFilters({ ...filters, status: e.target.value }); setPage(0); }}><option value="">All states</option>
        {['EXECUTING', 'SPLIT_PENDING', 'SUB_PROCESSES_PENDING', 'AGGREGATION_PENDING', 'PROCESSED', 'PROCESSED_WITH_ERRORS'].map(s => <option key={s}>{s}</option>)}</select></label></div>
    <Notice resource={list} />{list.data && <><Table rows={list.data.content} columns={[
      { label: 'Process', render: p => <><button className="link" onClick={() => inspect(p.id)}>{p.processType}</button><small className="mono">{p.id}</small></> },
      { label: 'Status', render: p => <Badge>{p.status}</Badge> },
      { label: 'Progress', render: p => <><progress max="100" value={p.completedPercent}/><small>{p.completedPercent}% · {p.numCompletedSubProcesses}/{p.numSubProcesses} children</small></> },
      { label: 'Created', render: p => time(p.createdAt) },
      { label: 'Execution', render: p => <button className="link" onClick={() => trace({ processId: p.id })}>View trace →</button> },
    ]} /><Pager data={list.data} page={page} setPage={setPage}/></>}
    {selected && <ProcessDetail key={selected} api={api} revision={revision} id={selected} inspect={inspect} close={close} trace={trace} />}</>;
}

function ProcessDetail({ api, revision, id, inspect, close, trace }) {
  const detail = useResource(api, `/processes/${encodeURIComponent(id)}`, revision);
  const [childrenPage, setChildrenPage] = useState(0), [successorsPage, setSuccessorsPage] = useState(0);
  const children = useResource(api, query('/processes', { parentId: id, page: childrenPage }), revision);
  const successors = useResource(api, query('/processes', { predecessorId: id, page: successorsPage }), revision);
  const related = (title, resource, page, setPage) => <section><h3>{title}</h3><Notice resource={resource}/>{resource.data && <>
    <Table rows={resource.data.content} columns={[{ label: 'Process', render: p => <button className="link" onClick={() => inspect(p.id)}>{p.processType} · {p.id}</button> },
      { label: 'Status', render: p => <Badge>{p.status}</Badge> }]} /><Pager data={resource.data} page={page} setPage={setPage}/></>}</section>;
  const p = detail.data?.process;
  return <section className="card detail"><div className="section-heading"><h2>Process detail</h2><button onClick={close}>Close detail</button></div><Notice resource={detail}/>
    {p && <><h3>{p.processType} <Badge>{p.status}</Badge></h3><p className="mono">{p.id}</p><p>{p.description}</p>
      <div className="lineage">{p.parentId && <button className="link" onClick={() => inspect(p.parentId)}>Parent: {p.parentId}</button>}
        {p.predecessorId && <button className="link" onClick={() => inspect(p.predecessorId)}>Predecessor: {p.predecessorId}</button>}
        <button className="link" onClick={() => trace({ processId: id })}>View end-to-end execution →</button></div>
      <div className="json-grid"><div><h3>Input arguments</h3><Json value={p.input}/></div><div><h3>Output arguments</h3><Json value={p.output}/></div></div>
      {p.errors?.length > 0 && <details><summary>Process errors ({p.errors.length})</summary><Json value={p.errors}/></details>}
      <h3>Completion events</h3>{!detail.data.completionEvents.length && <p className="muted">No recorded completion event. Older executions may predate event history.</p>}
      {detail.data.completionEvents.map(e => <div className="event" key={e.processId}><strong>ProcessCompleted</strong><span>Generated {time(e.generatedAt)}</span>
        <details><summary>Event payload</summary><Json value={e.payload}/></details></div>)}
      {related('Subprocesses', children, childrenPage, setChildrenPage)}{related('Successors from ProcessCompleted', successors, successorsPage, setSuccessorsPage)}
    </>}</section>;
}

function PayloadFields({ names, processType, setProcessType, args, setArgs }) {
  return <><label>Process definition<select required value={processType} onChange={e => setProcessType(e.target.value)}><option value="">Choose a definition</option>
    {names.map(n => <option key={n}>{n}</option>)}</select></label><label>Input arguments (JSON object)<textarea required value={args} onChange={e => setArgs(e.target.value)} spellCheck="false" /></label></>;
}
function Crons({ api, revision, definitions, info, mutate, busy, history }) {
  const [page, setPage] = useState(0), [editing, setEditing] = useState(null);
  const [name, setName] = useState(''), [cron, setCron] = useState('0 0/5 * * * ?'), [zone, setZone] = useState('UTC'), [enabled, setEnabled] = useState(true);
  const [processType, setProcessType] = useState(''), [args, setArgs] = useState('{}'), [error, setError] = useState('');
  const [manualKey, setManualKey] = useState(() => crypto.randomUUID());
  const [manualType, setManualType] = useState(''), [manualArgs, setManualArgs] = useState('{}');
  const list = useResource(api, query('/crontabs', { page }), revision);
  const names = (definitions.data || []).map(d => d.processType);
  const payload = (type, text) => ({ processDefName: type, args: jsonObject(text) });
  const save = async e => { e.preventDefault(); setError(''); try {
    await mutate(editing ? `/crontabs/${encodeURIComponent(editing)}` : '/crontabs', { method: editing ? 'PUT' : 'POST',
      body: { name, cronExpression: cron, timezone: zone, enabled, payload: payload(processType, args) } }, 'Cron trigger saved.');
    setEditing(null); setName('');
  } catch (e) { setError(e.message); } };
  const edit = c => { try {
    const p = JSON.parse(c.eventPayloadJson || '{}');
    setEditing(c.id); setName(c.name); setCron(c.cronExpression); setZone(c.timezone); setEnabled(c.enabled);
    setProcessType(p.processDefName || ''); setArgs(pretty(p.args || {})); setError('');
  } catch { setError('This schedule has an invalid JSON payload; repair it before editing.'); } };
  return <><div className="section-heading"><div><h2>Cron triggers</h2><p className="muted">Schedule ProcessCreate events using Quartz cron expressions (seconds included).</p></div></div>
    {info && !info.cronAvailable && <p className="notice">Quartz is not configured in this host. Existing schedules are readable; schedule updates are unavailable.</p>}
    <Notice resource={list}/>{list.data && <><Table rows={list.data.content} columns={[
      { label: 'Schedule', render: c => <><strong>{c.name}</strong><small className="mono">{c.cronExpression} · {c.timezone}</small></> },
      { label: 'Event', render: c => c.eventName }, { label: 'Enabled', render: c => c.enabled ? 'Yes' : 'No' },
      { label: 'Actions', render: c => <div className="row-actions"><button onClick={() => history({ crontabId: c.id })}>History</button>
        <button disabled={c.eventName !== 'ProcessCreate' || !info?.cronAvailable} onClick={() => edit(c)}>Edit</button></div> },
    ]}/><Pager data={list.data} page={page} setPage={setPage}/></>}
    <Notice resource={definitions}/>{error && <p className="notice error" role="alert">{error}</p>}
    <div className="form-grid"><form className="card" onSubmit={save}><h3>{editing ? 'Edit cron trigger' : 'Add cron trigger'}</h3>
      <label>Name<input required value={name} onChange={e => setName(e.target.value)}/></label>
      <label>Quartz cron expression<input required className="mono" value={cron} onChange={e => setCron(e.target.value)}/></label>
      <label>Timezone<input required value={zone} onChange={e => setZone(e.target.value)}/></label>
      <PayloadFields names={names} processType={processType} setProcessType={setProcessType} args={args} setArgs={setArgs}/>
      <label className="checkbox"><input type="checkbox" checked={enabled} onChange={e => setEnabled(e.target.checked)}/> Enabled</label>
      <button className="primary" disabled={busy || !info?.cronAvailable}>Save schedule</button>
      {editing && <button type="button" onClick={() => { setEditing(null); setName(''); }}>Cancel edit</button>}</form>
      <form className="card" onSubmit={async e => { e.preventDefault(); setError(''); try {
        await mutate('/triggers', { method: 'POST', body: { triggerId: manualKey, payload: payload(manualType, manualArgs) } }, result => {
          if (!result.dispatched) throw new Error('This key was already claimed but dispatch did not complete. Inspect its history; retrying the same key cannot replay it.');
          return result.duplicate ? 'This trigger was already dispatched. No duplicate process was created.' : 'Trigger dispatched. Inspect Trigger history for its process and trace.';
        });
        setManualKey(crypto.randomUUID());
      } catch (e) { setError(e.message); } }}><h3>Generate an event now</h3><p className="muted">Uses the same trigger ingress and idempotency as scheduled events.</p>
        <PayloadFields names={names} processType={manualType} setProcessType={setManualType} args={manualArgs} setArgs={setManualArgs}/>
        <label>Request / retry key<input required maxLength="80" value={manualKey} onChange={e => setManualKey(e.target.value)}/></label>
        <button type="button" disabled={busy} onClick={() => setManualKey(crypto.randomUUID())}>New request key</button>
        <p className="muted">Event: <strong>ProcessCreate</strong>. A retry uses the same key until this request succeeds.</p><button className="primary" disabled={busy}>Generate ProcessCreate</button></form></div></>;
}

function Executions({ api, revision, filters, reset, trace }) {
  const [page, setPage] = useState(0), [status, setStatus] = useState('');
  const [expanded, setExpanded] = useState(null);
  const list = useResource(api, query('/executions', { ...filters, status, page }), revision);
  return <><div className="section-heading"><div><h2>Trigger history</h2><p className="muted">All trigger sources. Completion means event dispatch finished, not that the resulting process finished.</p></div></div>
    <div className="filters"><label>Dispatch status<select value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
      <option value="">All statuses</option>{['RUNNING', 'COMPLETED', 'FAILED'].map(s => <option key={s}>{s}</option>)}</select></label>
      {Object.keys(filters).length > 0 && <button onClick={() => { reset(); setPage(0); }}>Clear schedule/trigger filter</button>}</div>
    <Notice resource={list}/>{list.data && <><Table rows={list.data.content} columns={[
      { label: 'Started', render: e => <>{time(e.startedAt)}<small>Finished: {time(e.finishedAt)}</small></> },
      { label: 'Event / source', render: e => <><strong>{e.eventName}</strong><small>{e.source}</small></> },
      { label: 'Trigger', render: e => <button className="link mono" onClick={() => setExpanded(expanded === e.id ? null : e.id)}>{e.triggerId}</button> },
      { label: 'Status', render: e => <Badge>{e.status}</Badge> },
      { label: 'Execution', render: e => <button className="link" onClick={() => trace({ triggerId: e.triggerId })}>Processes & trace →</button> },
    ]}/><Pager data={list.data} page={page} setPage={setPage}/>
      {list.data.content.filter(e => e.id === expanded).map(e => <div className="card" key={e.id}><h3>{e.eventName} · {e.triggerId}</h3><p>Scheduled/received: {time(e.triggerTime)}</p>
        {e.error && <p className="notice error">{e.error}</p>}<Json value={e.payload}/></div>)}</>}</>;
}

function Definitions({ resource, info, mutate, busy }) {
  const [form, setForm] = useState({ ...emptyDefinition }), [config, setConfig] = useState('{}');
  const [editing, setEditing] = useState(false), [error, setError] = useState('');
  const update = (key, value) => setForm(f => ({ ...f, [key]: value }));
  return <><div className="section-heading"><div><h2>Process definitions</h2><p className="muted">Global definitions · {info?.definitionSource || '…'} configurator. Worker implementations must already exist in the host application.</p></div></div>
    <Notice resource={resource}/>{resource.data && <Table rows={resource.data} columns={[
      { label: 'Type', render: d => <strong>{d.processType}</strong> }, { label: 'Shape', render: d => d.leaf ? 'Leaf / executor' : 'Composite / splitter + aggregator' },
      { label: 'Predecessor', render: d => d.predecessorProcessType || '—' }, { label: 'Arguments', render: d => d.predecessorArgs || 'BOTH' },
      { label: 'Inspect / edit', render: d => <button onClick={() => { setForm({ ...emptyDefinition, ...d }); setConfig(pretty(d.config || {})); setEditing(true); setError(''); }}>Open</button> },
    ]}/>}
    {!info?.definitionsWritable && <p className="notice">JSON-configured definitions are read-only. Use chenile.process.configurator=database to add or update definitions.</p>}
    <form className="card definition-form" onSubmit={async e => { e.preventDefault(); setError(''); try {
      const values = jsonObject(config);
      if (Object.values(values).some(v => typeof v !== 'string')) throw new Error('Configuration values must be strings.');
      await mutate(editing ? `/definitions/${encodeURIComponent(form.processType)}` : '/definitions', { method: editing ? 'PUT' : 'POST', body: { ...form, config: values } }, 'Process definition saved; local cache invalidated.');
      setEditing(false); setForm({ ...emptyDefinition }); setConfig('{}');
    } catch (e) { setError(e.message); } }}><div className="section-heading"><h3>{editing ? 'Inspect / edit definition' : 'Add definition'}</h3>
      {editing && <button type="button" onClick={() => { setEditing(false); setForm({ ...emptyDefinition }); setConfig('{}'); }}>New definition</button>}</div>
      {error && <p className="notice error" role="alert">{error}</p>}<fieldset disabled={!info?.definitionsWritable || busy}>
      <div className="form-grid"><label>Process type<input required readOnly={editing} value={form.processType} onChange={e => update('processType', e.target.value)}/></label>
        <label>Arguments descriptor<input value={form.args || ''} onChange={e => update('args', e.target.value)}/></label>
        <label>Parent process type<input value={form.parentProcessType || ''} onChange={e => update('parentProcessType', e.target.value)}/></label>
        <label>Predecessor process type<input value={form.predecessorProcessType || ''} onChange={e => update('predecessorProcessType', e.target.value)}/></label>
        <label>Arguments from predecessor<select value={form.predecessorArgs} onChange={e => update('predecessorArgs', e.target.value)}>{['INPUT', 'OUTPUT', 'BOTH'].map(v => <option key={v}>{v}</option>)}</select></label>
        <label className="checkbox"><input type="checkbox" checked={form.leaf} onChange={e => update('leaf', e.target.checked)}/> Leaf process</label></div>
        <label>Configuration (JSON object of string values)<textarea value={config} onChange={e => setConfig(e.target.value)} spellCheck="false"/></label>
        <button className="primary">{editing ? 'Update definition' : 'Add definition'}</button></fieldset></form></>;
}

function Execution({ api, revision, filters, setFilters, inspect, history }) {
  const [kind, setKind] = useState('processId'), [value, setValue] = useState('');
  const resource = useResource(api, filters ? query('/trace', filters) : null, revision);
  const positioned = layoutGraph(resource.data?.nodes || [], resource.data?.edges || []);
  const positions = new Map(positioned.map(n => [n.id, n]));
  const width = Math.max(760, ...positioned.map(n => n.x + 240)), height = Math.max(160, ...positioned.map(n => n.y + 104));
  const open = n => { if (n.kind === 'PROCESS') inspect(n.data.id); else if (n.kind === 'COMPLETION') inspect(n.data.processId); else history({ triggerId: n.data.triggerId }); };
  return <><div className="section-heading"><div><h2>End-to-end execution</h2><p className="muted">Trigger → root process → subprocesses → completion event → successors. Select any node to inspect it.</p></div></div>
    <form className="trace-search" onSubmit={e => { e.preventDefault(); setFilters({ [kind]: value.trim() }); }}>
      <select aria-label="Trace identifier type" value={kind} onChange={e => setKind(e.target.value)}><option value="processId">Process ID</option><option value="triggerId">Trigger ID</option></select>
      <input aria-label="Trace identifier" required placeholder="Enter an identifier, or open a trace from a process" value={value} onChange={e => setValue(e.target.value)}/><button className="primary">Trace execution</button></form>
    {filters && <p className="mono muted">{Object.entries(filters).map(([k,v]) => `${k}: ${v}`).join(' · ')}</p>}
    <Notice resource={resource}/>{!filters && <div className="empty card">Choose a process or trigger to follow its complete execution.</div>}
    {resource.data && <><div className="graph" aria-label="Execution graph"><svg viewBox={`0 0 ${width} ${height}`} width={width} height={height} role="img" aria-label="Causal execution connections">
      <defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#718397"/></marker></defs>
      {resource.data.edges.map((e,i) => { const a = positions.get(e.source), b = positions.get(e.target); if (!a || !b) return null;
        return <g key={`${e.source}:${e.target}:${i}`}><path d={`M${a.x + 222},${a.y + 44} C${a.x + 245},${a.y + 44} ${b.x - 25},${b.y + 44} ${b.x},${b.y + 44}`} fill="none" stroke="#718397" markerEnd="url(#arrow)"/>
          <title>{e.kind.replaceAll('_', ' ')}</title></g>; })}
      {positioned.map(n => <g key={n.id} role="button" tabIndex="0" aria-label={`${n.kind}: ${n.label}`} className={`graph-node ${n.kind.toLowerCase()}`} onClick={() => open(n)} onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(n); } }}>
        <rect x={n.x} y={n.y} width="222" height="88" rx="10"/><text x={n.x + 14} y={n.y + 22} className="node-kind">{n.kind}</text>
        <text x={n.x + 14} y={n.y + 44}>{n.label.length > 25 ? `${n.label.slice(0, 24)}…` : n.label}</text>
        <text x={n.x + 14} y={n.y + 68} className="node-status">{n.status?.replaceAll('_', ' ')}</text><title>{n.id}</title></g>)}
    </svg></div><div className="legend"><span>● Trigger</span><span>● Process</span><span>● Completion event</span><span>{positioned.length} nodes · {resource.data.edges.length} causal links</span></div>
      <h3>Execution timeline</h3><ol className="timeline">{timelineNodes(positioned).map(n => <li key={n.id}><span className="timeline-time">{time(n.time)}</span>
        <button className="link" onClick={() => open(n)}>{n.label}</button><small>{n.kind} · {n.status} · {n.id}</small></li>)}</ol>
      <p className="muted">Completion timestamps reflect generation, not subscriber delivery. Legacy executions may have lineage without recorded event history.</p></>}
  </>;
}
