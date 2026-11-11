-- Migration 11: admin maintenance — empty the trash and sweep orphaned photos.
--
-- The console already knows how to soft-delete (Undo), restore and clear *everything*.
-- What was missing is the mundane housekeeping between those: permanently remove the
-- recipes that are sitting in the trash, and delete the storage objects no recipe points
-- at any more (replaced photos, abandoned edits, a clear that did not finish).
--
-- Two small RPCs, both SECURITY INVOKER like the rest of the admin surface, so the
-- existing policies ("recipes delete admin", "recipe images delete admin") remain the
-- authority on who may run them.

-- ---------------------------------------------------------------------------
-- Permanently delete the recipes that are already soft-deleted.
--
-- Deliberately narrower than admin_clear_recipes(p_hard => true), which empties the
-- whole collection: this one only touches rows the admin already threw away. Deleting
-- the recipe cascades to its ingredients, steps and comments.
-- ---------------------------------------------------------------------------
create function public.admin_purge_deleted_recipes()
  returns int
  language plpgsql
  set search_path = ''
as $$
declare
  v_count int;
begin
  delete from public.recipes where deleted_at is not null;
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

comment on function public.admin_purge_deleted_recipes() is
  'Hard-deletes every soft-deleted recipe (cascading to children). Admin only; returns the count.';

-- ---------------------------------------------------------------------------
-- Every storage path any recipe still points at.
--
-- No deleted_at filter on purpose: a soft-deleted recipe can still be restored, so its
-- photos must not be considered orphans until the recipe itself is purged.
-- ---------------------------------------------------------------------------
create function public.admin_recipe_image_paths()
  returns text[]
  language sql
  stable
  set search_path = ''
as $$
  select coalesce(array_agg(path), '{}'::text[])
  from (
    select r.image_path as path
    from public.recipes r
    where r.image_path is not null

    union

    select s.image_path
    from public.recipe_steps s
    where s.image_path is not null
  ) paths;
$$;

comment on function public.admin_recipe_image_paths() is
  'Distinct image_path values referenced by recipes and steps (deleted recipes included). '
  'Used by the admin orphan sweep; it never deletes anything.';

revoke all on function public.admin_purge_deleted_recipes() from public, anon, authenticated;
revoke all on function public.admin_recipe_image_paths() from public, anon, authenticated;

grant execute on function public.admin_purge_deleted_recipes() to authenticated;
grant execute on function public.admin_recipe_image_paths() to authenticated;
