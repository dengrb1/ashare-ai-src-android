# Android API and Xiaomi platform requirements

This document separates client fixes from work that requires the API service or Xiaomi's platform.

## Completed in the Android client

- K-line periods now use the API contract: `1m`, `5m`, `15m`, `30m`, `60m`, and `day`.
- K-line ranges match WebUI: 1 day, 5 days, 1 month, 3 months, 6 months, and 1 year. Requests use timezone-aware `start`/`end`, 1,200-bar chunks, stable sorting, and timestamp deduplication.
- Research settings consume and submit `automatic_reports` with exactly slots A and B. Manual and automatic forms enforce positive budgets, `per_symbol_budget <= total_budget`, and `CUSTOM` symbol rules before sending.
- Notification polling uses persistent deduplication, standard Android notification channels, private lock-screen visibility, explicit internal deep links, and a normal-notification fallback.
- HyperOS detection checks `notification_focus_protocol`, `persist.sys.feature.island`, and `canShowFocus`. OS2 receives focus data; OS3 receives Super Island data only when the system and permission checks pass.

No API change is needed for the K-line or current research-settings fixes above.

## Required for reliable push delivery

The current foreground service is user-enabled best-effort polling. Android 15 limits `dataSync` foreground-service runtime, and the app cannot receive reliably after process death without vendor push.

Add authenticated device registration endpoints:

```http
POST /api/v1/devices
DELETE /api/v1/devices/{device_id}
```

Suggested registration request:

```json
{
  "platform": "ANDROID",
  "push_provider": "MIPUSH",
  "registration_id": "opaque MiPush regId",
  "app_version": "1.0.0",
  "device_name": "optional user-visible name"
}
```

Server requirements:

- Bind every device to the authenticated user. Never accept a user ID from the request body.
- Upsert by a server-side digest of `(provider, registration_id)` and rotate ownership safely after logout or account changes.
- Encrypt registration IDs at rest. Never log access tokens, MiPush secrets, full registration IDs, holdings, or notification bodies.
- Keep the MiPush app secret on the server only. Do not package it in Android resources or `BuildConfig`.
- Send by registration ID, which Xiaomi requires for focus/Super Island push scenarios.
- Rate-limit registration and push attempts, validate payload lengths, expire stale devices, and remove invalid registration IDs returned by MiPush.
- Use an opaque notification ID in push payloads. Fetch sensitive body data through the authenticated notifications API after the app opens.
- Revoke or disable the device on logout. `DELETE` must be idempotent and ownership checked.

Recommended optional delivery endpoint:

```http
POST /api/v1/devices/{device_id}/deliveries
```

Accept only a notification ID plus a bounded enum such as `RECEIVED`, `OPENED`, or `DISMISSED`; make it idempotent and do not accept arbitrary analytics properties.

## Notification API contract improvements

For useful deep links, every notification should return:

- `notification_id`, `notification_type`, `severity`, `title`, `body`, `created_at`, and `read_at`.
- `resource_type`: one of `RESEARCH`, `REPORT`, `TRADE_PLAN`, `BACKTEST`, or `EXIT_ADVICE`.
- `resource_id`: the canonical opaque resource ID.
- `resource_url`: an app-relative URL such as `/reports?date=2026-07-27&run_id=<id>`.

The Android client treats all URLs as untrusted and only accepts known routes, ISO dates, A-share symbols, and bounded resource IDs. The server should still generate canonical URLs and reject open redirects.

## Xiaomi platform work

The package and channels must be approved in Xiaomi's developer console. Production identifiers currently are:

- Package: `com.ashareai.app`
- Channels: `monitor`, `alert`, `progress`
- Business identifier in focus payloads: `ashare_market_monitor`

Request focus-notification permission for the package and all three channels, then confirm the approved Super Island template matches the submitted `bigIslandArea` and `smallIslandArea` data. Client-side permission checks cannot grant this approval.

MiPush payloads must include `miui.focus.param`; image URLs, when used server-side, must be HTTPS and comply with Xiaomi's size and aspect-ratio limits. Standard Android title/body fields must always remain present so denied or unsupported focus notifications degrade normally.

## Acceptance checks

1. A normal Android device receives an alert after the app process has been killed.
2. HyperOS 2 displays the same alert as a focus notification and opens the correct in-app resource.
3. HyperOS 3 with approved focus permission displays the selected Super Island template; permission denial falls back to a normal notification.
4. Duplicate push and polling delivery for the same `notification_id` produces one user-visible alert.
5. Logout invalidates the device binding, and another account on the same phone cannot receive the previous account's events.
6. No access token, MiPush secret, registration ID, holdings, or notification body appears in server or client logs.

Official references checked on 2026-07-27:

- Xiaomi HyperOS Super Island guide: https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131
- Xiaomi focus notification FAQ: https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2146
