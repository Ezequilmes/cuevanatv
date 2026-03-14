create extension if not exists "pgcrypto";

create table if not exists public.titles (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  "posterUrl" text,
  description text,
  type text check (type in ('movie','series')) default 'movie',
  published boolean default false,
  "sourcePageUrl" text,
  "playableUrl" text,
  "createdAt" timestamptz default now(),
  "updatedAt" timestamptz default now()
);

create table if not exists public.servers (
  id uuid primary key default gen_random_uuid(),
  "titleId" uuid not null,
  name text not null,
  "pageUrl" text,
  "playableUrl" text,
  priority int default 0
);

alter table public.servers
  add constraint servers_title_fk
  foreign key ("titleId") references public.titles(id)
  on delete cascade;

create table if not exists public.admins (
  user_id uuid primary key
);

create or replace function public.set_updated_at() returns trigger as $$
begin
  new."updatedAt" = now();
  return new;
end
$$ language plpgsql;

drop trigger if exists trg_titles_updated on public.titles;
create trigger trg_titles_updated
before update on public.titles
for each row execute function public.set_updated_at();

alter table public.titles enable row level security;
alter table public.servers enable row level security;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'titles' and policyname = 'titles_select_auth'
  ) then
    create policy "titles_select_auth"
    on public.titles for select
    to authenticated
    using (true);
  end if;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'servers' and policyname = 'servers_select_auth'
  ) then
    create policy "servers_select_auth"
    on public.servers for select
    to authenticated
    using (true);
  end if;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'titles' and policyname = 'titles_admin_write'
  ) then
    create policy "titles_admin_write"
    on public.titles for all
    to authenticated
    using (exists (select 1 from public.admins a where a.user_id = auth.uid()))
    with check (exists (select 1 from public.admins a where a.user_id = auth.uid()));
  end if;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'servers' and policyname = 'servers_admin_write'
  ) then
    create policy "servers_admin_write"
    on public.servers for all
    to authenticated
    using (exists (select 1 from public.admins a where a.user_id = auth.uid()))
    with check (exists (select 1 from public.admins a where a.user_id = auth.uid()));
  end if;
end $$;
