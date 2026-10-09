-- Rejection audit for optimization runs (OR_TOOLS §6, SECURITY §4).
-- An officer/admin can reject a pending proposal with a recorded reason;
-- the active schedule is left unchanged.

alter table schedule_optimization_runs
    add column rejected_at timestamptz,
    add column rejected_reason varchar(500);
