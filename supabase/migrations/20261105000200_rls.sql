-- Shared Recipes — Row Level Security.
-- Every table in an exposed schema MUST have RLS enabled; a client holding the
-- publishable key is otherwise able to read/write everything.

-- ---------------------------------------------------------------------------
-- Admin check helper.
-- SECURITY DEFINER so policies on public.profiles can call it without recursing
-- into their own policy. Empty search_path prevents hijacking.
-- ---------------------------------------------------------------------------
create function public.is_admin()
  returns boolean
  language sql
  stable
  security definer
  set search_path = ''
as $$
  select exists (
    select 1
    from public.profiles p
    where p.id = (select auth.uid())
      and p.role = 'admin'
  );
$$;

revoke all on function public.is_admin() from public;
grant execute on function public.is_admin() to authenticated;

-- ---------------------------------------------------------------------------
-- Enable RLS
-- ---------------------------------------------------------------------------
alter table public.profiles           enable row level security;
alter table public.recipes            enable row level security;
alter table public.recipe_ingredients enable row level security;
alter table public.recipe_steps       enable row level security;
alter table public.recipe_comments    enable row level security;

-- ---------------------------------------------------------------------------
-- Table grants (least privilege, on top of RLS)
-- ---------------------------------------------------------------------------
grant select, insert, update, delete on public.recipes            to authenticated;
grant select, insert, update, delete on public.recipe_ingredients to authenticated;
grant select, insert, update, delete on public.recipe_steps       to authenticated;
grant select, insert, update, delete on public.recipe_comments    to authenticated;

-- profiles: read + update only. No INSERT/DELETE from clients (the signup
-- trigger owns creation; cascade from auth.users owns deletion).
grant select on public.profiles to authenticated;

-- The role column is NOT updatable by clients, even if a policy allowed the
-- statement. Column-level grants are the backstop against self-promotion.
revoke update on public.profiles from authenticated;
grant update (display_name) on public.profiles to authenticated;

-- ---------------------------------------------------------------------------
-- Policies: profiles
-- ---------------------------------------------------------------------------
create policy "profiles select own or admin"
  on public.profiles for select to authenticated
  using (id = (select auth.uid()) or public.is_admin());

create policy "profiles update own"
  on public.profiles for update to authenticated
  using (id = (select auth.uid()))
  with check (id = (select auth.uid()));

-- ---------------------------------------------------------------------------
-- Policies: recipes — everyone signed in reads, only admins write
-- ---------------------------------------------------------------------------
create policy "recipes select authenticated"
  on public.recipes for select to authenticated
  using (true);

create policy "recipes insert admin"
  on public.recipes for insert to authenticated
  with check (public.is_admin());

create policy "recipes update admin"
  on public.recipes for update to authenticated
  using (public.is_admin())
  with check (public.is_admin());

create policy "recipes delete admin"
  on public.recipes for delete to authenticated
  using (public.is_admin());

-- ---------------------------------------------------------------------------
-- Policies: ingredients and steps (mirror their parent recipe)
-- ---------------------------------------------------------------------------
create policy "recipe_ingredients select authenticated"
  on public.recipe_ingredients for select to authenticated
  using (true);

create policy "recipe_ingredients write admin"
  on public.recipe_ingredients for all to authenticated
  using (public.is_admin())
  with check (public.is_admin());

create policy "recipe_steps select authenticated"
  on public.recipe_steps for select to authenticated
  using (true);

create policy "recipe_steps write admin"
  on public.recipe_steps for all to authenticated
  using (public.is_admin())
  with check (public.is_admin());

-- ---------------------------------------------------------------------------
-- Policies: comments — clients may comment, but only on their own rows
-- ---------------------------------------------------------------------------
create policy "recipe_comments select authenticated"
  on public.recipe_comments for select to authenticated
  using (true);

create policy "recipe_comments insert own"
  on public.recipe_comments for insert to authenticated
  with check (author_id = (select auth.uid()));

create policy "recipe_comments update own"
  on public.recipe_comments for update to authenticated
  using (author_id = (select auth.uid()))
  with check (author_id = (select auth.uid()));

create policy "recipe_comments delete own or admin"
  on public.recipe_comments for delete to authenticated
  using (author_id = (select auth.uid()) or public.is_admin());
