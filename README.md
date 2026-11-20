# X Local API — Independent Android extractor

Own Kotlin REST API on `http://127.0.0.1:8787`. **No FxTwitter, FxEmbed, Nitter, or other third-party scraping API.**

## Architecture

The app extracts public data directly from X-operated resources:
1. For public posts: X's own syndication widget JSON (`cdn.syndication.twimg.com`). The app computes the widget token and normalizes the result itself.
2. If the syndication feed does not return usable data and a username is known: X.com public HTML Open Graph metadata.
3. For profiles: X.com HTML Open Graph metadata.
4. Optional authenticated X API v2 endpoint for owners of X developer credentials.

The syndication endpoint is undocumented and can change. It does not grant access to nonpublic content or guarantee every public post is available.

## Endpoints
- `GET /health` — health and engine info
- `GET /v1/resolve?url=https%3A%2F%2Fx.com%2Fuser%2Fstatus%2F123` — auto-resolve post
- `GET /v1/resolve?url=https%3A%2F%2Fx.com%2Fuser` — public profile metadata
- `GET /v1/posts/public/123` — own normalized public post JSON
- `GET /v1/posts/oembed?url=...` — legacy alias for /v1/resolve, **not** an oEmbed API call
- `GET /v1/posts/123` — official X API v2, requires `Authorization: Bearer TOKEN`

A successful public post response returns `id`, `url`, `text`, `author`, `created_at`, `media` and `source`. Video variants are included when the X widget provides them. A `partial` flag indicates incomplete data.

## Limitations
- Server is localhost-only and only operates while the Android app remains active.
- Only public X resources are retrieved, without a user login.
- The public syndication source is undocumented, may rate-limit and sometimes omits fields.
- Private/deleted/restricted posts cannot be accessed by this extractor.
- X may deny anonymous requests. No misleading successful responses are returned for login placeholders.
- The official X API v2 may require paid access. No API keys are stored.

## Build
```bash
gradle assembleDebug
```
The GitHub Actions workflow builds a debug APK on push to main and uploads it as an artifact.


## Authenticated browser extraction (Android)

X can return `TweetTombstone` to anonymous syndication even when a signed-in user can open the post. The Android app now has a **Open X / Sign in / Extract post** button that launches an embedded X WebView:

1. Sign in directly to X in the browser (if X supports embedded login).
2. Open the post inside this WebView and wait for it to render.
3. Tap **Extract visible post JSON**. This evaluates a read-only DOM extraction script and captures the visible text, author handle, and visible image/video URLs.
4. Return to the API screen and tap **Show last authenticated extraction**.

The app's own HTTP server then exposes the last explicitly captured result at `GET /v1/session/latest` (no login data). This route is accessible only from **the same device** (`127.0.0.1`) while the application is running. Any local app may be able to call it, so do not extract sensitive/private posts.

This is **not a general authenticated X API proxy**. It only reads an explicitly opened post, does not access X cookies or credentials from the local API, and cannot automatically fetch arbitrary restricted posts. Embedded sign-in may be blocked by X, and the DOM selectors may need updates when X changes its UI. Media streaming URLs are not guaranteed to be exposed by a video element.

## Fullscreen and APK upgrades

Both the main UI and embedded X browser use immersive fullscreen (system status/navigation bars hidden, swipe to reveal temporarily).

GitHub Actions increments `versionCode` using `10000 + GITHUB_RUN_NUMBER`; package ID remains `com.jfdedit3.bytrolauncher`.

**IMPORTANT:** Android upgrades require both the same package ID and the same signing certificate. The default GitHub-generated debug keys are not stable across ephemeral runners. To receive an upgradeable signed release APK, configure both GitHub Actions repository secrets:

- `X_LOCAL_KEYSTORE_BASE64`: Base64 of your privately stored JKS signing keystore.
- `X_LOCAL_KEYSTORE_PASSWORD`: password for its keystore and key (alias `xlocalapi`).

Use GitHub repository **Settings → Secrets and variables → Actions → New repository secret**. Keep the original keystore and password safely backed up. Do not commit an unencrypted signing keystore or credentials to this public repository.

Once secrets are configured, the workflow publishes artifact **x-local-api-signed-update** containing `x-local-api-update.apk`. Always install this signed APK for future updates. Debug APKs are for testing only and cannot be expected to update other runner-built APKs.

**One-time migration:** an already installed APK signed with another certificate cannot be upgraded in place. Android requires uninstalling that old build once (or obtaining its original signing key). After installing the first consistently signed release, subsequent signed releases can update it without uninstalling. Note that uninstalling can clear local app data including WebView login sessions.
