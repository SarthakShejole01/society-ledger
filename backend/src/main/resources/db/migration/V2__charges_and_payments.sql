-- One charge per flat per month, and the payments made against it.

create table charges (
	id uuid primary key,
	society_id uuid not null references societies (id),
	flat_id uuid not null references flats (id),
	month date not null check (extract(day from month) = 1),
	amount_paise bigint not null check (amount_paise >= 0),
	created_at timestamptz not null default now(),
	unique (flat_id, month)
);

create table payments (
	id uuid primary key,
	society_id uuid not null references societies (id),
	charge_id uuid not null references charges (id),
	payer_name text not null,
	status text not null check (status in ('PENDING', 'CONFIRMED')),
	amount_paise bigint not null check (amount_paise >= 0),
	late_fee_paise bigint not null check (late_fee_paise >= 0),
	method text,
	gateway_reference text unique,
	started_at timestamptz not null,
	confirmed_at timestamptz,
	check ((status = 'CONFIRMED') = (gateway_reference is not null and confirmed_at is not null))
);

-- A charge can be paid only once, whatever the gateway sends.
create unique index one_confirmed_payment_per_charge on payments (charge_id) where status = 'CONFIRMED';

alter table charges enable row level security;
create policy tenant_isolation on charges
	using (society_id = current_society_id())
	with check (society_id = current_society_id());

alter table payments enable row level security;
create policy tenant_isolation on payments
	using (society_id = current_society_id())
	with check (society_id = current_society_id());

do $$
begin
	if exists (select 1 from pg_roles where rolname = 'ledger_app') then
		grant select, insert on charges to ledger_app;
		grant select, insert, update on payments to ledger_app;
	end if;
end
$$;
