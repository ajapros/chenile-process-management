/**
 * These tests demonstrate the process manager with a very fast child. (in fact with a
 * synchronous child.)<br/>
 * This is not a typical situation. But it is a good test since it will exacerbate race conditions.<br/>
 * The workers will instantly end before the process manager continues since it all happens in the same VM
 * and is synchronous.
 */
package org.chenile.orchestrator.process.test;