-- Migration 9: recipe administration — backup, restore and clear.
--
-- The admin screen needs three things a plain PostgREST query cannot give it:
--   * a *portable* backup of every recipe (soft-deleted rows included) as one JSON
--     document, so a restore is a single file and a single request;
--   * an idempotent restore that keeps the original ids and clears deleted_at, so
--     re-importing a backup brings the same rows back (comments and all) instead of
--     duplicating them — that is what makes "clear, then restore what I want" work;
--   * a clear that is recoverable by default, because clearing is usually the step
--     *before* restoring a subset and losing everything to a mis-tap would hurt.
--
-- admin_export_recipes / admin_restore_recipes / admin_clear_recipes are
-- SECURITY INVOKER like migrations 3 and 8: RLS still decides that only an admin may
-- read deleted rows or write at all, so a client calling them directly changes nothing.
-- admin_import_recipes is the one exception, explained on the function.

-- ---------------------------------------------------------------------------
-- Export: one jsonb array shaped exactly like the tables.
--
-- Field names match the columns (snake_case) on purpose: the file the app writes is
-- then a transparent dump of the tables, hand-editable, and the importer below reads
-- it back without any translation layer.
-- ---------------------------------------------------------------------------
create function public.admin_export_recipes()
  returns jsonb
  language sql
  stable
  set search_path = ''
as $$
  select coalesce(jsonb_agg(r.recipe order by r.created_at desc), '[]'::jsonb)
  from (
    select
      jsonb_build_object(
        'id',           r.id,
        'title',        r.title,
        'description',  r.description,
        'youtube_url',  r.youtube_url,
        'servings',     r.servings,
        'prep_minutes', r.prep_minutes,
        'cook_minutes', r.cook_minutes,
        'created_at',   r.created_at,
        'updated_at',   r.updated_at,
        'deleted_at',   r.deleted_at,
        'created_by',   r.created_by,
        'ingredients', (
          select coalesce(
            jsonb_agg(
              jsonb_build_object(
                'position', i.position,
                'quantity', i.quantity,
                'unit',     i.unit,
                'name',     i.name
              ) order by i.position
            ),
            '[]'::jsonb
          )
          from public.recipe_ingredients i
          where i.recipe_id = r.id
        ),
        'steps', (
          select coalesce(
            jsonb_agg(
              jsonb_build_object(
                'step_number', s.step_number,
                'instruction', s.instruction
              ) order by s.step_number
            ),
            '[]'::jsonb
          )
          from public.recipe_steps s
          where s.recipe_id = r.id
        ),
        'comments', (
          select coalesce(
            jsonb_agg(
              jsonb_build_object(
                'id',         c.id,
                'author_id',  c.author_id,
                'body',       c.body,
                'created_at', c.created_at
              ) order by c.created_at
            ),
            '[]'::jsonb
          )
          from public.recipe_comments c
          where c.recipe_id = r.id
        )
      ) as recipe,
      r.created_at
    from public.recipes r
  ) r;
$$;

comment on function public.admin_export_recipes() is
  'Every recipe (soft-deleted included) as one jsonb array. The app wraps it in a '
  'versioned backup file; see RecipeBackupCodec in the Kotlin client.';

-- ---------------------------------------------------------------------------
-- Import: upsert keyed on the recipe id, so a backup is a *restore*, not a copy.
--
-- SECURITY DEFINER, deliberately: restoring a backup has to (a) clear deleted_at,
-- (b) put back created_at/created_by and (c) re-attach comments other users wrote,
-- none of which RLS would let an invoker do (the comment insert policy requires
-- author_id = auth.uid()). The first statement below is the admin check that stands
-- in for the policies, and search_path is pinned empty like every other helper.
--
-- Robustness rules, all of them reported back in the returned counts:
--   * a row without a usable title is skipped (the table's check constraint would
--     otherwise abort the whole import);
--   * a row that fails for any other reason is rolled back to its own savepoint and
--     counted as skipped, so one bad record never loses the rest of the file;
--   * comments are only re-inserted when their author profile still exists (the FK
--     would fail otherwise), and never overwrite a comment written after the backup.
-- ---------------------------------------------------------------------------
create function public.admin_import_recipes(p_recipes jsonb)
  returns jsonb
  language plpgsql
  security definer
  set search_path = ''
