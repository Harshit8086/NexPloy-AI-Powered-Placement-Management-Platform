-- V20: enrich offers for the full frontend Offer shape (offer & documentation /
-- joining tracking): offer type, work location, joining date, acceptance deadline,
-- documents-verified / joined flags, PPO source, and a denormalized company name for
-- display. Additive and nullable (flags default false) so existing offers are unaffected.

alter table offers
    add column company_name varchar(200),
    add column offer_type varchar(32),
    add column location varchar(200),
    add column joining_date date,
    add column acceptance_deadline date,
    add column documents_verified boolean not null default false,
    add column joined boolean not null default false,
    add column from_internship varchar(200);

-- Backfill existing offers: company name from the recruiter, a sensible default type.
update offers o set
    company_name = coalesce(o.company_name, (select r.company_name from recruiters r where r.id = o.recruiter_id)),
    offer_type = coalesce(o.offer_type, 'Full-Time')
where o.company_name is null or o.offer_type is null;
