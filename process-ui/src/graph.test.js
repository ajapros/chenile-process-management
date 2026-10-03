import { it, expect } from 'vitest';
import { layoutGraph, timelineNodes } from './graph.js';

it('layers trigger, child, completion and successor relationships', () => {
  const nodes = ['trigger', 'root', 'child', 'event', 'successor'].map(id => ({ id }));
  const edges = [['trigger','root'], ['root','child'], ['root','event'], ['event','successor']].map(([source,target]) => ({ source,target }));
  const positions = new Map(layoutGraph(nodes, edges).map(n => [n.id,n]));
  expect(positions.get('root').x).toBeGreaterThan(positions.get('trigger').x);
  expect(positions.get('child').x).toEqual(positions.get('event').x);
  expect(positions.get('child').y).not.toEqual(positions.get('event').y);
  expect(positions.get('successor').x).toBeGreaterThan(positions.get('event').x);
});
it('orders timestamps by time rather than lexical fractional-second order', () => {
  const nodes = [{id:'later',time:'2026-10-02T10:00:00.110Z'}, {id:'unknown',time:null},
    {id:'earlier',time:'2026-10-02T10:00:00.1Z'}];
  expect(timelineNodes(nodes).map(n => n.id)).toEqual(['earlier','later','unknown']);
  expect(nodes[0].id).toBe('later');
});
it('handles disconnected, cyclic and missing legacy links without looping', () => {
  const nodes = ['a','b','c'].map(id => ({id}));
  const result = layoutGraph(nodes, [{source:'a',target:'b'}, {source:'b',target:'a'}, {source:'missing',target:'c'}]);
  expect(result).toHaveLength(3);
  expect(result.every(n => Number.isFinite(n.x) && Number.isFinite(n.y))).toBe(true);
});
