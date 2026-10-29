# LMA Muay Thai & KickBoxing — Club Management Web App

## Overview

A web application for managing a Muay Thai / Kickboxing club. Members can create their own profile and the club's coach/admin has a password-protected back-office ("Registry") with attendance, revenue tracking, and statistics.

- **App name:** LMA Muay Thai & KickBoxing
- **UI language:** English
- **Design:** Modern, clean (minimalist, card-based, clear typography, generous whitespace)
- **Tech stack:** HTML, CSS, vanilla JavaScript (no frameworks)
- **Storage:** Shared Supabase (Postgres) database via its REST API — both the web app and the Android app read/write the same `members` and `revenue_entries` tables, so data entered on either platform shows up on the other. Schema + RLS policies: [supabase/schema.sql](supabase/schema.sql). No per-user auth; the public "anon" key is embedded in both clients (same trust model as the Registry's own client-side password).
- **Charts:** Chart.js (or equivalent lightweight charting lib) for all graphs

## 1. Member Profile

A phone number is a **contact**, not a unique member key: looking it up shows every profile linked to it, and the same contact can have several profiles (e.g. one parent registering multiple children). Each profile has its own fields below. After saving a profile ("Validate"): on web, the member sees the list of profiles under that contact with the choice to create another or go back to the home screen; **on Android, Validate instead returns straight to the app's first screen** (the Member Area / Registry chooser), clearing the back stack.

| Field | Notes |
|---|---|
| Status | Student (default) / Coach / Assistant (Volunteer) — self-selected at profile creation |
| Phone number | With a country-prefix selector (dial code dropdown, **+44 United Kingdom listed and selected first**) |
| First name, Last name | |
| Date of birth | Format dd/mm/yyyy |
| Gender | Male / Female |
| WhatsApp number | |
| Emergency contact | Phone number **and** contact person's name |
| Class | Girls Only / Kids / Teens & Adults — **coaches don't have one** (hidden on the form when Status = Coach) |
| Payment type | Pay as you go / Monthly membership / Transfer / Free — **coaches don't have one** |
| Selfie photo | Optional (stored as base64 text in the shared database) |
| Training experience | Number of years + number of months — **coaches don't have one** |
| **Grade/Belt** | White, Red, Yellow, Orange, Green, Blue, Purple, Brown, Black belt — **editable only from the admin Registry, never by the member themselves**. Coaches do have a grade. |

Class / Payment type / Training experience apply to students and assistants, not coaches (a coach isn't attending a class or paying to train) — the form hides that whole section when Status = Coach, and the Registry shows "-" for those fields on a coach's row/detail.

Member-facing profile form must NOT expose the grade/belt field for editing — it is read-only there (or hidden), and can only be set/changed via the password-protected Registry.

A member can also delete any of their own profiles from the Member Area's profile list (with a confirmation prompt) — this permanently removes that record from the shared database.

## 2. Registry (Admin Back-Office)

- Protected by a single password: `2204` (client-side gate — simple password prompt, checked against a SHA-256 hash rather than a plaintext comparison; this is not meant to be cryptographically secure — there is no per-user auth, so a determined attacker could brute-force the hash offline, or bypass the gate entirely by calling the Supabase API directly with the embedded anon key, same as described under Storage above).
- From the Registry, the admin can:
  - Set/change a member's **grade/belt** (the only profile field the Registry edits directly today).
  - Delete a student's profile (with a confirmation prompt) from the Students tab.
  - Enter the day's revenue and attendance after each class: pick the date (class/session pre-selects to whatever is scheduled that day) and tick which students showed up from that class's roster — typing part of a name into the quick-add box and pressing Enter ticks the first unticked match, handy for a long roster. "Number of students present" is derived from the ticks, not typed in. Picking a date & class that already has an entry loads it back into the form (amount, note, who was ticked) instead of starting blank, so it can be corrected at any time — e.g. ticking a student who arrives late — by re-saving; the "Recent Entries" list also has an Edit button that jumps straight into editing an older entry this way. Enter the amount collected and an optional note alongside the attendance.
  - View dashboards/statistics (see below).

### Registry statistics & dashboards

1. **Student counts**, broken down by, with a "Breakdown for: [class]" filter to slice any of the four by one class:
   - Age (computed from date of birth, grouped into bands)
   - Seniority / time training (years + months grouped into bands) — excludes coaches
   - Payment type (Pay as you go / Monthly membership / Transfer / Free) — excludes coaches
   - Grade/belt — rendered as a colored square per belt (no text label) instead of bars with "White Belt"/"Red Belt" etc.
2. **Trends over time** (clear charts):
   - Evolution of the above counts over time
   - Monthly revenue evolution (month by month) and weekly revenue evolution (week by week)
3. **Class-by-class, week-by-week evolution**:
   - Number of students per class, week over week
   - Revenue per class, week over week
4. **Dashboard stat tiles**: Total Students, Revenue This Month, Revenue This Week, Total Revenue, Sessions Logged, Avg Monthly Revenue, Avg Weekly Revenue. The first five are tappable — tapping one opens a detail panel (e.g. the list of this month's sessions, or the student breakdown by status/class).

## 3. Weekly Class Schedule

| Day | Time | Class |
|---|---|---|
| Monday | 18:00–19:00 | Teens & Adults |
| Wednesday | 18:00–19:00 | Girls Only |
| Wednesday | 19:15–20:15 | Kids |
| Thursday | 18:00–19:00 | Teens & Adults |
| Friday | 18:00–19:00 | Teens & Adults Sparring |
| Saturday | 09:00–10:00 | Girls Only |
| Saturday | 12:15–13:15 | Kids |
| Saturday | 13:15–14:15 | Teens & Adults |

These fixed sessions are the unit used for "revenue entry after each class" and for the class-by-class / week-by-week charts above.

## 4. Data Model Notes

- `members` table: one row per profile (id, all fields above, created_at/updated_at as epoch-millis). Web's `class`/Android's `classCategory` field is stored as `class_category` — a class-*category* key, distinct from `revenue_entries.class_key` below.
- `revenue_entries` table: `{ id, date, class_key (schedule-slot id, e.g. "mon-18-teens"), amount, student_count, attendee_ids (member ids ticked present — student_count is just this list's length, kept as its own column for quick reads/charts), note, created_at }`. At most one entry per `(date, class_key)` pair in practice — logging the same date & class again edits that same row (upsert by `id`, looked up by date+class) rather than creating a duplicate.
- Class/schedule identity is a stable key (e.g. `mon-18-teens`) matching the schedule table above, so week-over-week comparisons are reliable regardless of calendar date.
- Registry password gate is a session-scoped unlock after correct password entry (web: `sessionStorage`; Android: an in-memory flag that resets when the app process dies, with an explicit "Lock" button) — it gates the UI only, not the underlying Supabase data (see Storage above).

## 5. Android App

A native Kotlin app (no WebView/Capacitor) mirroring the web app's features, built and distributed as a downloadable APK via GitHub Actions (debug build, signed with a stable checked-in keystore so updates install cleanly over previous versions). Networking uses `HttpURLConnection` + Kotlin coroutines against the same Supabase REST API as the web app — no other networking library.
