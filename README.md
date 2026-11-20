# X Local API (Android)

Android app exposing a small **localhost-only HTTP JSON API** for X.com posts.
This repository is a repurposed Android project; its original Bytro games launcher UI has been replaced.

## Endpoints (while the app is open)
- `GET http://127.0.0.1:8787/health` — status
- `GET http://127.0.0.1:8787/v1/posts/oembed?url=https%3A%2F%2Fx.com%2Fuser%2Fstatus%2F123` — publicly embeddable post metadata via X oEmbed. No account needed. Availability depends on X and on the public post.
- `GET http://127.0.0.1:8787/v1/posts/123` — structured post data from the official X API v2. Requires `Authorization: Bearer YOUR_X_API_TOKEN` **on each call** and API access rights. No tokens are saved by the app.

## Security and limitations
- Listens **only** on `127.0.0.1`, not the LAN or internet.
- GET-only API; no general URL proxy. The embed endpoint checks the X/Twitter domain and status ID.
- Public oEmbed is not a full Twitter/X API and does not guarantee media or private-post access.
- The Android app must remain open for the local server to run; Android can stop the process.
- Never publish X API tokens in GitHub or send them to unknown services.
- The official X API may have usage charges, quotas and access limitations.

## Build
```bash
gradle assembleDebug
```
APK is uploaded by [GitHub Actions](.github/workflows/android.yml).
