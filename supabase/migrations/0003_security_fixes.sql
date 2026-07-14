create or replace function public.set_updated_at() returns trigger
language plpgsql
set search_path = public
as $$
begin
  new."updatedAt" = now();
  return new;
end
$$;

do $$
begin
  if exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'titles' and policyname = 'titles_admin_write'
  ) then
    drop policy "titles_admin_write" on public.titles;
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'titles' and policyname = 'titles_admin_insert'
  ) then
    create policy "titles_admin_insert"
    on public.titles for insert
    to authenticated
    with check (exists (select 1 from public.admins a where a.user_id = auth.uid()));
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'titles' and policyname = 'titles_admin_update'
  ) then
    create policy "titles_admin_update"
    on public.titles for update
    to authenticated
    using (exists (select 1 from public.admins a where a.user_id = auth.uid()))
    with check (exists (select 1 from public.admins a where a.user_id = auth.uid()));
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'titles' and policyname = 'titles_admin_delete'
  ) then
    create policy "titles_admin_delete"
    on public.titles for delete
    to authenticated
    using (exists (select 1 from public.admins a where a.user_id = auth.uid()));
  end if;
end $$;

do $$
begin
  if exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'servers' and policyname = 'servers_admin_write'
  ) then
    drop policy "servers_admin_write" on public.servers;
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'servers' and policyname = 'servers_admin_insert'
  ) then
    create policy "servers_admin_insert"
    on public.servers for insert
    to authenticated
    with check (exists (select 1 from public.admins a where a.user_id = auth.uid()));
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'servers' and policyname = 'servers_admin_update'
  ) then
    create policy "servers_admin_update"
    on public.servers for update
    to authenticated
    using (exists (select 1 from public.admins a where a.user_id = auth.uid()))
    with check (exists (select 1 from public.admins a where a.user_id = auth.uid()));
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'servers' and policyname = 'servers_admin_delete'
  ) then
    create policy "servers_admin_delete"
    on public.servers for delete
    to authenticated
    using (exists (select 1 from public.admins a where a.user_id = auth.uid()));
  end if;
end $$;

