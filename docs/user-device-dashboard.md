# User Device Dashboard MVP

## What Changed

- Added a new `frontend/` React + Vite + TypeScript dashboard for user-facing sousvide device management.
- Implemented `/devices` list and in-app device detail navigation without changing the existing Spring API contract.
- Wired the dashboard to the existing device, status, temperature, control policy, enabled, and command history APIs.

## Why It Changed

The existing project exposed backend APIs and operator-oriented observability assets, but did not include a product-style dashboard for users to see and control their own sousvide devices. This MVP prioritizes quick device status recognition, temperature trend visibility, and safe manual control from a repeatable web UI.

## Scope

- Device list summary: total, online, offline, heating.
- Device cards: online/offline, enabled, current temperature, target temperature, state, and last seen.
- Device detail: current state, temperature chart, `1h`/`6h`/`24h` range selector, control policy form, enabled toggle, command buttons, and command history.
- UI states: loading, empty, error, offline, command failure, and pending/expired command feedback.

## API Contract

The dashboard uses the existing endpoints only:

- `GET /devices`
- `GET /devices/{id}`
- `PATCH /devices/{id}/enabled`
- `GET /devices/{id}/status`
- `GET /devices/{id}/temps`
- `GET /devices/{id}/control-policy`
- `PATCH /devices/{id}/control-policy`
- `POST /devices/{id}/commands`
- `GET /devices/{id}/commands`

No JWT/Auth, ownership, cook session, notification, SSE, or WebSocket behavior is introduced in this MVP.

## How To Verify

```bash
cd frontend
npm install
npm run build
npm run dev
```

With the Spring backend running on `http://localhost:8080`, open the Vite URL and verify:

- The device list loads and the summary values reflect `/devices` plus `/devices/{id}/status`.
- Filtering works for all, online, offline, disabled, and heating.
- The detail view loads status, chart data, control policy, and command history.
- Saving a control policy calls `PATCH /devices/{id}/control-policy`.
- Enabled toggle calls `PATCH /devices/{id}/enabled`.
- `HEAT_ON`, `HEAT_OFF`, and `HOLD` call `POST /devices/{id}/commands`.
- Offline or disabled devices block manual command buttons and show an explanatory state.

## Risks And Limits

- The MVP polls instead of using SSE or WebSocket updates.
- The dashboard currently assumes the backend API is available through the Vite proxy or `VITE_API_BASE_URL`.
- There is no authentication or device ownership filtering in this frontend because those backend concerns are out of scope for this PR.
- Command success is displayed from command history state; device-level ACK latency still depends on the backend reliability flow.

