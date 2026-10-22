-- The provider's order for an online payment, so its notifications can be matched to ours.

alter table payments add column gateway_order_id text unique;

-- A provider notification arrives with no society, only the order id. This is the one way the
-- application may find a payment without a society scope, and it returns nothing but the ids.
create function payment_for_gateway_order(p_order_id text)
	returns table (society_id uuid, payment_id uuid)
	language sql stable security definer
	set search_path = public
	as $$ select society_id, id from payments where gateway_order_id = p_order_id $$;

revoke all on function payment_for_gateway_order(text) from public;

do $$
begin
	if exists (select 1 from pg_roles where rolname = 'ledger_app') then
		grant execute on function payment_for_gateway_order(text) to ledger_app;
	end if;
end
$$;
