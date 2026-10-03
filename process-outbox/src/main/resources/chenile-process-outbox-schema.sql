-- Transactional outbox for durable execution of ProcessEntryAction consequences.
-- Rows are inserted in the SAME transaction as the Process state save, so every consequence of a
-- transition (worker dispatch, sub-process creation, parent signal, completion emission) is durably
-- recorded before commit and can never be lost to a crash between the save and the side effect.
create table if not exists chenile_process_outbox (
	id varchar(128) primary key,
	-- the process whose transition produced this command
	process_id varchar(128) not null,
	-- Registry discriminator; new types require no enum or extra columns.
	command_type varchar(128) not null,
	-- Command-owned JSON (including any target, event, worker type, and business payload).
	payload text not null,
	idempotency_key varchar(512) not null,
	status varchar(32) not null,
	attempt integer not null default 0,
	-- durable timer + retry backoff: invisible to claimNext until now >= visible_after
	visible_after timestamp not null,
	locked_by varchar(200),
	claim_token varchar(128),
	tenant_id varchar(255),
	locked_until timestamp,
	created_at timestamp not null,
	updated_at timestamp not null,
	finished_at timestamp,
	error_message varchar(4000),
	constraint uk_chenile_process_outbox_idempotency unique (idempotency_key)
);

create index if not exists idx_chenile_process_outbox_backlog
	on chenile_process_outbox (status, visible_after, locked_until);

create index if not exists idx_chenile_process_outbox_process
	on chenile_process_outbox (process_id);

-- Consumer receipts are process-effect idempotency, deliberately separate from TriggerLog.
create table if not exists chenile_process_receipt (
    consumer varchar(64) not null,
    receipt_key varchar(512) not null,
    received_at timestamp not null,
    primary key (consumer, receipt_key)
);
