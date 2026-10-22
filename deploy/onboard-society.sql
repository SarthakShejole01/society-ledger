-- Loads one society, its flats and its people from a spreadsheet saved as CSV (see flats-template.csv).
--
--   psql "$OWNER_DATABASE_URL" -v society_code=sunrise -v society_name="Sunrise Apartments" \
--     -f onboard-society.sql < /path/to/flats.csv
--
-- Run it as the owner of the tables, not as ledger_app. Everything happens in one transaction, so a
-- mistake in the sheet loads nothing. A row with no mobile and email is just a flat without a login yet.
-- member_role is OWNER, TREASURER or COMMITTEE (blank means OWNER).
\set ON_ERROR_STOP on
\if :{?society_code}
\else
  \echo 'Missing settings. Usage: psql <owner connection> -v society_code=... -v society_name=... -f onboard-society.sql < flats.csv'
  \quit
\endif

begin;

create temp table incoming (
  number text, floor integer, monthly_amount_rupees numeric,
  member_name text, member_mobile text, member_email text, member_role text
) on commit drop;

-- The sheet arrives on standard input (psql cannot expand a variable inside \copy).
\copy incoming from pstdin with (format csv, header true)

-- Check the sheet before touching anything real.
do $$
declare bad text;
begin
  select string_agg('flat ' || number, ', ') into bad from incoming
   where nullif(trim(member_mobile), '') is not null
     and (regexp_replace(member_mobile, '\D', '', 'g') !~ '^\d{10}$'
          or coalesce(member_email, '') !~ '^[^@\s]+@[^@\s]+$'
          or nullif(trim(member_name), '') is null);
  if bad is not null then
    raise exception 'Each person needs a name, a 10-digit mobile number and an email address. Check: %', bad;
  end if;
  select string_agg('flat ' || number, ', ') into bad from incoming
   where nullif(trim(member_mobile), '') is not null
     and coalesce(nullif(upper(trim(member_role)), ''), 'OWNER') not in ('OWNER', 'TREASURER', 'COMMITTEE');
  if bad is not null then
    raise exception 'member_role must be OWNER, TREASURER or COMMITTEE. Check: %', bad;
  end if;
  select string_agg(number, ', ') into bad from (
    select trim(number) as number from incoming group by trim(number) having count(*) > 1) d;
  if bad is not null then
    raise exception 'These flat numbers appear more than once: %', bad;
  end if;
  select string_agg(m, ', ') into bad from (
    select regexp_replace(member_mobile, '\D', '', 'g') as m from incoming
    where nullif(trim(member_mobile), '') is not null
    group by 1 having count(*) > 1) d;
  if bad is not null then
    raise exception 'These mobile numbers appear more than once (one number can log in as one person): %', bad;
  end if;
end $$;

insert into societies (id, code, name, status)
values (gen_random_uuid(), :'society_code', :'society_name', 'LIVE')
returning id as society_id \gset

insert into flats (id, society_id, number, floor, monthly_amount_paise)
select gen_random_uuid(), :'society_id', trim(number), floor, round(monthly_amount_rupees * 100)
from incoming;

insert into members (id, society_id, flat_id, name, mobile, email, role)
select gen_random_uuid(), :'society_id', f.id, trim(i.member_name),
       regexp_replace(i.member_mobile, '\D', '', 'g'), lower(trim(i.member_email)),
       coalesce(nullif(upper(trim(i.member_role)), ''), 'OWNER')
from incoming i
join flats f on f.society_id = :'society_id' and f.number = trim(i.number)
where nullif(trim(i.member_mobile), '') is not null;

select (select count(*) from flats where society_id = :'society_id') as flats_loaded,
       (select count(*) from members where society_id = :'society_id') as people_loaded;

commit;
