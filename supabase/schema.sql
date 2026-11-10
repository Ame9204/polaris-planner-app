-- Polaris cloud sync: run this ONCE in your Supabase project.
-- (Dashboard → SQL Editor → New query → paste everything → Run.)
-- Running it again is safe: everything is "if not exists" / "or replace".
--
-- Four tables mirror the app's own: tasks, events, schedule_blocks, notes.
-- Each row belongs to the signed-in user who created it (user_id), and the
-- security rules (RLS) make sure nobody can ever read or change anyone else's rows.

-- ---------- Tables ----------

create table if not exists public.tasks (
    id          text primary key,
    user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
    title       text not null,
    done        boolean not null default false,
    due         date,
    created_at  bigint not null,
    remind_at   timestamp,                        -- local date + time, no time zone
    changed_at  bigint not null default 0,        -- when it last changed on a device (ms)
    deleted     boolean not null default false,   -- deletion marker
    synced_at   timestamptz not null default now()-- when the server last accepted a change
);

create table if not exists public.events (
    id          text primary key,
    user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
    title       text not null,
    date        date not null,
    end_date    date,
    start_time  time,
    end_time    time,
    created_at  bigint not null,
    changed_at  bigint not null default 0,
    deleted     boolean not null default false,
    synced_at   timestamptz not null default now()
);

create table if not exists public.schedule_blocks (
    id                     text primary key,
    user_id                uuid not null default auth.uid() references auth.users (id) on delete cascade,
    title                  text not null,
    start_time             time not null,
    end_time               time not null,
    days                   integer not null default 0,
    date                   date,
    remind_minutes_before  integer,
    starts_on              date,
    ends_on                date,
    skip_dates             text not null default '',
    created_at             bigint not null,
    changed_at             bigint not null default 0,
    deleted                boolean not null default false,
    synced_at              timestamptz not null default now()
);

create table if not exists public.notes (
    id          text primary key,
    user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
    title       text not null default '',
    body        text not null default '',
    pinned      boolean not null default false,
    created_at  bigint not null,
    updated_at  bigint not null,
    changed_at  bigint not null default 0,
    deleted     boolean not null default false,
    synced_at   timestamptz not null default now()
);

-- Added later (safe to run again): series start/end and skipped days for repeating blocks
alter table public.schedule_blocks add column if not exists starts_on  date;
alter table public.schedule_blocks add column if not exists ends_on    date;
alter table public.schedule_blocks add column if not exists skip_dates text not null default '';

-- Devices ask "what changed since I last looked?", so index the time the server accepted it
create index if not exists tasks_sync_idx           on public.tasks (user_id, synced_at);
create index if not exists events_sync_idx          on public.events (user_id, synced_at);
create index if not exists schedule_blocks_sync_idx on public.schedule_blocks (user_id, synced_at);
create index if not exists notes_sync_idx           on public.notes (user_id, synced_at);

-- ---------- Newest edit wins ----------
-- If an older edit arrives late (e.g. from a phone that was offline), keep the newer one.
-- Every accepted change gets a fresh synced_at from the server's own clock, which is what
-- other devices use to find new changes (so a late arrival is never missed).

create or replace function public.polaris_keep_newest()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'UPDATE' then
        if new.changed_at <= old.changed_at then
            return old;                 -- not newer than what we have: ignore it
        end if;
        new.user_id := old.user_id;     -- a row can never change owner
    end if;
    new.synced_at := clock_timestamp(); -- the server's own clock, to the microsecond
    return new;
end;
$$;

do $$
declare t text;
begin
    foreach t in array array['tasks', 'events', 'schedule_blocks', 'notes'] loop
        execute format('drop trigger if exists keep_newest on public.%I', t);
        execute format(
            'create trigger keep_newest before insert or update on public.%I
             for each row execute function public.polaris_keep_newest()', t);
    end loop;
end $$;

-- ---------- Security: each user only sees their own rows ----------

do $$
declare t text;
begin
    foreach t in array array['tasks', 'events', 'schedule_blocks', 'notes'] loop
        execute format('alter table public.%I enable row level security', t);
        execute format('drop policy if exists "own rows" on public.%I', t);
        execute format(
            'create policy "own rows" on public.%I for all to authenticated
             using (user_id = auth.uid()) with check (user_id = auth.uid())', t);
    end loop;
end $$;