as $$
declare
  v_recipe      jsonb;
  v_id          uuid;
  v_created_by  uuid;
  v_existed     boolean;
  v_was_deleted boolean;
  v_inserted    int := 0;
  v_updated     int := 0;
  v_restored    int := 0;
  v_skipped     int := 0;
begin
  if not public.is_admin() then
    raise exception 'admin_import_recipes is reserved for admins'
      using errcode = '42501';
  end if;

  if jsonb_typeof(coalesce(p_recipes, '[]'::jsonb)) <> 'array' then
    raise exception 'admin_import_recipes expects a jsonb array';
  end if;

  if jsonb_array_length(p_recipes) > 5000 then
    raise exception 'admin_import_recipes accepts at most 5000 recipes per call';
  end if;

  for v_recipe in select * from jsonb_array_elements(p_recipes)
  loop
    begin
      if coalesce(btrim(v_recipe ->> 'title'), '') = '' then
        v_skipped := v_skipped + 1;
        continue;
      end if;

      v_id := case
        when coalesce(v_recipe ->> 'id', '')
             ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
          then (v_recipe ->> 'id')::uuid
      end;

      -- created_by is a FK to auth.users: keep it only when that user still exists
      -- (a backup restored into a different project would otherwise fail).
      v_created_by := case
        when coalesce(v_recipe ->> 'created_by', '')
             ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
          then (
            select u.id from auth.users u where u.id = (v_recipe ->> 'created_by')::uuid
          )
      end;

      v_was_deleted := false;
      v_existed := false;

      if v_id is not null then
        select r.deleted_at is not null
          into v_was_deleted
          from public.recipes r
         where r.id = v_id;
        v_existed := found;
      end if;

      if v_existed then
        update public.recipes set
          title        = btrim(v_recipe ->> 'title'),
          description  = v_recipe ->> 'description',
          youtube_url  = v_recipe ->> 'youtube_url',
          servings     = (v_recipe ->> 'servings')::int,
          prep_minutes = (v_recipe ->> 'prep_minutes')::int,
          cook_minutes = (v_recipe ->> 'cook_minutes')::int,
          -- A restore means "make this recipe look like the backup", created_at included;
          -- updated_at is left to the trigger.
          created_at   = coalesce((v_recipe ->> 'created_at')::timestamptz, created_at),
          deleted_at   = null
        where id = v_id;
      else
        v_id := coalesce(v_id, gen_random_uuid());

        insert into public.recipes (
          id, title, description, youtube_url, servings, prep_minutes, cook_minutes,
          created_by, created_at
        ) values (
          v_id,
          btrim(v_recipe ->> 'title'),
          v_recipe ->> 'description',
          v_recipe ->> 'youtube_url',
          (v_recipe ->> 'servings')::int,
          (v_recipe ->> 'prep_minutes')::int,
          (v_recipe ->> 'cook_minutes')::int,
          coalesce(v_created_by, (select auth.uid())),
          coalesce((v_recipe ->> 'created_at')::timestamptz, now())
        );
      end if;

      -- Children are replaced wholesale, exactly like update_recipe() does, so a
      -- restored recipe matches the one that was backed up.
      delete from public.recipe_ingredients where recipe_id = v_id;

      insert into public.recipe_ingredients (recipe_id, position, quantity, unit, name)
      select
        v_id,
        coalesce((e ->> 'position')::int, (ord - 1)::int),
        (e ->> 'quantity')::numeric,
        e ->> 'unit',
        e ->> 'name'
      from jsonb_array_elements(coalesce(v_recipe -> 'ingredients', '[]'::jsonb))
        with ordinality as t(e, ord)
      where coalesce(btrim(e ->> 'name'), '') <> '';

      delete from public.recipe_steps where recipe_id = v_id;

      insert into public.recipe_steps (recipe_id, step_number, instruction)
      select
        v_id,
        coalesce((e ->> 'step_number')::int, ord::int),
        e ->> 'instruction'
      from jsonb_array_elements(coalesce(v_recipe -> 'steps', '[]'::jsonb))
        with ordinality as t(e, ord)
      where coalesce(btrim(e ->> 'instruction'), '') <> '';

      -- Comments are added, never deleted: words written after the backup survive.
      insert into public.recipe_comments (id, recipe_id, author_id, body, created_at)
      select
        (e ->> 'id')::uuid,
        v_id,
        p.id,
        e ->> 'body',
        coalesce((e ->> 'created_at')::timestamptz, now())
      from jsonb_array_elements(coalesce(v_recipe -> 'comments', '[]'::jsonb)) as t(e)
      join public.profiles p on p.id::text = (e ->> 'author_id')
      where coalesce(e ->> 'id', '')
            ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        and coalesce(btrim(e ->> 'body'), '') <> ''
      on conflict (id) do nothing;

      if v_existed then
        if v_was_deleted then
          v_restored := v_restored + 1;
        else
          v_updated := v_updated + 1;
        end if;
      else
        v_inserted := v_inserted + 1;
      end if;
    exception
      when others then
        -- Malformed row (bad number, over-long text, ...). plpgsql keeps a savepoint
        -- around the begin block, so this row rolls back and the import goes on.
        v_skipped := v_skipped + 1;
    end;
  end loop;

  return jsonb_build_object(
    'inserted', v_inserted,
    'updated',  v_updated,
    'restored', v_restored,
    'skipped',  v_skipped
  );
