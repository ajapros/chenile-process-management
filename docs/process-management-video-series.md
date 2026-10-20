# Process Management — the full-picture video (shot-by-shot script)

One flagship "deep dive" episode for the Chenile channel (episode 13), rather than a series of
shorts: **target ~6 minutes**, chaptered so viewers can jump. It tells the whole story through
the **p1** sample — declare a process, generate the deployment, implement three methods, watch
the durable loop run, and operate it. It is the on-screen companion to the
[process deployment tutorial](https://chenile.org/developer/process-deployment-tutorial/).

> **Format note.** The house series runs 90–130 s; this is deliberately longer and chaptered —
> a reference piece, not a short. If you ever want shorts too, each chapter below is already a
> clean cut point (the earlier 5-episode breakdown maps onto chapters 2–8).

## How to use this script

- Each **scene** is one `SEG` line for `build.mjs`: `{ id, scene, text, say? }`. **VISUAL** drives
  the GSAP composition; **VO** is the narration verbatim. Keep each VO line a single caption card.
- **Reusable assets** (no new design work):
  - The animated schematic `cheniledocs.github.io/assets/schematics/p1-process-deployment.html` —
    screen-record its 12 steps; they are the spine of chapters 6–7.
  - Mermaid in `docs/process-management-diagrams.md` (sequence, ER) and
    `docs/puml/process-states.mermaid` (STM).
  - p1 code (`chenile-samples/p1`): `defs.json`, `BatchSplitter`, `ItemExecutor`, `BatchAggregator`.
  - Brand: bg `#0d0d0d`, accent `#f89038`; Sora / Josefin Sans / JetBrains Mono.
- **Pronunciation overrides** (`say`, set once): `p1`→"p one", `API`→"A P I", `DTO`→"D T O",
  `STM`→"S T M", `idempotent`→"eye DEM po tent", `idempotency`→"eye dem PO ten see",
  `Kafka`→"KAHF kuh", `JGen`→"J gen", `Postgres`→"POST gres", `202`→"two oh two", `KEDA`→"KAY duh".
- **Timings:** house layout — sting 3.8 s, line gap 0.35 s, scene gap 0.9 s, outro 6.5 s. At ~50
  lines this lands around 5.5–6.5 min. Put a scene break at each chapter for the chapter markers.

---

## Chapter 1 — Cold open: the problem  · ~0:00

| id | scene | VISUAL | VO |
|---|---|---|---|
| sting | sting | Chenile mark + title "Process Management" | — |
| c1-1 | s1 | One service box straining under a huge job | Some work doesn't fit a single request and response. |
| c1-2 | s1 | The job fans into dozens of sub-jobs | A long job that fans out into many — and is only done when all of them finish. |
| c1-3 | s1 | A crash icon; the whole thing vanishes | Do it the naive way, and a crash halfway through strands everything. |
| c1-4 | s1 | Text: "declare it. generate it. survive crashes." | Chenile does this as data, generates the plumbing, and survives the crash. |

## Chapter 2 — The shape: brain and muscle  · ~0:25

| id | scene | VISUAL | VO |
|---|---|---|---|
| c2-1 | s2 | Split screen: calm "brain", busy "muscle" | The idea is to split the work into two kinds of thing. |
| c2-2 | s2 | Brain fills in: state machine, outbox, triggers | A brain that owns the state — what happens next, and never loses it. |
| c2-3 | s2 | Muscle fills in: workers, no database | And a muscle that does the actual work. Stateless, and you run as many as you like. |
| c2-4 | s2 | Two host boxes label in: p1Server, p1Worker | In p1 that's two small deployables. One server, one worker. |
| c2-5 | s2 | A single Kafka topic drawn between them | Between them, one Kafka topic, and a callback. That's the whole coupling. |
| c2-6 | s2 | Text: "orchestration as data" | And the flow itself is declared once, as data. |

## Chapter 3 — Declare the processes  · ~1:05

| id | scene | VISUAL | VO |
|---|---|---|---|
| c3-1 | s3 | `defs.json`: `batch` and `item` | Here's p1 as data: a batch, and an item. |
| c3-2 | s3 | Highlight `leaf` flags | Leaf or not decides the worker. A leaf executes; a non-leaf splits and aggregates. |
| c3-3 | s3 | Highlight `inputFields` / `outputFields` | Typed fields become typed input and output objects. |
| c3-4 | s3 | Highlight `config.queue: p1-work` | Each definition names the topic its work rides on. |
| c3-5 | s3 | `cron-triggers.json`, `enabled:false` | Schedules live in their own file — seeded off until you turn them on. |

## Chapter 4 — Generate the deployment  · ~1:45

| id | scene | VISUAL | VO |
|---|---|---|---|
| c4-1 | s4 | Terminal: `jgen.sh -g chenile-process-management` → `-f` | One JGen blueprint turns those two files into a running deployment. |
| c4-2 | s4 | The generated `p1` reactor tree unfolds | It composes smaller blueprints — a server, a worker, one JAR per definition. |
| c4-3 | s4 | Module list: server-service highlighted | The server holds the state and ships no business logic. |
| c4-4 | s4 | `p1-worker-listener` highlighted | The worker is a headless Chenile listener on Kafka — itself generated. |
| c4-5 | s4 | `p1-batch-workers`, `p1-item-workers` highlighted | And one worker module per process type, waiting for your logic. |
| c4-6 | s4 | Text: "you write the domain logic. that's all." | Everything but the domain logic is generated. |

## Chapter 5 — Three methods  · ~2:35

| id | scene | VISUAL | VO |
|---|---|---|---|
| c5-1 | s5 | `BatchSplitter extends SplitterBase` | The splitter turns one batch into many children. |
| c5-2 | s5 | the loop with `makeSubProcessPayload(new ItemIn(i),"item")` | For each item, make a typed payload. The base class reports back for you. |
| c5-3 | s5 | `ItemExecutor extends ExecutorBase` | The executor is the leaf — typed input in, typed output out. |
| c5-4 | s5 | `doStart` returning a populated `ItemOutput` | Do the real work here. Throw, and it's recorded as a failure. |
| c5-5 | s5 | `BatchAggregator extends AggregatorBase` | The aggregator folds the children back together. |
| c5-6 | s5 | `AggregatedResult` with outputs/errors/counts | Chenile ships the result type, so you don't invent one. |
| c5-7 | s5 | Three methods side by side, nothing else | Three methods. No dispatch, no retries, no plumbing. |

## Chapter 6 — The durable loop  · ~3:30  *(screen-record the p1 schematic here)*

| id | scene | VISUAL | VO |
|---|---|---|---|
| c6-1 | s6 | Schematic step 1: trigger → 202 | Watch one batch run. A trigger commits durably and returns 202 — before any work runs. |
| c6-2 | s6 | Schematic step 2: batch created, STM | It replays as a create. The batch enters the state machine. |
| c6-3 | s6 | Schematic step 3: outbox row written | Its next step is written to the outbox, in the same transaction as the state. |
| c6-4 | s6 | Schematic steps 4–5: publish to Kafka | A poller publishes the work to Kafka, carrying the tenant along. |
| c6-5 | s6 | Schematic steps 6–7: deliver, dispatch to splitter | The worker consumes it and routes to your splitter. |
| c6-6 | s6 | Schematic step 8: splitDone callback via registry | The splitter calls back — finding the server through the registry. |
| c6-7 | s6 | Schematic step 9: three items execute | Three items are created, each executed over the very same loop. |
| c6-8 | s6 | Schematic steps 10–12: aggregate → PROCESSED | Then the batch aggregates, reaches processed, and emits completion. |

## Chapter 7 — Why it's safe: exactly-once, crash-proof  · ~4:45

| id | scene | VISUAL | VO |
|---|---|---|---|
| c7-1 | s7 | "at-least-once" arrow doubling a message | Kafka can deliver a message twice. |
| c7-2 | s7 | Three guard badges: enqueue key, fencing, receipt | Three guards turn that into exactly-once effects. |
| c7-3 | s7 | Receipt check on a child count | A replayed child is counted once; a replayed completion makes no duplicate. |
| c7-4 | s7 | Crash icon hits the worker; work resumes | Crash the worker. Nothing is lost — the work just resumes. |
| c7-5 | s7 | Text: "state and consequences — together, or not at all" | State and its consequences commit together, or not at all. |

## Chapter 8 — Run it and watch it  · ~5:15

| id | scene | VISUAL | VO |
|---|---|---|---|
| c8-1 | s8 | Terminals: `./run-server.sh`, `./run-workers.sh` | Start the server, then the worker. The worker joins a consumer group. |
| c8-2 | s8 | Text: "scale = more workers, same group" | Need throughput? Run more workers. The broker shares the load. |
| c8-3 | s8 | `curl POST /trigger` | Kick off a batch with one call. |
| c8-4 | s8 | process-ui: the tree filling, items turning green | Watch the tree fill — a batch, its items, turning green. |
| c8-5 | s8 | `/trace` graph + H2 console outbox draining | Every causal edge is queryable, and you can watch the outbox drain. |

## Chapter 9 — Recap  · ~5:40

| id | scene | VISUAL | VO |
|---|---|---|---|
| c9-1 | s9 | Final frame: brain + muscle + one topic | A brain, a muscle, one topic. Declared as data, durable by default. |
| c9-2 | s9 | Text: "tutorial + p1 sample linked below" | The full tutorial and the p1 sample are linked below. |
| c9-3 | s9 | Text: "go build one" | Go build one. |
| outro | outro | Channel bug + subscribe | — |

---

## Production notes

- **Film the schematic first.** Chapter 6 is the spine — screen-record
  `p1-process-deployment.html` end to end, then cut chapters 2 and 7 from the same frames.
  Chapters 3–5 and 8 are screen-recordings of real p1 files and terminals.
- **Scratch then re-voice.** Cut with Kokoro `af_heart`, lock the edit, then re-record in Raja's
  voice line by line (`record-voice.mjs ep13 --line c6-1`). Render without `--retts`.
- **YouTube chapters** (≥10 s apart, from `timings.json`): `0:00 The problem` · `0:25 Brain & muscle`
  · `1:05 Declare` · `1:45 Generate` · `2:35 Three methods` · `3:30 The durable loop`
  · `4:45 Exactly-once & crashes` · `5:15 Run it` · `5:40 Recap`.
- **Thumbnail:** the schematic's two-zone frame (p1Server | Kafka | p1Worker) with the title
  "Process Management". **Share one-liner:** "Long jobs, split in two — declared as data, durable
  by default. The whole of Chenile Process Management in six minutes."
- **Site hook-up:** render `ep13.{mp4,jpg,vtt}` into `assets/video/`, add `_episodes/13-process-management.md`
  (front matter `video`, `poster`, `captions`, `video_length`, `youtube_id`), feature it on
  `video-series.html`, and embed/link it from the
  [process deployment tutorial](https://chenile.org/developer/process-deployment-tutorial/).
- **Build reminder:** Hyperframes renders only on the Mac (the sandbox has no Chromium / npm 403).
  This is the script for `build.mjs`, not a rendered cut.
