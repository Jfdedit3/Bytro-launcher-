# X Local API — Android (FxTwitter API v2)

Android app and local HTTP JSON service, bound to **127.0.0.1:8787**. It now fetches public X/Twitter post and profile data from **https://api.fxtwitter.com**. No X.com sign-in, cookies or official X developer token are required for the public FxTwitter routes.

## Usage

Paste a public X URL such as `https://x.com/NANYOKINME/status/2123791051915948136`, then tap **Fetch via FxTwitter API**. The response is displayed as formatted JSON.

## Local HTTP routes

- `GET /health`: running state; reports `engine: fxtwitter_api_v2`
- `GET /v1/resolve?url=https%3A%2F%2Fx.com%2Fuser%2Fstatus%2F123456`: fetch X post using FxTwitter API v2
- `GET /v1/resolve?url=https%3A%2F%2Fx.com%2Fuser`: fetch X profile using FxTwitter API v2
- `GET /v1/posts/public/123456`: direct FxTwitter status lookup
- `GET /v1/posts/oembed?url=...`: compatibility alias for /v1/resolve; **does not call X oEmbed**
- `GET /v1/session/latest`: legacy endpoint for a manually extracted post, if available
- `GET /v1/posts/123456`: optional legacy official X API v2 proxy requiring a bearer token in the request header

FxTwitter's upstream endpoints are `/2/status/{id}` and `/2/profile/{handle}`. The app returns the provider's JSON, including its post `status.media.photos` and `status.media.videos` when available, and appends `source: fxtwitter_api_v2`. Upstream HTTP codes and API `code` errors are preserved.

## Limits and privacy

- The local server is reachable **only on your phone** at 127.0.0.1 while the app runs.
- Post URLs and identifiers are sent to **FxTwitter**, a third-party provider. No X credentials are sent on public routes.
- FxTwitter availability, limits and access restrictions apply. It may fail to retrieve certain posts.
- The old X in-app WebView extractor remains in source code for compatibility but is no longer exposed as the main user workflow.
- App still supports immersive fullscreen mode.
- APK updates need a consistent signing certificate and increasing Android versionCode. The GitHub Actions signed release artifact is produced only when signing secrets are configured; **debug APKs from different runners may conflict and require uninstalling the previous APK**.

## Build

GitHub Actions: [.github/workflows/android.yml](.github/workflows/android.yml)

```bash
gradle assembleDebug
```

Official upstream documentation: https://docs.fxembed.com/api/introduction/
