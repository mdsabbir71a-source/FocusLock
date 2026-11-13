CREATE OR REPLACE FUNCTION private.sync_auth_user()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  provider_name text;
begin
  -- Billing-only anonymous sessions must not create ordinary app profiles.
  if new.is_anonymous then return new; end if;
  provider_name := coalesce(new.raw_app_meta_data ->> 'provider', 'email');

  insert into public.profiles (
    user_id, email, phone, display_name, avatar_url, auth_provider, created_at, updated_at, last_seen_at
  )
  values (
    new.id,
    new.email,
    new.phone,
    coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name'),
    new.raw_user_meta_data ->> 'avatar_url',
    provider_name,
    coalesce(new.created_at, now()),
    now(),
    now()
  )
  on conflict (user_id) do update
  set email = excluded.email,
      phone = excluded.phone,
      display_name = coalesce(public.profiles.display_name, excluded.display_name),
      avatar_url = coalesce(public.profiles.avatar_url, excluded.avatar_url),
      auth_provider = excluded.auth_provider,
      updated_at = now();

  insert into public.entitlements (user_id, access_level, status, source)
  values (new.id, 'free', 'active', 'free_beta')
  on conflict (user_id) do nothing;

  return new;
end;
$function$
