# Donetick sync API — field notes

Reference for the native sync client (`data/sync`, `data/remote`). Captured from a live
self-hosted Donetick server (a ~15-month-old single-user circle) plus a read of the
`donetick/donetick` Go source. All example values below are sanitized.

## Endpoint

```
GET {base}/api/v1/sync/changes?since=<int64>
```

- `base` is the configured server origin + `/api/v1`.
- Auth: `MultiAuthMiddleware` — accepts **either** `Authorization: Bearer <jwt>` (what the
  app uses, borrowed from the WebView's `localStorage['token']`) **or** a `secretkey: <token>`
  header (long-lived API token; used only for offline validation, never shipped).
  Both auth methods resolve to the same `*UserDetails`; the handler reads only `.ID` and
  `.CircleID`, so a request authenticated either way returns identical data for the same user.
- No `Origin` / `Referer` / CORS gating — a plain non-browser client works.
- `404` / `501` ⇒ server predates the sync engine ⇒ client falls back to `GET /api/v1/chores/`.

## Response envelope

```json
{
  "changes":   { "chores": [ <Chore> ], "choreHistories": [ <ChoreHistory> ] },
  "deletions": { "chores": [ 123 ],      "choreHistories": [ 456 ] },
  "cursor":    2445,
  "hasMore":   false
}
```

- `deletions.*` are **bare integer IDs** (`[]int` in Go), not objects. Empty in the capture;
  shape confirmed from source.
- `cursor` is an `int64` "sync version". Pass it back as the next `since`.
- Page size is **200 per stream** (`defaultSyncLimit`). `hasMore` is true while **any** stream
  (chores, histories, or either tombstone list) is still truncated.

## Pagination semantics (important)

- Initial sync is `since=0`. Loop: `GET ?since=<cursor>` until `hasMore == false`, accumulating
  `changes.chores` and `deletions.chores` across pages; persist the final `cursor`.
- **`cursor` advances on pages that contain zero chores.** In the capture, all 29 chores
  arrived on page 1, but `choreHistories` took ~13 more pages to drain (2400+ rows). The
  client requests histories, **ignores them**, and keeps paging only to reach the correct
  final cursor. First sync of a long-lived circle ≈ a dozen sequential requests (foreground,
  IO thread, one-time). Steady-state is a single request returning nothing.
- `MAX_PAGES` cap (100): on a very active circle, stop, persist what was accumulated + the
  last cursor, resume next foreground cycle. No lost data.

## Chore object

Same model returned by `GET /api/v1/chores/` (that endpoint just omits archived rows).
Fields seen in live data:

| field | type | notes |
|-------|------|-------|
| `id` | int | |
| `name` | string | |
| `nextDueDate` | string \| null | **`2026-09-06T00:00:00Z`** — RFC3339, whole seconds, `Z`. Null for `once` chores already done. |
| `isActive` | bool | `false` for completed one-offs / archived. Notification filter. |
| `notification` | bool | Notification filter. Independent of `notificationMetadata`. |
| `notificationMetadata` | object | Only two shapes seen: `{"circleGroupID":null}` and `{"circleGroupID":null,"dueDate":true,"templates":[{"value":0,"unit":"m"}]}`. **Do not gate scheduling on `dueDate`** — most schedulable chores omit it. `templates` = pre-due lead times (deferred feature). |
| `status` | int | `0..3` (0 NoStatus, 1 InProgress, 2 Paused, 3 PendingApproval). **Every chore was `0`** in the capture — no evidence any value should suppress notifications. Carried raw on `ChoreItem` for a future filter. Not completion — completion lives in `ChoreHistory`. |
| `assignedTo` | int \| null | Not used for notifications. |
| `assignees` | `[{userId:int}]` | |
| `frequencyType` | string | `once`, `daily`, `weekly`, `interval`, … |
| `frequency` | int | |
| `frequencyMetadata` | object | Present even when empty (`{"unit":null,"time":"","timezone":"","weekPattern":null}`). |
| `description` | string | **`""`** when unset, not null. May contain HTML. |
| `priority` | int | |
| `completionWindow` | int? | Sometimes absent. |
| `isRolling`, `requireApproval`, `isPrivate`, `isRolling` | bool | |
| `labelsV2` | array | Empty in capture. |
| `circleId`, `createdBy`, `updatedBy` | int | |
| `createdAt`, `updatedAt` | string | RFC3339 with **nanosecond** fraction: `2025-06-24T04:00:01.938729751Z` (variable fraction length). Not used by the client. |
| `syncVersion` | int64 | |

Response is already scoped to what the authenticated user may see (circle chores + that
user's private chores). It is **not** filtered to "assigned to me" — notifications fire for
every visible chore, matching current app behavior.

## ChoreHistory object (client ignores these)

```json
{ "id": 3, "choreId": 6, "performedAt": "…Z", "completedBy": 1, "assignedTo": 1,
  "notes": null, "dueDate": "…Z", "updatedAt": "…Z",
  "createdAt": "0001-01-01T00:00:00Z", "status": 1, "syncVersion": 30 }
```

`createdAt` can be the Go zero time (`0001-01-01T00:00:00Z`). `nextDueDate` on the chore
already reflects the next occurrence after a completion, so history is not needed for
notification correctness in v1.

## Date parsing

`nextDueDate` in real data is simple enough for the old `yyyy-MM-dd'T'HH:mm:ss'Z'` format —
the current `ChoreNotificationManager.parseIsoDate` bug is **latent, not active**. The client
still parses via `java.time` (`OffsetDateTime` / `Instant`, core-library-desugared) so a
future server emitting offsets or fractional seconds on `nextDueDate` doesn't silently break
scheduling, and so the three duplicated parsers collapse into one `DueDateParser`.

## Open items deferred

- `notificationMetadata.templates` pre-due lead times (one alarm per template).
- `status == 2` (Paused) possibly suppressing notifications — no live data to confirm.
- Per-assignee notification filtering — native code has no logged-in user id.
