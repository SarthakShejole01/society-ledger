-- People who can log in, one-time codes and sessions. All tenant tables, all protected by row-level security.

create table members (
	id uuid primary key,
	society_id uuid not null references societies (id),
	flat_id uuid references flats (id),
	name text not null,
	mobile text not null,
	email text not null,
	role text not null check (role in ('OWNER', 'TREASURER', 'COMMITTEE')),
	created_at timestamptz not null default now(),
	unique (society_id, mobile)
);

create table otp_challenges (
	id uuid primary key,
	society_id uuid not null references societies (id),
	member_id uuid not null references members (id),
	code_hash text not null,
	created_at timestamptz not null,
	expires_at timestamptz not null,
	attempts integer not null default 0,
	used boolean not null default false
);
create index otp_challenges_member on otp_challenges (member_id, created_at);

create table sessions (
	token_hash text primary key,
	society_id uuid not null references societies (id),
	member_id uuid not null references members (id),
	created_at timestamptz not null,
	expires_at timestamptz not null
);

alter table members enable row level security;
create policy tenant_isolation on members
	using (society_id = current_society_id())
	with check (society_id = current_society_id());

alter table otp_challenges enable row level security;
create policy tenant_isolation on otp_challenges
	using (society_id = current_society_id())
	with check (society_id = current_society_id());

alter table sessions enable row level security;
create policy tenant_isolation on sessions
	using (society_id = current_society_id())
	with check (society_id = current_society_id());

-- Login starts from a society code, before any society is known. This is the only way the
-- application may look a society up without a scope, and it returns nothing but the id.
create function society_id_for_code(p_code text) returns uuid
	language sql stable security definer
	set search_path = public
	as $$ select id from societies where code = p_code $$;

revoke all on function society_id_for_code(text) from public;

do $$
begin
	if exists (select 1 from pg_roles where rolname = 'ledger_app') then
		grant select on members to ledger_app;
		grant select, insert, update on otp_challenges to ledger_app;
		grant select, insert, delete on sessions to ledger_app;
		grant execute on function society_id_for_code(text) to ledger_app;
	end if;
end
$$;
