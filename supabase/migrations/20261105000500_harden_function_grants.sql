-- Migration 5: keep SECURITY DEFINER helpers off the public API surface.
--
-- Postgres does not check EXECUTE when a trigger fires, so handle_new_user() only
-- needs to be owner-callable; leaving it reachable at /rest/v1/rpc/handle_new_user
-- is an unnecessary exposure. is_admin() must stay executable by signed-in users
-- because the RLS policies call it, but `anon` has no business calling it — and
-- revoking from PUBLIC alone is not enough, because Supabase's default privileges
-- grant EXECUTE on new public functions to anon/authenticated explicitly.

revoke all on function public.handle_new_user() from public, anon, authenticated;

revoke execute on function public.is_admin() from public, anon;
grant execute on function public.is_admin() to authenticated;
