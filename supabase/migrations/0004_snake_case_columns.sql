do $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'titles' and column_name = 'posterUrl'
  ) then
    execute 'alter table public.titles rename column "posterUrl" to poster_url';
  end if;
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'titles' and column_name = 'posterurl'
  ) then
    execute 'alter table public.titles rename column posterurl to poster_url';
  end if;

  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'titles' and column_name = 'sourcePageUrl'
  ) then
    execute 'alter table public.titles rename column "sourcePageUrl" to source_page_url';
  end if;
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'titles' and column_name = 'sourcepageurl'
  ) then
    execute 'alter table public.titles rename column sourcepageurl to source_page_url';
  end if;

  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'titles' and column_name = 'playableUrl'
  ) then
    execute 'alter table public.titles rename column "playableUrl" to playable_url';
  end if;
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'titles' and column_name = 'playableurl'
  ) then
    execute 'alter table public.titles rename column playableurl to playable_url';
  end if;

  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'titles' and column_name = 'createdAt'
  ) then
    execute 'alter table public.titles rename column "createdAt" to created_at';
  end if;

  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'titles' and column_name = 'updatedAt'
  ) then
    execute 'alter table public.titles rename column "updatedAt" to updated_at';
  end if;

  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'servers' and column_name = 'titleId'
  ) then
    execute 'alter table public.servers rename column "titleId" to title_id';
  end if;
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'servers' and column_name = 'titleid'
  ) then
    execute 'alter table public.servers rename column titleid to title_id';
  end if;

  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'servers' and column_name = 'pageUrl'
  ) then
    execute 'alter table public.servers rename column "pageUrl" to page_url';
  end if;
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'servers' and column_name = 'pageurl'
  ) then
    execute 'alter table public.servers rename column pageurl to page_url';
  end if;

  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'servers' and column_name = 'playableUrl'
  ) then
    execute 'alter table public.servers rename column "playableUrl" to playable_url';
  end if;
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'servers' and column_name = 'playableurl'
  ) then
    execute 'alter table public.servers rename column playableurl to playable_url';
  end if;
end $$;

create or replace function public.set_updated_at() returns trigger
language plpgsql
set search_path = public
as $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'titles' and column_name = 'updated_at'
  ) then
    new.updated_at = now();
  end if;
  return new;
end
$$;

drop trigger if exists trg_titles_updated on public.titles;
create trigger trg_titles_updated
before update on public.titles
for each row execute function public.set_updated_at();
