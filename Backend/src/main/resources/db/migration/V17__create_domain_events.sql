-- Domain-event audit trail (DESIGN.md section 7). Consumers of published domain events record
-- them here for audit/analytics; the event id is the primary key so recording is idempotent
-- (a replayed or redelivered event is written at most once).

create table domain_events (
    id varchar(64) primary key,
    event_type varchar(64) not null,
    campus_id varchar(64),
    aggregate_id varchar(64),
    summary varchar(1000),
    occurred_at timestamptz not null,
    recorded_at timestamptz not null
);

create index idx_domain_events_aggregate on domain_events(aggregate_id);
create index idx_domain_events_campus on domain_events(campus_id);
create index idx_domain_events_type on domain_events(event_type);
