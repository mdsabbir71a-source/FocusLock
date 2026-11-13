-- Anonymous billing sessions must not inherit ordinary app data access.
-- Existing authenticated policies continue to govern non-anonymous users.
do $block$
declare t text;
begin
 foreach t in array array['admin_audit_log','analytics_daily','app_diagnostics','app_remote_config','devices','entitlements','owner_bootstrap_requests','profiles','user_consents']
 loop
   execute format('create policy deny_billing_anonymous on public.%I as restrictive for all to authenticated using ((auth.jwt()->>''is_anonymous'') is distinct from ''true'') with check ((auth.jwt()->>''is_anonymous'') is distinct from ''true'')', t);
 end loop;
end
$block$;