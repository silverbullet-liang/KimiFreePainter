
# Privacy Policy

**Last updated: 2026-10-30**

KimiFreePainter (including Android and Web versions, "the Project") **takes your privacy seriously**. This policy explains how the Project handles your data.

---

## 1. We Collect No Data

1.1 The Project has **no backend server, database, analytics service, tracking SDK, or crash reporting SDK**.

1.2 The Project **does not collect, upload, share, or sell** any personal information, usage data, device information, or generated content.

1.3 The Project **contains no third-party ad SDK, analytics SDK, or push SDK**.

1.4 **Web-specific note**: The Web version is hosted on GitHub Pages (static). **The author cannot access any user data.** All data stays in the user's browser.

---

## 2. Data Storage

### 2.1 Android

| Data | Location | Encrypted |
|---|---|---|
| accessToken / refreshToken | EncryptedSharedPreferences (Android Keystore) | ✅ |
| Creation history metadata | Room DB (app private dir) | ❌ (sandbox) |
| Images | `filesDir/history/` | ❌ (sandbox) |
| Settings | DataStore | ❌ (sandbox) |

App private dir is protected by Android sandbox; other apps cannot access it.

### 2.2 Web

| Data | Location | Encrypted |
|---|---|---|
| accessToken / refreshToken | **Temporary session**: memory (lost on refresh)<br>**Remember token**: localStorage | ❌ |
| Creation history metadata | localStorage | ❌ |
| Images | IndexedDB (Blob) | ❌ |
| Settings | localStorage | ❌ |

**Web security warning**: localStorage data can be read by same-origin JavaScript, browser extensions, or XSS attacks. Default is "temporary session"; enabling "remember token" requires user confirmation.

---

## 3. Network Requests

3.1 The Project sends HTTPS requests to the following domains, **to call Kimi client internal RPC interfaces** (obtained via static analysis, not official public API):

- `www.kimi.com` (main API, `/apiv2` prefix)
- `auth.kimi.com` (auth, `/api` prefix)
- `kimi-img.moonshot.cn` (image CDN, download only)

3.2 Request headers include device identifiers (`x-msh-device-id` etc.), **used only by Kimi's server to identify the device**. The Project does not collect or upload them separately.

3.3 The Project **does not send requests to any third-party domains** (except GitHub Pages static assets).

3.4 **Web CORS note**: Web requests are subject to browser CORS policy. If Kimi's server does not allow cross-origin, the Web version falls back to "quick command mode" (generates console commands for the user to run on kimi.com).

---

## 4. WebView (Android Only)

4.1 Android holds a background Kimi WebView, **used only to read login state**.

4.2 WebView content **comes entirely from kimi.com**. The Project does not inject third-party scripts, modify page content, or intercept user input.

4.3 WebView cookies and localStorage are managed by Android WebView and kimi.com. The Project **does not read, export, or upload** any WebView data except the token.

---

## 5. Permissions (Android Only)

| Permission | Purpose | Required |
|---|---|---|
| `INTERNET` | API calls | ✅ |
| `WRITE_EXTERNAL_STORAGE` (Android 9- only) | Save images to gallery | For save feature |
| `FOREGROUND_SERVICE` | Keep creation task alive | For task running |
| `POST_NOTIFICATIONS` (Android 13+) | Task status notification | Optional |

**Not requested**: location, contacts, camera, microphone, SMS, call log, calendar, sensors, or any sensitive permissions.

**Web version requests no browser permissions** (except clipboard write, which requires HTTPS; GitHub Pages satisfies this).

---

## 6. Data Deletion

- **Android**: Settings → "Clear history", "Log out"; uninstalling the app clears all data.
- **Web**: Settings → "Clear all data" (clears IndexedDB + localStorage + memory); clearing browser site data also works.

---

## 7. Children's Privacy

The Project is not intended for children under 14. Minors should use it under guardian supervision.

---

## 8. Changes to This Policy

Maintainers **may modify** this policy at any time. Modified versions will be published in the repository. Continued use is deemed acceptance.

---

## 9. Contact

For privacy questions, please contact via GitHub Issues.
