create table if not exists chenile_process_work_item (
	id varchar(128) primary key,
	process_id varchar(128) not null,
	process_type varchar(200) not null,
	worker_type varchar(32) not null,
	idempotency_key varchar(512) not null,
	payload text not null,
	status varchar(32) not null,
	attempt integer not null default 0,
	locked_by varchar(200),
	locked_until timestamp,
	created_at timestamp not null,
	updated_at timestamp not null,
	finished_at timestamp,
	error_message varchar(4000),
	constraint uk_chenile_process_work_idempotency unique (idempotency_key)
);

create index if not exists idx_chenile_process_work_backlog
	on chenile_process_work_item (status, locked_until);

create index if not exists idx_chenile_process_work_process
	on chenile_process_work_item (process_id, worker_type, status);
