-- Shared Recipes — schema, roles and supporting objects.
-- Migration 1 of 3 for a fresh project.

create extension if not exists "pgcrypto" with schema extensions;

-- ---------------------------------------------------------------------------
-- Roles
-- ---------------------------------------------------------------------------
create type public.app_role as enum ('admin', 'client');

-- ---------------------------------------------------------------------------
-- Profiles: one row per auth user, holds the application role
-- ---------------------------------------------------------------------------
create table public.profiles (
  id           uuid primary key references auth.users (id) on delete cascade,
  email        text not null default '',
  display_name text,
  role         public.app_role not null default 'client',
  created_at   timestamptz not null default now()
);

comment on table public.profiles is 'Application users. The role column is the single source of truth for authorisation.';

-- Mirror new auth users into profiles. SECURITY DEFINER because the insert runs
-- in the auth schema's context, where the caller has no rights on public.profiles.
create function public.handle_new_user()
  returns trigger
  language plpgsql
  security definer
  set search_path = ''
as $$
begin
  insert into public.profiles (id, email, display_name)
  values (
    new.id,
    coalesce(new.email, ''),
    coalesce(
      nullif(new.raw_user_meta_data ->> 'display_name', ''),
      split_part(coalesce(new.email, ''), '@', 1)
    )
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- Recipes
-- ---------------------------------------------------------------------------
create table public.recipes (
  id           uuid primary key default gen_random_uuid(),
  title        text not null check (length(btrim(title)) between 1 and 200),
  description  text check (description is null or length(description) <= 4000),
  youtube_url  text check (
                 youtube_url is null
                 or youtube_url ~* '^https?://(www\.|m\.)?(youtube\.com|youtu\.be)/'
               ),
  -- Derived 11-char video id so clients never have to parse URLs.
  -- Immutable expression, therefore valid in a stored generated column.
  youtube_id   text generated always as (
                 case
                   when youtube_url ~* '(?:youtu\.be/|/v=|/embed/|/shorts/)([A-Za-z0-9_-]{11})'
                     then substring(youtube_url from '(?:youtu\.be/|/v=|/embed/|/shorts/)([A-Za-z0-9_-]{11})')
                 end
               ) stored,
  servings     int check (servings is null or servings > 0),
  prep_minutes int check (prep_minutes is null or prep_minutes >= 0),
  cook_minutes int check (cook_minutes is null or cook_minutes >= 0),
  created_by   uuid references auth.users (id) on delete set null,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create index recipes_created_at_idx on public.recipes (created_at desc);
create index recipes_title_idx on public.recipes (lower(title));

-- ---------------------------------------------------------------------------
-- Ingredient list ("components")
-- ---------------------------------------------------------------------------
create table public.recipe_ingredients (
  id        uuid primary key default gen_random_uuid(),
  recipe_id uuid not null references public.recipes (id) on delete cascade,
  position  int not null default 0,
  quantity  numeric(10, 2) check (quantity is null or quantity >= 0),
  unit      text check (unit is null or length(unit) <= 40),
  name      text not null check (length(btrim(name)) between 1 and 200)
);

create index recipe_ingredients_recipe_id_idx
  on public.recipe_ingredients (recipe_id, position);

-- ---------------------------------------------------------------------------
-- Procedure (ordered steps)
-- ---------------------------------------------------------------------------
create table public.recipe_steps (
  id          uuid primary key default gen_random_uuid(),
  recipe_id   uuid not null references public.recipes (id) on delete cascade,
  step_number int not null check (step_number > 0),
  instruction text not null check (length(btrim(instruction)) between 1 and 4000),
  unique (recipe_id, step_number)
);

create index recipe_steps_recipe_id_idx
  on public.recipe_steps (recipe_id, step_number);

-- ---------------------------------------------------------------------------
-- Comments (any authenticated user may write; scoped to their own rows)
-- ---------------------------------------------------------------------------
create table public.recipe_comments (
  id         uuid primary key default gen_random_uuid(),
  recipe_id  uuid not null references public.recipes (id) on delete cascade,
  author_id  uuid not null references public.profiles (id) on delete cascade,
  body       text not null check (length(btrim(body)) between 1 and 2000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index recipe_comments_recipe_id_idx
  on public.recipe_comments (recipe_id, created_at desc);

-- ---------------------------------------------------------------------------
-- updated_at maintenance
-- ---------------------------------------------------------------------------
create function public.set_updated_at()
  returns trigger
  language plpgsql
  set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger recipes_set_updated_at
  before update on public.recipes
  for each row execute function public.set_updated_at();

create trigger recipe_comments_set_updated_at
  before update on public.recipe_comments
  for each row execute function public.set_updated_at();
