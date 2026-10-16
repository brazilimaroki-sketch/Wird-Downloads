-- إعداد مراجعة مساهمات «صدقة جارية» باستعمال جدول visitor_azkar الموجود
-- 1) أنشئ مستخدماً للمشرف من Supabase > Authentication > Users.
-- 2) بريد المشرف مضبوط مسبقاً على chatgptma86@gmail.com.
-- 3) شغّل الملف كاملاً من Supabase > SQL Editor.

create extension if not exists pgcrypto;

-- نحتفظ بالجدول والبيانات القديمة، ونضيف فقط حقول المراجعة الناقصة.
alter table public.visitor_azkar
  add column if not exists contributor_name text,
  add column if not exists repetition integer not null default 1,
  add column if not exists reviewed_at timestamptz,
  add column if not exists reviewer_id uuid references auth.users(id) on delete set null;

update public.visitor_azkar
set contributor_name = coalesce(nullif(contributor_name,''),'مساهم')
where contributor_name is null or contributor_name = '';

alter table public.visitor_azkar
  alter column contributor_name set default 'مساهم';

create table if not exists public.wird_admins (
  user_id uuid primary key references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

create index if not exists visitor_azkar_status_created_idx
  on public.visitor_azkar(status, created_at desc);

alter table public.wird_admins enable row level security;
alter table public.visitor_azkar enable row level security;

drop policy if exists "wird admins read self" on public.wird_admins;
create policy "wird admins read self"
on public.wird_admins for select
to authenticated
using (user_id = auth.uid());

-- نحذف السياسات القديمة ثم نعيدها بشكل متوافق مع لوحة المراجعة.
drop policy if exists "Read approved azkar" on public.visitor_azkar;
drop policy if exists "Submit pending azkar" on public.visitor_azkar;
drop policy if exists "public reads approved visitor azkar" on public.visitor_azkar;
drop policy if exists "public submits pending visitor azkar" on public.visitor_azkar;
drop policy if exists "wird admins read all visitor azkar" on public.visitor_azkar;
drop policy if exists "wird admins update visitor azkar" on public.visitor_azkar;

create policy "public reads approved visitor azkar"
on public.visitor_azkar for select
to anon, authenticated
using (status = 'approved');

create policy "public submits pending visitor azkar"
on public.visitor_azkar for insert
to anon, authenticated
with check (
  status = 'pending'
  and reviewed_at is null
  and reviewer_id is null
  and char_length(text) between 2 and 2000
  and char_length(source) between 2 and 300
  and repetition between 1 and 1000
);

create policy "wird admins read all visitor azkar"
on public.visitor_azkar for select
to authenticated
using (
  exists (select 1 from public.wird_admins a where a.user_id = auth.uid())
);

create policy "wird admins update visitor azkar"
on public.visitor_azkar for update
to authenticated
using (
  exists (select 1 from public.wird_admins a where a.user_id = auth.uid())
)
with check (
  exists (select 1 from public.wird_admins a where a.user_id = auth.uid())
  and status in ('pending','approved','rejected')
);

-- هذه الصلاحيات تصلح ظهور API DISABLED، بينما RLS يبقى هو الحماية الفعلية.
grant usage on schema public to anon, authenticated;
grant select, insert on public.visitor_azkar to anon, authenticated;
grant update on public.visitor_azkar to authenticated;
grant select on public.wird_admins to authenticated;

-- أضف حساب المشرف. غيّر البريد قبل تشغيل الملف.
do $$
declare
  admin_email text := 'chatgptma86@gmail.com';
begin
  if admin_email <> '' then
    insert into public.wird_admins(user_id)
    select id from auth.users where lower(email) = lower(admin_email)
    on conflict (user_id) do nothing;
  end if;
end $$;
