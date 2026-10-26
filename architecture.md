# BeamOps architecture

BeamOps is the Beam Networks staff app for Android (package `com.helper.beamnetworks`). It is a
single-module Kotlin app that runs on the live Beam billing system (`billing.beamnetworks.co.ke`)
and adds phone features the website cannot: caller ID, background alerts, an in-app updater.
It is distributed outside the Play Store (see [deployment.md](deployment.md)).

> **Keep this file current.** Whenever a change alters how the app is put together (a new
> package, screen or route, data source, background job, permission, dependency, build or hosting
> step), update the matching section here in the same piece of work, before committing. See
> `CLAUDE.md`. Describe what exists now, not history: history belongs in `CHANGELOG.md`.

## 1. Stack at a glance

| Area | Choice |
|---|---|
| Language / build | Kotlin 2.3, Android Gradle Plugin 8.13, Gradle 9.2 wrapper, JDK from Android Studio |
| SDK | `minSdk` 29 (Android 10), `compileSdk`/`targetSdk` 36 |
| UI | Jetpack Compose (BOM 2026.06) + Material 3, own design system on top |
| Navigation | Navigation Compose 2.9, type-safe `@Serializable` routes, deep links |
| State | `ViewModel` + `StateFlow` + coroutines; manual DI (`AppContainer`) |
| Network | Retrofit 3 + OkHttp 4.12 + kotlinx.serialization |
| Extra storage | Firebase Realtime Database (expenses, ticket-issue suggestions only) |
| Background | WorkManager (notification checks, daily installation reminders) |
| Maps / location | osmdroid (OpenStreetMap), Google Play Services Location |
| Tests | JUnit 4 unit tests in `app/src/test` |

Versions live in `gradle/libs.versions.toml` and `app/build.gradle.kts`; this file does not
repeat them beyond the table. Google Sign-In / Sheets libraries are still in the build but unused
(left over from the pre-redesign app).

## 2. Big picture

```
                    ┌──────────────────────────────────────────┐
  Staff phone       │  BeamOps app                             │
                    │  UI (Compose)  ──►  ViewModels           │
                    │        │                │                │
                    │        ▼                ▼                │
                    │   AppContainer  (repositories, session,  │
                    │   notification feed, caller directory)   │
                    └───────┬───────────────┬──────────────────┘
                            │ HTTPS + JWT   │ Firebase SDK
                            ▼               ▼
              Beam billing API         Firebase Realtime DB
        (customers, payments, jobs,     (expenses, issue
         tickets, stock, network…)       suggestions)

  Background: WorkManager ─► NotificationChecker ─► system notifications + in-app feed
  Phone calls: CallReceiver ─► CallerDirectory / API ─► caller ID overlay
  Updates:    AppUpdater ─► version.json + APK on the Vercel download site
```

**Sources of truth.** Customers, payments, invoices, tickets, field jobs, inventory, network
status, staff and settings all come from the Beam billing API, so the app and the website always
agree. Only **expenses** and **ticket-issue suggestions** stay in Firebase, because the API has no
equivalent. The old Firebase installs/tickets/stock are intentionally not shown any more.

## 3. Source layout (`app/src/main/java/com/helper/beamnetworks`)

| Package / file | What lives there |
|---|---|
| `BeamNetworksApplication`, `MainActivity` | App entry: builds the `AppContainer`, creates notification channels, schedules the reminder worker; single activity hosting `BeamApp` |
| `data/` | `AppContainer` (DI), `Repositories.kt` (customers, payments, tickets, jobs, inventory over the API), `FirebaseRepositories.kt` (expenses, issues), `Models.kt` (UI-facing models), `RemoteList` / `Loadable` (cached, refreshable lists), `CallerDirectory` (offline caller ID cache) |
| `data/api/` | `BeamApi` (Retrofit interface, every path keeps its trailing slash), `ApiClient` (auth + refresh), `SessionStore` (encrypted session), `ApiModels*.kt` (DTOs), `apiCall` / `ApiException` (readable errors) |
| `data/notifications/` | `NotificationChecker` (polls the API for events), `NotificationRules` (pure change detection, unit-tested), `NotificationModels` (categories, feed, settings, deep links), `Notifier` (system notifications), `NotificationWorker` |
| `data/update/` | `AppUpdater`: in-app updater state machine |
| `ui/BeamApp.kt` | App shell: start-up overlays, login vs main shell, drawer, top/bottom bars, NavHost, sheet host |
| `ui/navigation/` | `Routes.kt` (route classes) and `containerViewModel` helper |
| `ui/components/` | Design system: chrome (top/bottom bars), surfaces (cards, rows), forms, badges, drawer, permission helpers |
| `ui/theme/`, `ui/icons/` | Colours (navy `#2E449C`, orange `#F26223`), type, shapes, `BeamIcons` |
| `ui/screens/*` | One folder per area: `home`, `customers`, `installs` (field jobs), `tickets`, `account` (profile, stock, income & expenses, money tools), `registers` (payments, invoices, SMS log, hotspot, settings), `notifications` (feed, notification settings, network status/tools), `login`, `setup` (phone setup, update prompt) |
| `ui/sheets/` | `SheetViewModel` + `Sheets.kt`: every bottom-sheet form (create customer/job/ticket/payment/expense/product, complete job, extend) |
| root files | Caller ID (`CallReceiver`, `CallerIdPopup`), `CallLog*` and `MapScreen` pickers, `InstallationReminderWorker` |
| `util/` | Formatting, phone/amount parsing, `Mpesa.kt` (parses a pasted M-Pesa message) |

