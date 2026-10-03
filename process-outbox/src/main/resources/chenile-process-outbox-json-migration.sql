-- PostgreSQL upgrade from the column-based command schema. Stop producers and dispatchers first.
-- Back up the table before applying with your migration tool. Entire upgrade commits or rolls back.
begin;
lock table chenile_process_outbox in access exclusive mode;

do $migration$
begin
    if exists (select 1 from information_schema.columns
               where table_schema=current_schema() and table_name='chenile_process_outbox' and column_name='target_id') then
        -- Other commands already store their complete WorkerDto/Process/ProcessCompletedEvent as JSON.
        -- Preserve parent receipt identity and idempotency keys when wrapping the legacy signal body.
        update chenile_process_outbox
        set payload=jsonb_build_object('parentId', target_id, 'childId', process_id,
                  'eventName', event_name, 'payload', payload::jsonb)::text
        where command_type='SIGNAL_PARENT';
    end if;
end;
$migration$;

alter table chenile_process_outbox drop column if exists target_id;
alter table chenile_process_outbox drop column if exists event_name;
alter table chenile_process_outbox drop column if exists worker_type;
alter table chenile_process_outbox alter column command_type type varchar(128);
alter table chenile_process_outbox alter column payload set not null;
commit;
