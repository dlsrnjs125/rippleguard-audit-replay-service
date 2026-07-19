create table audit_event (
    event_id uuid primary key,
    event_type varchar(128) not null,
    schema_version varchar(32) not null,
    occurred_at timestamp with time zone not null,
    producer varchar(128) not null,
    application_id uuid not null,
    case_id varchar(128) not null,
    evaluation_run_id uuid,
    correlation_id varchar(128) not null,
    causation_id uuid,
    sanitized_payload text not null,
    payload_hash varchar(64) not null,
    unknown_version boolean not null,
    ingested_at timestamp with time zone not null
);

create index ix_audit_event_case_occurred on audit_event(case_id, occurred_at, ingested_at);
create index ix_audit_event_correlation on audit_event(correlation_id);
create index ix_audit_event_causation on audit_event(causation_id);

create table inbox_event (
    event_id uuid primary key,
    event_type varchar(128) not null,
    application_id uuid,
    payload_hash varchar(64) not null,
    duplicate_count integer not null,
    first_seen_at timestamp with time zone not null,
    last_seen_at timestamp with time zone not null,
    constraint ck_inbox_event_duplicate_count check (duplicate_count >= 0)
);
