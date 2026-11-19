create table if not exists private.play_verify_rate_limits (
 user_id uuid primary key references auth.users(id) on delete cascade,
 window_start timestamptz not null,
 request_count integer not null check (request_count > 0)
);
revoke all on private.play_verify_rate_limits from public, anon, authenticated;
alter table private.play_verify_rate_limits enable row level security;
create or replace function public.consume_play_verify_limit()
returns boolean
language plpgsql security definer set search_path = ''
as $function$
declare v_user uuid := auth.uid(); v_count integer; v_now timestamptz := clock_timestamp();
begin
 if v_user is null then return false; end if;
 insert into private.play_verify_rate_limits(user_id, window_start, request_count)
 values(v_user, v_now, 1)
 on conflict(user_id) do update
 set window_start = case when private.play_verify_rate_limits.window_start <= v_now - interval '1 minute' then v_now else private.play_verify_rate_limits.window_start end,
 request_count = case when private.play_verify_rate_limits.window_start <= v_now - interval '1 minute' then 1 else least(private.play_verify_rate_limits.request_count + 1, 11) end
 returning request_count into v_count;
 return v_count <= 10;
end
$function$;
revoke all on function public.consume_play_verify_limit() from public, anon;
grant execute on function public.consume_play_verify_limit() to authenticated;