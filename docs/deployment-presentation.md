# Deploying Chenile Process Management — presentation / video outline

A speaker-ready slide outline that doubles as the narration script for the video. Each
slide has its on-screen bullets, the diagram to show, and a short narration cue (what to
say over it). The diagrams are the mermaid blocks in [deployment.md](deployment.md) and
[process-management-diagrams.md](process-management-diagrams.md) — render and drop them in.

Source of truth for every claim here: [deployment.md](deployment.md).

---

## Slide 1 — Title

**Deploying Chenile Process Management**
Two mini monoliths, one durable orchestration engine.

- Narration: "Chenile Process Management runs long jobs as a tree of processes. Today I
  want to show you how you actually *deploy* it — not as one big process, but as a small
  team of cooperating services that each scale on their own terms."

---

## Slide 2 — The job it does (one line of recap)

- A long job is a tree: non-leaf processes **split**, leaf processes **execute**, results
  **aggregate** back up.
- All of that is driven by a state machine with a durable, always-on outbox.
- Diagram: the `PROCESS_FLOW` state diagram (`docs/puml/process-states.mermaid`).
- Narration: "One sentence of recap: splitters fan work out, executors do the leaf work,
  aggregators fold it back. That's the engine. The question is where that work runs."

---

## Slide 3 — The temptation, and the problem

- The easy path: one process that orchestrates *and* does the work.
- But orchestration and work have opposite profiles:
  - the engine is **stateful and singular** — it owns the only writable process state;
  - the work is **stateless and plural** — CPU-bound, bursty, changes often.
- Coupling them means you can't scale, redeploy, or crash one without the other.
- Narration: "Put them in one box and every busy executor competes with your
  orchestration engine, and every change to domain logic redeploys the brain. We split
  them."

---

## Slide 4 — The shape of a deployment

- **At least two** mini monoliths, growing to more:
  - **Process Manager Server** — the brain.
  - **Process Manager Worker** — the muscle (start with one, add more).
- Decoupled by a **queue** (`chenile-azure`, Kafka) plus an HTTP callback.
- Diagram: the "At a glance" component flowchart from deployment.md.
- Narration: "Server on the left dispatches work onto a queue. Worker on the right picks
  it up, does it, and calls back. The only things between them are a topic and one URL."

---

## Slide 5 — The server monolith: the brain

- Packages: `process-api` + `process-service` (the STM), `chenile-outbox` (always-on),
  **one** worker starter — the **q-based** one — plus `trigger-service` + `crontab-trigger`,
  and the management API / admin UI.
- Owns the **only** PostgreSQL datasource; runs the outbox poller.
- Dispatches work; never performs it.
- Narration: "Everything that *starts* work lives here: cron and triggers to kick jobs
  off, the state machine to drive them, the outbox to make every consequence durable, and
  a starter to hand work out. It holds all the state and none of the labour."

---

## Slide 6 — The worker monolith: the muscle

- Packages: `process-delegate` (the `ProcessManagerClient` + the `SplitterBase` /
  `ExecutorBase` / `AggregatorBase` base classes), one or more **worker jars**, a
  **`ProcessWorkListener`**, and a `chenile-pubsub` subscriber.
- Holds **no** process state, needs **no** process database.
- The one required knob: **`process.manager.base-url`** — where the brain lives.
- Narration: "The worker holds only your domain code and the plumbing to reach the server.
  The listener pulls a WorkerDto off the queue; your splitter or executor or aggregator
  does the job; the delegate calls the result back. Start it with the server's URL and
  you're done."

---

## Slide 7 — Why the queue-based starter

- Three interchangeable starters: **in-VM** (work runs inside the server),
  **JDBC** (durable work queue in the shared database), **queue-based** (publish a
  `WorkerDto` to a topic).
- Only the **queue** starter shares nothing but a broker — exactly what a stateless remote
  worker wants.
- `QueueBasedProcessStarter` → `ChenilePub.asyncPublish(topic, json, {tenant})`; `topic`
  comes from the definition's `execDef.get("queue")`.
- Narration: "In-VM keeps work in the brain. JDBC makes workers share the brain's
  database. The queue starter is the one that truly cuts the cord — and lets you route
  different process types to different worker pools."

---

## Slide 8 — The loop (the heart of the talk)

- Cron → durable trigger acceptance (outbox ID acknowledgement) → later event dispatch → create root → outbox records
  `START_WORKER` in the same transaction → poller publishes `WorkerDto` → worker's
  `ProcessWorkListener` dispatches to the right `IWorker` → work runs → HTTP callback
  (`splitDone` / `doneSuccessfully` / `aggregationDone`) → STM advances → records the next
  consequences → … → terminal → `EMIT_COMPLETED` → successors chained.
- Diagram: the dispatch-and-callback `sequenceDiagram` from deployment.md.
- Narration: walk the arrows once, slowly. "Notice the work leaves the server as a
  message and comes back as an event. The server's state only ever changes on the
  callback — the worker is a pure function with a return address."

---

## Slide 9 — Why it's safe across the gap

- The queue hop is **at-least-once**: a `WorkerDto` can arrive twice.
- Callbacks are made safe on the server by **effect-level idempotency** —
  `checkIdempotency` against `chenile_process_receipt`, keyed by business identity — so a
  replay can't double-count a child or create duplicate successors.
- `create` returns when the root is committed, **not** when the cascade finishes — observe
  progress through the management API.
- Narration: "Distributing work means messages repeat. The engine already assumes that:
  the receipts table means every effect lands exactly once even when the message lands
  twice. Workers should still dedupe their own external side effects."

---

## Slide 10 — Scaling

- Three axes:
  1. **more instances** of one worker monolith (competing consumers; KEDA to/from zero on
     queue depth);
  2. **more worker monoliths** (route heavy process types to their own pool via their own
     topic);
  3. **more worker jars** inside a monolith (packaging choice).
- The server stays single in intent — replicate it for availability, not throughput.
- Diagram: the scaling flowchart from deployment.md.
- Narration: "You never scale the brain to go faster — you scale the muscle. Add
  replicas, add pools, or just add jars. The topic names in your definitions decide who
  gets what."

---

## Slide 11 — Generate the whole thing

- Both monoliths are mechanical: fixed module sets, only the worker jars and definitions
  vary.
- The **`chenile-process-deployment` JGen blueprint** scaffolds a runnable server and N
  runnable workers from one spec — module manifests, `ProcessWorkListener`, queue
  subscriptions and `process.manager.base-url` all wired.
- Reference: [process-deployment-blueprint.md](blueprints/process-deployment-blueprint.md).
- Narration: "You don't hand-assemble this. One spec in, a server and a set of workers
  out, already pointed at each other. You fill in the `doStart` methods; everything else
  is generated."

---

## Slide 12 — Recap / call to action

- Brain and muscle, split by a queue and a callback URL.
- Durable where it counts (server + outbox + receipts); disposable where it helps
  (stateless workers).
- Scale the workers, generate the scaffold, write only the domain logic.
- Narration: "Split the stateful brain from the stateless muscle, let a queue and a URL
  join them, and let the generator build the frame. That's a Chenile process deployment."

---

## Production notes (for the video, not slides)

- Show a real `three-level-file-processes.json` spec on screen when you reach slide 11.
- The loop slide (8) is the money shot — consider animating the arrows in order.
- If recording with Hyperframes + `/brag`, the component, sequence and scaling diagrams
  are the three visual beats; the rest is talking head over bullets.
