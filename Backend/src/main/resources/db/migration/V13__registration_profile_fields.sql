-- Extra profile fields captured at self-service registration.
-- Students gain a phone; recruiters gain phone plus company details.
-- All nullable so existing seeded rows remain valid.

alter table students
    add column phone varchar(32);

alter table recruiters
    add column phone varchar(32),
    add column company_website varchar(320),
    add column company_industry varchar(120),
    add column company_location varchar(200),
    add column designation varchar(120);
