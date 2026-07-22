alter table inbox_event
    add column conflict_count integer not null default 0;

alter table inbox_event
    add constraint ck_inbox_event_conflict_count check (conflict_count >= 0);

create table audit_event_quarantine (
    quarantine_id uuid primary key,
    source_event_id uuid,
    event_type varchar(128),
    schema_version varchar(32),
    producer varchar(128),
    received_at timestamp with time zone not null,
    reason_code varchar(128) not null,
    safe_payload_hash varchar(64) not null,
    correlation_id varchar(128),
    causation_id uuid,
    retry_eligible boolean not null
);

create index ix_audit_event_quarantine_source_event on audit_event_quarantine(source_event_id);
create index ix_audit_event_quarantine_reason on audit_event_quarantine(reason_code);

create table agent_run_audit (
    agent_run_id uuid primary key,
    decision_case_id varchar(128) not null,
    evaluation_run_id uuid not null,
    validation_outcome varchar(32) not null,
    validation_reason_codes text not null,
    agent_result_reference varchar(512) not null,
    agent_result_digest varchar(71) not null,
    validated_schema_version varchar(32) not null,
    validated_at timestamp with time zone not null,
    latest_attempt_id integer not null,
    attempt_count integer not null,
    first_occurred_at timestamp with time zone not null,
    last_occurred_at timestamp with time zone not null,
    source_event_id uuid not null unique,
    source_schema_version varchar(32) not null,
    ingestion_status varchar(32) not null,
    snapshot_id varchar(128),
    snapshot_version varchar(128),
    snapshot_schema_version varchar(32),
    snapshot_digest varchar(71),
    feature_schema_version varchar(128),
    preprocessing_version varchar(128),
    model_version varchar(128),
    model_artifact_digest varchar(71),
    threshold_version varchar(128),
    constraint ck_agent_run_audit_validation_outcome check (validation_outcome in ('VALIDATED', 'REJECTED')),
    constraint ck_agent_run_audit_ingestion_status check (ingestion_status in ('ACCEPTED')),
    constraint ck_agent_run_audit_attempt_count check (attempt_count >= 1)
);

create index ix_agent_run_audit_decision_case on agent_run_audit(decision_case_id, last_occurred_at);
create index ix_agent_run_audit_evaluation_run on agent_run_audit(evaluation_run_id, last_occurred_at);

create table agent_attempt_audit (
    agent_run_id uuid not null,
    attempt_id integer not null,
    attempt_status varchar(32) not null,
    failure_classification varchar(64),
    failure_reason_code varchar(128),
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    result_digest varchar(71) not null,
    source_event_id uuid not null unique,
    primary key (agent_run_id, attempt_id, source_event_id),
    constraint ck_agent_attempt_audit_attempt_id check (attempt_id >= 1),
    constraint fk_agent_attempt_audit_run foreign key (agent_run_id) references agent_run_audit(agent_run_id)
);

create index ix_agent_attempt_audit_run on agent_attempt_audit(agent_run_id, attempt_id);
