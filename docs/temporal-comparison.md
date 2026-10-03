# Chenile Process Management vs Temporal — Guide

Both Chenile Process Management and Temporal provide durable, long-running orchestration with retries and
horizontally scaled workers, but from opposite philosophies. This guide compares them so you can decide
which fits a given problem.

## At a glance

Chenile and Temporal solve the same problem — reliably running long, multi-step work that must survive
crashes — through opposite models. Chenile expresses orchestration **as data**: a state machine plus
process rows in the application's own database, with a transactional outbox making each transition's
consequences durable. Temporal expresses orchestration **as code**: an ordinary-looking workflow function
that Temporal makes crash-proof by event-sourcing and deterministically replaying it on a dedicated
cluster.

For the orchestration Chenile models — worker dispatch, child creation, parent signalling, completion, and
chaining — the two are in the same class on durability and exactly-once semantics; effects that are neither
transactional on the shared database nor idempotent at their receiver are at-least-once in both systems and
must be made idempotent. Temporal is the broader engine: arbitrary control flow, first-class durable
timers, signals and queries, per-activity retry policies, and a history UI. Chenile is the simpler one to
run and reason about: authoritative, inspectable state in a database you already operate, no determinism
constraint, and no separate cluster. Choose Chenile for fan-out/fan-in batch work (and chains of it) that
belongs in your own database; choose Temporal for arbitrary-shape workflows that need its richer primitives
and tooling and justify operating a cluster.

## The core distinction

In **Chenile**, the orchestration *is data*. The flow is a state-transition diagram, each `Process` is a
persisted row with a current state, and workers live outside the machine — they do work and raise events
(`splitDone`, `subProcessDoneSuccessfully`, `aggregationDone`) to advance state. There is no orchestration
program; there is a state machine, configuration, command handlers, and durable consequences written in
the application's own database.

In **Temporal**, the orchestration *is code*. You write a workflow function (Go/Java/TypeScript/Python)
that reads like ordinary sequential code — loops, conditionals, activity calls, durable sleep, waiting on
signals — and Temporal makes it crash-proof by event-sourcing every step and *replaying* the function to
rebuild state. The defining constraint is that workflow code must be deterministic so replay is faithful.
Chenile never replays code; it reloads a row, so it has no determinism constraint.

A useful anchor: Chenile sits near **AWS Step Functions / the transactional-outbox saga** camp (a state
machine you configure, durability from your own database), while Temporal is the **durable-code** camp (the
successor to Uber's Cadence).

## Side by side

| Dimension | Chenile Process Management | Temporal |
|---|---|---|
| Model | Declarative STM + durable command handlers | Imperative workflow-as-code, deterministic replay |
| Authoritative state | Rows in your own database (inspectable, repairable) | Event history in the Temporal cluster |
| Deployment | Library + your database; no extra cluster | Separate cluster, or Temporal Cloud |
| Durable execution | Transactional outbox + poller | Event-sourced replay |
| Exactly-once local effects | Consumer receipts + claim-token fencing, one transaction | Recorded in history |
| External effects | At-least-once; receivers deduplicate (`WorkerDto.dispatchId`) | At-least-once activities; make them idempotent |
| Crash recovery | Lease reclaim + stale-claimant fencing | Automatic via replay |
| Retries / backoff / dead-letter | Exponential backoff, max attempts, `DEAD`, compensation, `replayDead` | Per-activity retry policies |
| Durable timers / `sleep` / in-flow cron | `visible_after` + entry-level Quartz crontabs | Native |
| Signals / queries | Events via REST/STM + durable parent signalling | First-class signals and queries |
| Visibility | SQL over outbox / process / trigger tables | Web UI + searchable history |
| Scaling | `FOR UPDATE SKIP LOCKED` + database backlog (KEDA) | Task queues + workers |
| Scope | Splitter/aggregator + process chaining | General-purpose workflows |

## Durability and correctness

On the property that governs correctness — *will a transition's consequences survive a crash, exactly
once* — the two systems are in the same class.

Chenile commits the consequences of a transition (worker dispatch, child creation, parent notification,
completion, chaining) as outbox rows in the same transaction that saves the process, then a dispatcher
executes them. Temporal appends consequences to an event history and a worker picks them up. Neither loses
work to a crash between "decided" and "did."

For effects written through the shared datasource, Chenile's consumer receipts plus claim-token fencing
make a redelivered command a no-op — the same guarantee Temporal gets by recording activity results in
history. The replay-safe parent counter (a receipt on parent/child identity rather than a blind increment)
is the textbook form of this.

For effects Chenile cannot commit transactionally — a broker publish from the queue-based starter, or the
after-commit subscriber fan-out — the effect is at-least-once and receivers must deduplicate. This is
exactly Temporal's activity contract: activities run at least once; you make them idempotent. Same
semantics, same obligation.

When a command exhausts its retries it becomes `DEAD`; a dead `START_WORKER` is auto-compensated to the
error terminal so a worker that will never run does not strand a subtree. Temporal surfaces retry
exhaustion into the workflow for saga compensation. Different mechanism, equivalent intent.

## Where Temporal is more capable

These differences are about generality and tooling rather than durability.

Temporal workflows are arbitrary code — loops, conditionals, `Promise.allOf`, `await`, child workflows —
whereas Chenile expresses orchestration as a fixed STM shape (splitter/aggregator plus
predecessor/successor chaining). A free-form multi-step saga in Chenile must be modelled as new states and
events.

Temporal has first-class durable timers, `sleep`, and in-workflow cron. Chenile has `visible_after`
(powering backoff and delayed commands) and durable Quartz crontabs at the activation layer, but no
"sleep for days inside the flow" primitive.

Temporal can signal a running workflow and query its live in-memory state. Chenile delivers events through
REST/STM and signals parents durably, but has no "query the running instance" beyond reading its rows.

Temporal configures retry intervals, timeouts, and non-retryable error types per activity. Chenile applies
one policy across commands, plus the START_WORKER compensation.

Temporal ships a Web UI over full event history for live debugging. Chenile's history is the outbox,
process, and trigger tables — queryable by SQL, but you build your own views.

## Where Chenile is more suitable

Chenile keeps authoritative state as inspectable, repairable rows in your own database, with no
replay-determinism constraint on your code; recovery actions like `replayDead(id)` are plain operations,
not history surgery. It needs no separate cluster to run, secure, upgrade, or pay for — it is a library
over the database you already operate. And because state, outbox, and receipts share one datasource and
transaction manager, they commit together, with no cross-system dual-write at the core (only at the
deliberately documented external-effect edge).

## Choosing between them

Choose **Chenile** when the work is fan-out/fan-in batch processing (or chains of it) that should live in
your own database with no new infrastructure, and when inspectable, repairable state matters more than
arbitrary control flow. You do not trade away durability or exactly-once semantics to get that simplicity.

Choose **Temporal** when workflows have arbitrary shape, need durable timers / signals / queries as
first-class primitives, benefit from per-activity retry tuning and a managed debugging UI, span a polyglot
fleet, and justify operating a cluster (or paying for Temporal Cloud).

## One caveat

Chenile's guarantees hold for the orchestration cascade it models — worker dispatch, child creation, parent
signalling, completion, and chaining. They are not a general durable-execution engine for arbitrary
application code: an effect a worker performs that is neither transactional on the shared datasource nor
idempotent at its receiver is at-least-once, exactly as a non-idempotent Temporal activity would be. The
durability is scoped to the orchestration, not to everything a worker might do.
