alter table public.admins enable row level security;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'admins' and policyname = 'admins_select_self'
  ) then
    create policy "admins_select_self"
    on public.admins for select
    to authenticated
    using (user_id = auth.uid());
  end if;
end $$;