end;
$$;

comment on function public.admin_import_recipes(jsonb) is
  'Upserts a backup (the array shape returned by admin_export_recipes), keeping ids '
  'and clearing deleted_at. Admin only; returns {inserted, updated, restored, skipped}.';

-- ---------------------------------------------------------------------------
-- Selective restore of soft-deleted recipes ("restore the ones I want").
-- SECURITY INVOKER: the "recipes update admin" policy decides who may restore.
-- ---------------------------------------------------------------------------
create function public.admin_restore_recipes(p_ids uuid[])
  returns int
  language plpgsql
  set search_path = ''
as $$
declare
  v_count int;
begin
  update public.recipes
     set deleted_at = null
   where id = any (coalesce(p_ids, '{}'::uuid[]))
     and deleted_at is not null;

  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

-- ---------------------------------------------------------------------------
-- Clear. Soft by default (recoverable, same semantics as the Undo snackbar);
-- p_hard = true purges the rows for real, cascading to children and comments.
-- SECURITY INVOKER: the "update admin" / "delete admin" policies still apply.
-- ---------------------------------------------------------------------------
create function public.admin_clear_recipes(p_hard boolean default false)
  returns int
  language plpgsql
  set search_path = ''
as $$
declare
  v_count int;
begin
  if coalesce(p_hard, false) then
    delete from public.recipes;
    get diagnostics v_count = row_count;
  else
    update public.recipes set deleted_at = now() where deleted_at is null;
    get diagnostics v_count = row_count;
  end if;

  return v_count;
end;
$$;

-- Supabase grants EXECUTE on new public functions to anon/authenticated by default,
-- so revoke first and grant to signed-in users only (mirrors migrations 5, 6 and 8).
revoke all on function public.admin_export_recipes() from public, anon, authenticated;
revoke all on function public.admin_import_recipes(jsonb) from public, anon, authenticated;
revoke all on function public.admin_restore_recipes(uuid[]) from public, anon, authenticated;
revoke all on function public.admin_clear_recipes(boolean) from public, anon, authenticated;

grant execute on function public.admin_export_recipes() to authenticated;
grant execute on function public.admin_import_recipes(jsonb) to authenticated;
grant execute on function public.admin_restore_recipes(uuid[]) to authenticated;
grant execute on function public.admin_clear_recipes(boolean) to authenticated;