## 4. Start-up and dependency injection

1. `BeamNetworksApplication.onCreate` builds one `AppContainer`, creates notification channels
   and schedules the daily `InstallationReminderWorker`.
2. `AppContainer` owns the long-lived objects: `SessionStore`, the Retrofit `api`, all
   repositories, `NotificationFeed/Settings/Checker`, `Notifier`, `CallerDirectory`, a toast
   controller and an app-wide coroutine scope. It also performs `login`/`logout` and clears cached
   data when the session ends. It starts/stops the background worker as the session appears or
   disappears.
3. ViewModels get the container through `containerViewModel { app, savedState -> ... }`.
4. `BeamApp` (Compose root) shows `LoginScreen` when there is no session, otherwise `BeamShell`.
   Two overlays sit on top of either: `PhoneSetupScreen` (permission walk-through) and
   `UpdatePrompt` (update dialogs). `AppUpdater.check` runs once per launch after sign-in.

## 5. Data layer

**Auth.** Staff sign in with `auth/tenant/login/`. `SessionStore` keeps access + refresh tokens,
encrypted with an AES-GCM key in the Android Keystore, in private SharedPreferences. `ApiClient`
adds the bearer token to every call and, on a 401, performs **one serialised refresh** (refresh
tokens rotate and are single-use). Only a *rejected* refresh token signs the user out; a dropped
connection does not. If the account has `must_change_password`, the app routes to the login flow
and background checks pause.

**Lists.** `RemoteList` holds one API-backed list with `Loadable` loading/error/loaded flags;
concurrent refreshes share one load, and `update { }` applies a local change after a successful
write so the UI updates without a reload.

**Field jobs.** The API's job model has no fields for phone, router or PoE, so installation
details are encoded as lines in the job `description`: the location, then `Phone: …`,
`Router: yes|no`, `PoE: yes|no`, `Notes: …`. `JobRepository.toJob` decodes them; a description
without a `Router:` line is treated as plain notes (jobs made on the website).

**Expenses and issues** are written to Firebase nodes `expenses` and `issues`
(`FirebaseRepositories.kt`). Expenses store item, amount (string), date (`dd/MM/yyyy`),
category, shop ("paid to") and notes.

**Caller directory.** `CallerDirectory` is a JSON file of customers/jobs used to identify a
caller offline; the daily worker and a refresh refill it.

## 6. UI architecture

**Shell.** `BeamShell` combines a modal navigation drawer (all areas), a top bar (menu or back,
title, alerts bell, profile avatar) and a bottom bar with four tabs (Home, Customers, Jobs,
Tickets) plus the centre create button. `goToTab` switches tabs with state save/restore; the
Home tab pops back to the start destination.

**Routes** (`ui/navigation/Routes.kt`): `HomeRoute`, `CustomersRoute(filter)`,
`CustomerDetailRoute`, `InstallsRoute`, `InstallDetailRoute`, `TicketsRoute`,
`TicketDetailRoute`, `AccountRoute` (the **Profile** page), `StockRoute`, `AccountsRoute`
(income & expenses), `NotificationsRoute`, `NotificationSettingsRoute`, `NetworkRoute`,
`CallLogRoute` and `MapRoute` (pickers), plus register routes (payments, invoices, SMS log,
hotspot, announcements, sessions, customer map, settings) declared next to `BeamApp`. Several
routes have `beamnetworks://app/...` deep links, used by notifications.

**Sheets.** All create/edit forms are bottom sheets driven by `SheetViewModel`. `SheetType`
selects the form, `Draft` holds the field values, `submit()` validates and calls a repository.
The older full-screen pickers (call log, map) run as separate routes; `openPicker` parks the
sheet, the picker writes its result into the previous back-stack entry's `SavedStateHandle`, and
`resumeAfterPicker` restores the sheet with the result. The expense and payment sheets have a
"Paste M-Pesa message" button that fills fields from the clipboard (no SMS permission).

**Profile** (`ProfileScreen`): the avatar opens only the signed-in user's profile: name, email,
phone, role, business; edit name/phone (`PATCH customers/staff/{id}/`), change password
(`auth/tenant/change-password/`, which returns fresh tokens), and sign out. Everything else
(network, stock, accounts, sessions, call log, map, settings, app alerts, caller ID) is in the
drawer or Settings.

