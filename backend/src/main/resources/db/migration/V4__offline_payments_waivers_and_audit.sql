-- Cash and cheque payments recorded by the treasurer, late fee waivers, and an append-only audit log.

alter table payments drop constraint payments_check;

alter table payments
	add column channel text not null default 'ONLINE' check (channel in ('ONLINE', 'OFFLINE')),
	add column recorded_by uuid references members (id),
	add column reference_note text,
	add column waived_late_fee_paise bigint not null default 0 check (waived_late_fee_paise >= 0),
	add column waiver_reason text;

alter table payments
	add constraint payments_confirmed_has_time check (status = 'PENDING' or confirmed_at is not null),
	add constraint payments_online_has_reference check (channel = 'OFFLINE' or status = 'PENDING' or gateway_reference is not null),
	add constraint payments_offline_has_recorder check (channel = 'ONLINE' or recorded_by is not null),
	add constraint payments_waiver_has_reason check (waived_late_fee_paise = 0 or waiver_reason is not null);

create table audit_log (
	id uuid primary key,
	society_id uuid not null references societies (id),
	member_id uuid references members (id),
	action text not null,
	detail text not null,
	created_at timestamptz not null default now()
);

alter table audit_log enable row level security;
create policy tenant_isolation on audit_log
	using (society_id = current_society_id())
	with check (society_id = current_society_id());

do $$
begin
	if exists (select 1 from pg_roles where rolname = 'ledger_app') then
		-- Append only: the application can add and read entries, never change or remove them.
		grant select, insert on audit_log to ledger_app;
	end if;
end
$$;
