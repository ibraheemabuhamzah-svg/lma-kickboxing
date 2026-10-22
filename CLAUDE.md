# LMA Muay Thai & KickBoxing — Club Management Web App

## Overview

A web application for managing a Muay Thai / Kickboxing club. Members can create their own profile and the club's coach/admin has a password-protected back-office ("Registry") with attendance, revenue tracking, and statistics.

- **App name:** LMA Muay Thai & KickBoxing
- **UI language:** English
- **Design:** Modern, clean (minimalist, card-based, clear typography, generous whitespace)
- **Tech stack:** HTML, CSS, vanilla JavaScript (no frameworks)
- **Storage:** Browser `localStorage` (no backend)
- **Charts:** Chart.js (or equivalent lightweight charting lib) for all graphs

## 1. Member Profile

Each member can create/edit their own profile. Fields:

| Field | Notes |
|---|---|
| Status | Student (default) / Coach / Assistant (Volunteer) — self-selected at profile creation |
| Phone number | With a country-prefix selector (dial code dropdown) |
| First name, Last name | |
| Date of birth | Format dd/mm/yyyy |
| Gender | Male / Female |
| WhatsApp number | |
| Emergency contact | Phone number **and** contact person's name |
| Class | Girls Only / Kids / Teens & Adults |
| Payment type | Pay as you go / Monthly membership / Transfer |
| Selfie photo | Optional (stored as base64 in localStorage) |
| Training experience | Number of years + number of months |
| **Grade/Belt** | White, Red, Yellow, Orange, Green, Blue, Purple, Brown, Black belt — **editable only from the admin Registry, never by the member themselves** |

Member-facing profile form must NOT expose the grade/belt field for editing — it is read-only there (or hidden), and can only be set/changed via the password-protected Registry.

## 2. Registry (Admin Back-Office)

- Protected by a single password: `LmaKickBoxingR` (client-side gate — simple password prompt; stored data stays in localStorage, this is not meant to be cryptographically secure).
- From the Registry, the admin can:
  - View/edit every member's full profile, including setting/changing the **grade/belt**.
  - Enter the day's revenue after each class (date, class/session, amount, optional note).
  - View dashboards/statistics (see below).

### Registry statistics & dashboards

1. **Student counts**, broken down by:
   - Age (computed from date of birth, grouped into bands)
   - Seniority / time training (years + months grouped into bands)
   - Payment type (Pay as you go / Monthly membership / Transfer)
   - Grade/belt
2. **Trends over time** (clear charts):
   - Evolution of the above counts over time
   - Monthly revenue evolution (month by month)
3. **Class-by-class, week-by-week evolution**:
   - Number of students per class, week over week
   - Revenue per class, week over week

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

## 4. Data Model Notes (localStorage)

- `members`: array of member profile objects (id, all fields above, createdAt/updatedAt).
- `revenueEntries`: array of { id, date, classId (day+time+class name), amount, note }.
- Class identity should be a stable key (e.g. `mon-18-teens-adults`) matching the schedule table above, so week-over-week comparisons are reliable regardless of calendar date.
- Registry password gate is a session-level unlock (e.g. sessionStorage flag) after correct password entry — does not encrypt the underlying localStorage data.

## 5. Android App

A companion Android app (wrapping the same web app, e.g. via a WebView/Capacitor shell) is planned, built and distributed as a downloadable APK via GitHub.
