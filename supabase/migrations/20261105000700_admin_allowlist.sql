-- Migration 7: admin allowlist.
--
-- Until now the only way to get the `admin` role (needed to create, edit or delete
-- recipes — see the RLS policies in migration 2) was a manual UPDATE on public.profiles.
-- This makes the promotion part of the sign-up flow and reproducible after a reset:
-- any email listed in public.admin_emails is created as an admin.
--
-- The table is managed by postgres/service_role only. RLS is enabled with no policies
-- and the API roles' grants are revoked, so the app can neither read nor enumerate it.

create table public.admin_emails (
  email      text primary key,
  note       text,
  created_at timestamptz not null default now(),
  constraint admin_emails_is_lowercase check (email = lower(btrim(email)))
);

comment on table public.admin_emails is
  'Emails that receive the admin role on sign-up. Not exposed to clients.';

alter table public.admin_emails enable row level security;
revoke all on public.admin_emails from anon, authenticated;

-- Same body as migration 1, plus the role decision from the allowlist.
create or replace function public.handle_new_user()
  returns trigger
  language plpgsql
  security definer
  set search_path = ''
as $$
begin
  insert into public.profiles (id, email, display_name, role)
  values (
    new.id,
    coalesce(new.email, ''),
    coalesce(
      nullif(new.raw_user_meta_data ->> 'display_name', ''),
      split_part(coalesce(new.email, ''), '@', 1)
    ),
    case
      when exists (
        select 1 from public.admin_emails a
        where a.email = lower(coalesce(new.email, ''))
      ) then 'admin'::public.app_role
      else 'client'::public.app_role
    end
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

-- CREATE OR REPLACE preserves the ACL, but state it explicitly so the intent survives
-- future edits: nothing needs EXECUTE on a trigger function over the API.
revoke all on function public.handle_new_user() from public, anon, authenticated;

-- Project owner.
insert into public.admin_emails (email, note)
values ('javierenrique@yahoo.com', 'project owner')
on conflict (email) do nothing;

-- Retroactive sync: allowlisted accounts that already exist are promoted here, which
-- is also what fixes the account created before this allowlist existed.
update public.profiles p
set role = 'admin'
where p.role <> 'admin'
  and exists (select 1 from public.admin_emails a where a.email = lower(p.email));
