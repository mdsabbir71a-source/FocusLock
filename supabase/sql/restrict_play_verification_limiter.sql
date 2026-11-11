drop function public.consume_play_verify_limit();
create function public.consume_play_verify_limit(p_user_id uuid)
returns boolean language plpgsql security definer set search_path = ''
as $function$
declare v_count integer; v_now timestamptz := clock_timestamp();
begin
 if p_user_id is null then return false; end if;
 insert into private.play_verify_rate_limits(user_id, window_start, request_count)
 values(p_user_id, v_now, 1)
 on conflict(user_id) do update
 set window_start = case when private.play_verify_rate_limits.window_start <= v_now - interval '1 minute' then v_now else private.play_verify_rate_limits.window_start end,
 request_count = case when private.play_verify_rate_limits.window_start <= v_now - interval '1 minute' then 1 else least(private.play_verify_rate_limits.request_count + 1, 11) end
 returning request_count into v_count;
 return v_count <= 10;
end
$function$;
revoke all on function public.consume_play_verify_limit(uuid) from public, anon, authenticated;
grant execute on function public.consume_play_verify_limit(uuid) to service_role;