# Backend API

All successful responses use the common envelope `{ success, data, message, timestamp, requestId }`. Errors use `{ success, error: { code, message, details }, statusCode, timestamp, path, requestId }`.

## Authentication

`POST /api/v1/auth/login`

```json
{ "username": "operator", "password": "your-password" }
```

Returns `accessToken`, `refreshToken`, `tokenType`, and `expiresIn` seconds. Use the access token in `Authorization: Bearer <token>`.

`POST /api/v1/auth/refresh` takes `{ "refreshToken": "..." }`; it revokes that refresh token and returns a rotated pair. `POST /api/v1/auth/logout` takes the same body and revokes the caller's refresh token. `GET /api/v1/auth/me` returns the current user without password fields.

## Users

`POST /api/v1/users` creates a bank user/admin. `GET /api/v1/users?page=0&size=20&role=BANK_USER&status=ACTIVE` lists users. `GET /api/v1/users/{id}` reads a user. `PATCH /api/v1/users/{id}` updates profile fields and role; `PATCH /api/v1/users/{id}/status` changes status; `PATCH /api/v1/users/{id}/password` resets a password. These management operations require `ADMIN`.

`PATCH /api/v1/users/me/password` changes the authenticated user's password using `currentPassword` and `newPassword`.

## Fraud

Fraud create accepts one aggregate containing transaction fields and optional `ruleHits` and `graphEntries`. `POST /api/v1/fraud-transactions` is reserved for the trusted producer service token. Bank users and admins can query `GET /api/v1/fraud-transactions` with pagination and filters, `GET /api/v1/fraud-transactions/{id}`, `GET /api/v1/fraud-transactions/{id}/rule-hits`, and `GET /api/v1/fraud-transactions/{id}/graph`. `PATCH /api/v1/fraud-transactions/{id}/review-status` updates the review state, and `GET /api/v1/fraud-transactions/summary` returns dashboard counts.
