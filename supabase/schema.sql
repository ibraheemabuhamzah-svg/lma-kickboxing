-- LMA Muay Thai & KickBoxing — shared Supabase schema
-- Run this once in the Supabase SQL Editor (Project → SQL Editor → New query).

create table if not exists members (
  id text primary key,
  role text default 'student',
  first_name text default '',
  last_name text default '',
  dob text default '',
  gender text default '',
  phone_prefix text default '+971',
  phone_number text default '',
  whatsapp text default '',
  emergency_name text default '',
  emergency_phone text default '',
  class_category text default '',
  payment_type text default 'payg',
  training_years int default 0,
  training_months int default 0,
  selfie_base64 text,
  grade text default 'white',
  created_at bigint,
  updated_at bigint
);

create table if not exists revenue_entries (
  id text primary key,
  date text default '',
  class_key text default '',
  amount numeric default 0,
  student_count int default 0,
  note text default '',
  created_at bigint
);

alter table members enable row level security;
alter table revenue_entries enable row level security;

-- No per-user auth in this app (same trust model as the client-side
-- password gate already in use) — the public "anon" API key can read and
-- write every row. Anyone who extracts that key from the published web
-- app or the APK can bypass the Registry password and read/write data
-- directly via the Supabase API. Acceptable for a small club roster, but
-- worth knowing: don't put anything more sensitive than this app already
-- asks members for.
create policy "anon read members" on members for select using (true);
create policy "anon write members" on members for insert with check (true);
create policy "anon update members" on members for update using (true);
create policy "anon delete members" on members for delete using (true);

create policy "anon read revenue" on revenue_entries for select using (true);
create policy "anon write revenue" on revenue_entries for insert with check (true);
create policy "anon update revenue" on revenue_entries for update using (true);
create policy "anon delete revenue" on revenue_entries for delete using (true);
