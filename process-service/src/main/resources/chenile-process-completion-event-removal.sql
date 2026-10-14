-- Run after all application instances use the version without completion snapshots.
-- This removes historical event payloads; process state and durable outbox delivery are unchanged.
DROP TABLE IF EXISTS chenile_process_completion_event;
