/** Layer a directed causal graph. Cycle-safe, including legacy malformed lineage. */
export function layoutGraph(nodes, edges) {
  const ids = new Set(nodes.map(n => n.id));
  const incoming = new Map(nodes.map(n => [n.id, 0]));
  const outgoing = new Map(nodes.map(n => [n.id, []]));
  const depth = new Map(nodes.map(n => [n.id, 0]));
  for (const edge of edges) if (ids.has(edge.source) && ids.has(edge.target)) {
    incoming.set(edge.target, incoming.get(edge.target) + 1);
    outgoing.get(edge.source).push(edge.target);
  }
  const queue = nodes.filter(n => incoming.get(n.id) === 0).map(n => n.id);
  for (let i = 0; i < queue.length; i++) {
    const id = queue[i];
    for (const target of outgoing.get(id)) {
      depth.set(target, Math.max(depth.get(target), depth.get(id) + 1));
      incoming.set(target, incoming.get(target) - 1);
      if (incoming.get(target) === 0) queue.push(target);
    }
  }
  const rows = new Map();
  return nodes.map(node => {
    const column = depth.get(node.id);
    const row = rows.get(column) || 0;
    rows.set(column, row + 1);
    return { ...node, x: 24 + column * 260, y: 24 + row * 118 };
  });
}

export function timelineNodes(nodes) {
  const timestamp = node => node.time && Number.isFinite(Date.parse(node.time)) ? Date.parse(node.time) : Infinity;
  return [...nodes].sort((a, b) => timestamp(a) - timestamp(b));
}
