-- Migration 12: let admins list the photo bucket.
--
-- A public bucket only bypasses RLS for the *direct download* endpoint
-- (`/object/public/...`). Listing objects (`POST /object/list/{bucket}`) is a query on
-- storage.objects and is therefore filtered by RLS like any other table.
--
-- Migration 10 deliberately granted no SELECT policy, because the app never needed to
-- list: Coil downloads thumbnails by URL. The admin maintenance sweep does need it — it
-- compares the bucket with the paths the database references — so it is granted here to
-- admins only. Clients still cannot enumerate the bucket.
drop policy if exists "recipe images read admin" on storage.objects;
create policy "recipe images read admin"
  on storage.objects for select to authenticated
  using (bucket_id = 'recipe-images' and public.is_admin());
