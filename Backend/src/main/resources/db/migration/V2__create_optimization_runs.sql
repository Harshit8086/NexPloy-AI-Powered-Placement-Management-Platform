create table schedule_optimization_runs (
    id varchar(64) primary key,
    schedule_version varchar(128) not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    approved_at timestamptz,
    result_json text not null
);
