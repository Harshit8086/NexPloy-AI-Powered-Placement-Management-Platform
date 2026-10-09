-- Recruiter self-service profile fields + a per-recruiter settings row.
-- All recruiter columns are nullable so existing seeded rows stay valid;
-- recruiter_settings is created lazily per recruiter by the service, with
-- production-sensible defaults baked in here for any row inserted directly.

alter table recruiters
    add column logo_initial varchar(4),
    add column company_description text,
    add column company_size varchar(40),
    add column company_linkedin varchar(320),
    add column company_twitter varchar(320),
    add column company_github varchar(320);

create table recruiter_settings (
    recruiter_id varchar(64) primary key,

    -- Notifications
    notify_new_application boolean not null default true,
    notify_shortlisted     boolean not null default true,
    notify_interview       boolean not null default true,
    notify_offer           boolean not null default true,
    notify_drive           boolean not null default true,
    email_notifications    boolean not null default true,
    in_app_notifications   boolean not null default true,

    -- Preferences
    theme       varchar(16) not null default 'dark',
    language    varchar(16) not null default 'en',
    timezone    varchar(64) not null default 'Asia/Kolkata',
    date_format varchar(24) not null default 'DD MMM YYYY',

    -- Privacy
    profile_visibility   varchar(24) not null default 'CAMPUS',
    contact_visibility   varchar(24) not null default 'CAMPUS',
    allow_data_analytics boolean not null default true,
    allow_marketing_email boolean not null default false,

    constraint recruiter_settings_recruiter_fk
        foreign key (recruiter_id) references recruiters(id)
);
