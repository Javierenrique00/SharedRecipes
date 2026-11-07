-- Migration 8: undoable recipe deletes.
--
-- Deleting a recipe used to be a hard DELETE, so the app had nothing left to put
-- back and could not offer an Undo action. Deletes are now *soft*: delete_recipe()
-- stamps `deleted_at` instead of removing the row, and restore_recipe() clears it
-- again. That is what makes Undo exact — same id, same ingredients, same steps and
-- the same comments come back, with no data re-inserted from the client (comment
-- authorship rules make a client-side re-create impossible for a client's comment).
--
-- Consequences:
--   * reads must filter `deleted_at is null`; the app does that in RecipesApiImpl
--     and the policy below hides deleted rows from clients as a second line of
--     defence (admins keep seeing them, which is what lets them restore).
--   * the DELETE policy is kept, so an admin can still purge a row for real
--     (`delete from public.recipes where id = ...`), which cascades as before.
--     Soft-deleted rows of a purged recipe go with it.

alter table public.recipes add column deleted_at timestamptz;

comment on column public.recipes.deleted_at is
  'Set when an admin deletes the recipe; null = visible. Cleared by public.restore_recipe().';

-- The visible list is the only hot query; keep the index partial so soft-deleted
-- rows never widen it.
create index recipes_visible_idx on public.recipes (created_at desc)
  where deleted_at is null;

-- ---------------------------------------------------------------------------
-- Policies: hide soft-deleted recipes from clients
-- ---------------------------------------------------------------------------
drop policy "recipes select authenticated" on public.recipes;

create policy "recipes select visible or admin"
  on public.recipes for select to authenticated
  using (deleted_at is null or public.is_admin());

-- ---------------------------------------------------------------------------
-- RPCs: soft delete / restore
-- SECURITY INVOKER (the default), so the "recipes update admin" policy still
-- decides who may run them: a client calling either function updates no rows.
-- ---------------------------------------------------------------------------
create function public.delete_recipe(p_recipe_id uuid)
  returns void
  language plpgsql
  set search_path = ''
as $$
begin
  update public.recipes
  set deleted_at = now()
  where id = p_recipe_id
    and deleted_at is null;
end;
$$;

create function public.restore_recipe(p_recipe_id uuid)
  returns void
  language plpgsql
  set search_path = ''
as $$
begin
  update public.recipes
  set deleted_at = null
  where id = p_recipe_id
    and deleted_at is not null;
end;
$$;

-- Supabase grants EXECUTE on new public functions to anon/authenticated by default,
-- so revoke first, then grant to signed-in users only (mirrors migration 5).
revoke all on function public.delete_recipe(uuid) from public, anon, authenticated;
revoke all on function public.restore_recipe(uuid) from public, anon, authenticated;
grant execute on function public.delete_recipe(uuid) to authenticated;
grant execute on function public.restore_recipe(uuid) to authenticated;
