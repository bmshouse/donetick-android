# Native chore sync

How the DoneTick Android wrapper gets chore data for its native notifications.

## Background

The app is a thin WebView wrapper around a self-hosted DoneTick server. On top of the
WebView it schedules **native Android notifications** for chores that are due. To do that
it needs the chore list — name, `nextDueDate`, `notification`, `isActive` — in Kotlin.

Early versions sniffed the list out of the WebView's own traffic by wrapping `window.fetch`
and watching for `GET /api/v1/chores/`. That broke when DoneTick's frontend adopted an
**offline-first sync engine**: the list is now hydrated from IndexedDB and refreshed via
`GET /api/v1/sync/changes?since=<cursor>`, and `/api/v1/chores/` is only hit on a cold
cache. A stop-gap (v1.0.8) had injected JS actively pull `/api/v1/chores/` itself; this
document describes what replaced it.

## Current design: native sync client

The app makes its **own** calls to `/api/v1/sync/changes`, using the JWT the user's normal
WebView login left in `localStorage['token']`. It is **foreground only** — sync runs when a
page finishes loading and on manual refresh. No WorkManager, no background service, no boot
receiver.

```
onPageFinished ─▶ WebViewViewModel.onWebViewPageFinished()
                     │
                     ├─ JwtReader.read()      evaluateJavascript("localStorage.getItem('token')")
                     │
                     └─ ChoreSyncCoordinator.sync(token)
                            │  base URL from GetServerConfigUseCase
                            ├─ SyncApi.getChanges(base, token, cursor)   loop while hasMore
                            │     └─ 404/501 ─▶ SyncApi.getChoresLegacy(base, token)   (old servers)
                            ├─ SyncStateStore.applyDelta(upserts, deletedIds, cursor)
                            └─ returns the full merged snapshot
                     │
                     ├─ uiState.choresList = mapped snapshot
                     └─ ChoreNotificationManager.scheduleChoreNotifications(...)   if anything changed
```

### Components (`app/src/main/java/org/chaosorderx/donetick/`)

| Class | Role |
|-------|------|
| `data/remote/HttpClient` | ~40-line `HttpURLConnection` GET wrapper. No OkHttp — the surface is one authenticated GET. `openConnection` is an overridable test seam. |
| `data/remote/SyncApi` / `HttpSyncApi` | `getChanges()` and `getChoresLegacy()`, returning a `PageResult` sealed type: `Ok` / `Unauthorized` (401,403) / `NotSupported` (404,501) / `TransportError` / `BadResponse`. |
| `data/sync/SyncStateStore` | Unencrypted SharedPreferences (`donetick_sync_state`): the `cursor`, the `server_base`, and the chore snapshot as an id→json map. Persisted so an Activity/process restart resumes from the cursor instead of re-pulling `since=0`. The JWT is never stored here. |
| `data/sync/ChoreSyncCoordinator` | The pagination loop + delta apply + legacy fallback. `MAX_PAGES = 100`. Returns `Success(chores, changed)` / `Unauthorized` / `NoServer` / `Failed`. |
| `data/mapper/ChoreJsonMapper` | The single chore-JSON → `ChoreItem` parser (sync objects, the `/chores/` `{res:[...]}` envelope, and the intent round-trip all share it). |
| `data/mapper/DueDateParser` | RFC3339 → epoch millis via `java.time` (core library desugaring). Replaces three duplicated `SimpleDateFormat` call sites. |

### Auth and failure handling

- **JWT**: read fresh from the page each sync. Expired (`401`) → non-destructive: cursor,
  snapshot and alarms are kept; the WebView's own engine refreshes the token via
  `POST /api/v1/auth/refresh`, and the next cycle re-reads it.
- **Not logged in**: `JwtReader.read()` returns null → sync skipped silently, retried on the
  next `onPageFinished`.
- **Server unreachable / 5xx / non-JSON**: logged; last-good list and notifications retained.
- **Empty delta**: cursor still advances and persists; `scheduleChoreNotifications` is skipped
  (it cancels + reschedules every alarm).
- **First run** (`cursor == 0`): pages through the full snapshot — note this drains the
  server's entire `choreHistories` backlog too (the client ignores histories but must reach
  `hasMore == false`), so a long-lived circle takes ~10-15 sequential requests once.

## The remaining JS hook

`WebViewActivity.injectApiInterceptorScript` still injects a ~25-line script, for two things
the native side cannot observe:

- `POST /api/v1/chores/{id}/do` → `AndroidApiCapture.onChoreMarkedDone(id)` — cancels that
  chore's notification the instant the user taps "done" in the web UI, without waiting for
  the next sync.
- any `/api/v1/auth/` call → `AndroidApiCapture.onAuthActivity()` → a debounced sync — covers
  logging in without a full page reload, after which `onPageFinished` never fires again.

`window.__dtHooksInstalled` guards against double-wrapping `fetch`/XHR.

## Server API reference

See [`docs/sync-api-notes.md`](docs/sync-api-notes.md) for the captured response schema,
chore field types, date formats, and pagination semantics.

## Not implemented

- Background sync (WorkManager) / `BOOT_COMPLETED` reschedule — notifications only refresh
  while the app is foregrounded.
- `notificationMetadata.templates` pre-due lead times.
- Per-assignee notification filtering (native code has no logged-in user id).
