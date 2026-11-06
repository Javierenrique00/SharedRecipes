-- Shared Recipes — atomic write RPCs.
--
-- Why: PostgREST cannot wrap multiple table writes in one transaction from a
-- client. Writing a recipe + its ingredients + steps as three separate requests
-- would leave a half-created recipe on any failure. These functions run as
-- SECURITY INVOKER (the default), so RLS still applies to the caller and a
-- non-admin calling them is rejected by the policies on public.recipes.

create function public.create_recipe(
  p_title        text,
  p_description  text   default null,
  p_youtube_url  text   default null,
  p_servings     int    default null,
  p_prep_minutes int    default null,
  p_cook_minutes int    default null,
  p_ingredients  jsonb  default '[]'::jsonb,
  p_steps        jsonb  default '[]'::jsonb
) returns uuid
  language plpgsql
  set search_path = ''
as $$
declare
  v_recipe_id uuid;
begin
  insert into public.recipes (
    title, description, youtube_url, servings, prep_minutes, cook_minutes, created_by
  ) values (
    p_title, p_description, p_youtube_url, p_servings, p_prep_minutes, p_cook_minutes,
    (select auth.uid())
  )
  returning id into v_recipe_id;

  insert into public.recipe_ingredients (recipe_id, position, quantity, unit, name)
  select
    v_recipe_id,
    coalesce((e ->> 'position')::int, (ord - 1)::int),
    (e ->> 'quantity')::numeric,
    e ->> 'unit',
    e ->> 'name'
  from jsonb_array_elements(coalesce(p_ingredients, '[]'::jsonb)) with ordinality as t(e, ord);

  insert into public.recipe_steps (recipe_id, step_number, instruction)
  select
    v_recipe_id,
    coalesce((e ->> 'step_number')::int, ord::int),
    e ->> 'instruction'
  from jsonb_array_elements(coalesce(p_steps, '[]'::jsonb)) with ordinality as t(e, ord);

  return v_recipe_id;
end;
$$;

create function public.update_recipe(
  p_recipe_id    uuid,
  p_title        text,
  p_description  text   default null,
  p_youtube_url  text   default null,
  p_servings     int    default null,
  p_prep_minutes int    default null,
  p_cook_minutes int    default null,
  p_ingredients  jsonb  default null,  -- null = leave unchanged
  p_steps        jsonb  default null   -- null = leave unchanged
) returns void
  language plpgsql
  set search_path = ''
as $$
begin
  update public.recipes set
    title         = p_title,
    description   = p_description,
    youtube_url   = p_youtube_url,
    servings      = p_servings,
    prep_minutes  = p_prep_minutes,
    cook_minutes  = p_cook_minutes
  where id = p_recipe_id;

  if p_ingredients is not null then
    delete from public.recipe_ingredients where recipe_id = p_recipe_id;

    insert into public.recipe_ingredients (recipe_id, position, quantity, unit, name)
    select
      p_recipe_id,
      coalesce((e ->> 'position')::int, (ord - 1)::int),
      (e ->> 'quantity')::numeric,
      e ->> 'unit',
      e ->> 'name'
    from jsonb_array_elements(p_ingredients) with ordinality as t(e, ord);
  end if;

  if p_steps is not null then
    delete from public.recipe_steps where recipe_id = p_recipe_id;

    insert into public.recipe_steps (recipe_id, step_number, instruction)
    select
      p_recipe_id,
      coalesce((e ->> 'step_number')::int, ord::int),
      e ->> 'instruction'
    from jsonb_array_elements(p_steps) with ordinality as t(e, ord);
  end if;
end;
$$;

grant execute on function public.create_recipe(text, text, text, int, int, int, jsonb, jsonb) to authenticated;
grant execute on function public.update_recipe(uuid, text, text, text, int, int, int, jsonb, jsonb) to authenticated;
