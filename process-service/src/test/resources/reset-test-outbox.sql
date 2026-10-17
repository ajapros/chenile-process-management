-- Reset only the test database transport when constructing a new application context.
drop table if exists chenile_process_outbox;
drop table if exists chenile_process_receipt;
