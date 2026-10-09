create table refresh_tokens (
    id varchar(64) primary key,
    token_hash varchar(128) not null unique,
    username varchar(100) not null,
    issued_at timestamptz not null,
    expires_at timestamptz not null,
    revoked boolean not null default false,
    replaced_by varchar(64),
    constraint refresh_tokens_user_fk foreign key (username) references user_accounts(username)
);

create index idx_refresh_tokens_username on refresh_tokens(username);
