-- Migration 6: strip the default anon grants.
--
-- Supabase grants `all` on every new table in `public` (and EXECUTE on every new
-- function) to `anon`, so an unauthenticated caller kept raw INSERT/UPDATE/DELETE
-- privileges on the app tables. RLS already denies those rows, but the grants are
-- unnecessary surface: the app never touches these objects before signing in, and
-- README promises least privilege. Revoking them makes RLS the only gate left.

revoke all on public.profiles,
              public.recipes,
              public.recipe_ingredients,
              public.recipe_steps,
              public.recipe_comments
  from anon;

revoke execute on function public.create_recipe(text, text, text, int, int, int, jsonb, jsonb)
  from anon, public;
revoke execute on function public.update_recipe(uuid, text, text, text, int, int, int, jsonb, jsonb)
  from anon, public;

grant execute on function public.create_recipe(text, text, text, int, int, int, jsonb, jsonb)
  to authenticated;
grant execute on function public.update_recipe(uuid, text, text, text, int, int, int, jsonb, jsonb)
  to authenticated;
