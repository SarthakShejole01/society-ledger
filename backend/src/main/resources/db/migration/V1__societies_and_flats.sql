-- Every tenant table carries society_id and is protected by row-level security.
-- The application connects as a role that does not own these tables, so the policies always apply.

create table societies (
	id uuid primary key,
	code text not null unique,
	name text not null,
	status text not null default 'ONBOARDING'
		check (status in ('ONBOARDING', 'TRIAL', 'LIVE', 'SUSPENDED')),
	created_at timestamptz not null default now()
);

create table flats (
	id uuid primary key,
	society_id uuid not null references societies (id),
	number text not null,
	floor integer not null check (floor >= 0),
	monthly_amount_paise bigint not null check (monthly_amount_paise >= 0),
	created_at timestamptz not null default now(),
	unique (society_id, number)
);

-- The society of the current transaction, set by the application. Empty means "no society".
create function current_society_id() returns uuid
	language sql stable
	as $$ select nullif(current_setting('app.society_id', true), '')::uuid $$;

alter table societies enable row level security;
create policy society_isolation on societies
	using (id = current_society_id());

alter table flats enable row level security;
create policy tenant_isolation on flats
	using (society_id = current_society_id())
	with check (society_id = current_society_id());

do $$
begin
	if exists (select 1 from pg_roles where rolname = 'ledger_app') then
		grant select on societies to ledger_app;
		grant select, insert, update, delete on flats to ledger_app;
	end if;
end
$$;
