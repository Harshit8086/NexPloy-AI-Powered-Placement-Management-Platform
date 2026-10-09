-- V68: Drop legacy role_skill_requirements catalog table and indexes.
-- The per-drive drive_skill_requirements table (V67) is now the authoritative source.

drop index if exists ux_role_skill_req_scope;
drop index if exists ix_role_skill_req_role;
drop table if exists role_skill_requirements;
