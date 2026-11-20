# X Local API — Android

Android REST API on **127.0.0.1:8787**, running while the app is open. Public X posts and profiles are resolved via the third-party [FxEmbed](https://docs.fxembed.com/api/introduction/) API. For posts, X oEmbed is a fallback. No X developer token is necessary for public-mode lookups, but access is best-effort.

## Endpoints

- `GET /health` — API health
- `GET /v1/resolve?url=https%3A%2F%2Fx.com%2Fusername%2Fstatus%2F123` — resolve public post and media metadata
- `GET /v1/resolve?url=https%3A%2F%2Fx.com%2Fusername` — resolve public profile
- `GET /v1/posts/oembed?url=...` — backward-compatible route, now uses FxEmbed first and oEmbed fallback
- `GET /v1/posts/public/123` — direct FxEmbed public post lookup
- `GET /v1/posts/123` — official X API v2 lookup, requires `Authorization: Bearer YOUR_X_API_TOKEN`; token never saved

The `source` field indicates which provider returned the data. X oEmbed's restricted-post errors do not prevent a public-data lookup on FxEmbed. This does not circumvent access restrictions: deleted, private and otherwise unavailable posts may still return an error.

## Limitations and security

- The server is bound to localhost and is not reachable remotely.
- Third-party FxEmbed receives the requested public URL/post ID. Check its policies before use.
- Responses depend on upstream availability and may include varying metadata. No guaranteed real-time or complete results.
- Official X API may require paid access and imposes limits.
- No X login session, cookies, or API tokens are stored by this app.
- The local server stops when Android closes the app.

## Build
```bash
gradle assembleDebug
```
GitHub Actions uploads the debug APK to workflow artifacts.