**Design system.** `ui/components` and `ui/theme` hold the reusable pieces (cards, list rows,
pill tabs, form fields, sheets, badges, toasts). Screens use these rather than raw Material.

## 7. Background work and device features

- **Alerts.** `NotificationWorker` runs every 15 minutes (and the app also checks every minute
  while open). `NotificationChecker` runs sources (routers, downtime, outages, monitoring alerts,
  payments, suspensions, tickets, Beam account, SMS/PayHero balances, stock, jobs assigned to me, and a new BeamOps version on the download site, announced once per `versionCode`),
  remembers what it has already reported in `CheckerState`, and the first run of each source only
  records a baseline. New events go to `NotificationFeed` (last 200, stored on disk) and
  `Notifier` (system notifications, one channel per `NotificationCategory`). Each category can be
  switched off in App alerts.
- **Daily reminders.** `InstallationReminderWorker` reminds about jobs three days out and
  refreshes the caller directory.
- **Caller ID.** `CallReceiver` listens for `PHONE_STATE`; on ringing it looks the number up in
  `CallerDirectory` (instant, offline), falls back to one API refresh, and shows
  `CallerIdPopup`, an overlay window that needs "display over other apps". There is no foreground
  service.
- **Permissions.** `PhoneSetup` is a step-by-step walk-through shown at start-up when something is
  missing (notifications, phone and call log, overlay, battery optimisation, install updates,
  location). Answers are remembered; Settings › This phone › Phone permissions reopens it. The
  battery step opens Android's "ignore battery optimisation" prompt directly. The app asks for no
  SMS permission: Android blocks it for apps installed outside the Play Store.
- **In-app updates.** `AppUpdater` reads `<UPDATE_BASE_URL>version.json`, compares
  `versionCode` with the installed one, downloads the APK, verifies its SHA-256, and opens
  Android's installer through a `FileProvider`. `UpdatePrompt` shows the dialogs, including the
  release notes since the installed version (`history` in `version.json`, newest first).
  `UPDATE_BASE_URL` is a `BuildConfig` field in `app/build.gradle.kts`.

## 8. Build, release and hosting

- **Signing.** Release builds are signed with a key referenced by `keystore.properties`
  (gitignored). Android only installs an update signed with the same key, so the key must be kept
  and backed up.
- **Versioning.** `versionCode` (integer, must rise every release) and `versionName` in
  `app/build.gradle.kts`. The changelog has a matching `## [x.y]` section.
- **Two static sites on Vercel**, both deployed from the CLI (not from git):
  1. *Changelog* — project `beam-networks-changelog`, folder `changelog-site/`, public alias
     `beam-networks-changelog.vercel.app`. Source of truth is `CHANGELOG.md`.
  2. *Downloads* — project `beam-ops-downloads`, folder `release-site/` (generated, gitignored),
     alias `beam-ops-downloads.vercel.app`. Under a hard-to-guess folder it serves `index.html`
     (the download page), `version.json`, `logo.png` and `BeamOps-<version>.apk`.
- **`scripts/publish-release.ps1`** builds the signed APK, writes `version.json` (including
  `history`, parsed from `CHANGELOG.md`), fills `scripts/download-page.html` and deploys.
  Full steps and pitfalls are in [deployment.md](deployment.md).

## 9. Testing

Unit tests (`app/src/test/java/com/helper/beamnetworks`): `NotificationRulesTest`,
`CallerDirectoryTest`, `FormatTest`, `BalanceDtoTest`, `MpesaTest`, `UpdateInfoTest`. UI and
real-API behaviour are checked by hand on a phone. Run
`./gradlew :app:testDebugUnitTest :app:assembleDebug` with Android Studio's JDK. Android lint
currently crashes on `MapScreen.kt` (an Accompanist lint-rule bug), so it is not run.

## 10. Conventions and invariants

- Every user-visible change updates `CHANGELOG.md` first (public page: no secrets, no customer
  data, no internal paths). Release notes shown to staff are generated from it.
- Never reuse a `versionCode` for different content: the updater cannot tell the builds apart.
- Never commit `keystore.properties`, `*.jks`, `app/google-services.json`, `.env*`, `.vercel`
  or `handoff/` (design reference).
- Trailing slashes on API paths are required.
- Write files without a byte-order mark when scripts generate JSON for Vercel or the app.

## 11. Known limits and unverified assumptions

- Editing a profile uses the staff endpoint; the API may restrict it to admins, in which case
  non-admins see an error.
- The billing API does not text technicians on assignment (it notifies the customer); technicians
  get an in-app notification from their own phone's checker instead.
- Sideloaded installs may hit Android "restricted settings" and Play Protect warnings for
  sensitive permissions (call log, overlay); the download page explains this.
- `READ_CALL_LOG` can also be blocked on sideloaded builds, which affects the call log picker and
  caller ID number lookup on affected phones.
