create table pending_causation_event (
    event_id uuid primary key,
    event_type varchar(128) not null,
    schema_version varchar(32) not null,
    producer varchar(128) not null,
    application_id uuid not null,
    case_id varchar(128) not null,
    evaluation_run_id uuid,
    correlation_id varchar(128) not null,
    causation_id uuid not null,
    raw_payload text not null,
    raw_payload_hash varchar(64) not null,
    first_seen_at timestamp with time zone not null,
    next_attempt_at timestamp with time zone not null,
    attempt_count integer not null,
    expires_at timestamp with time zone not null,
    status varchar(32) not null,
    constraint ck_pending_causation_attempt_count check (attempt_count >= 0),
    constraint ck_pending_causation_status check (status in ('PENDING_CAUSATION', 'RESOLVED', 'EXPIRED'))
);

create index ix_pending_causation_causation_status on pending_causation_event(causation_id, status);
create index ix_pending_causation_next_attempt on pending_causation_event(status, next_attempt_at);
create index ix_pending_causation_expires on pending_causation_event(status, expires_at);
