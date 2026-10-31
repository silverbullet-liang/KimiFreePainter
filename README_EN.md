
# KimiFreePainter

> A cleaner, faster, prettier way to use Kimi's free `@AI画师` image generation.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Docs: CC BY-NC-SA 4.0](https://img.shields.io/badge/Docs-CC%20BY--NC--SA%204.0-lightgrey.svg)](docs/API_REVERSE_ENGINEERING.md)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-purple.svg)]()
[![Compose](https://img.shields.io/badge/Compose-MD3%201.3.x-4285F4.svg)]()
[![Web](https://img.shields.io/badge/Web-GitHub%20Pages-orange.svg)]()

---

## ⚠️ Important Notice (Read First)

- **This project uses non-public interfaces, not Kimi's official OpenAPI.**
  The internal RPC interfaces are obtained via static analysis of the Kimi Android client.
  They are not officially authorized, may break at any time, and **may differ from Kimi's Terms of Service**.

- **The `@AI画师` image generation feature is free by design — no credits consumed.**
  No paywall bypass, no quota abuse, no account sharing — because the feature itself is free.

- **For personal learning, research, and technical exploration only.**
  Do not use for commercial purposes, bulk creation, content farms, or circumvention.

- **Account risk is yours.**
  The author and contributors are **not responsible** for any account warnings, restrictions, or bans.

- **No data collected, no data uploaded.**
  All data stays on your device / in your browser.

- **Read [DISCLAIMER_EN.md](DISCLAIMER_EN.md) and [PRIVACY_EN.md](PRIVACY_EN.md) for full terms.**

> Note: `@AI画师` is the **Chinese-only** name of the AI artist bot in Kimi community.
> It cannot be translated — you must mention it exactly as `@AI画师`.

---

## ✨ Features

- 🆓 **Truly free**: `@AI画师` is a platform-free feature, no credits consumed
- ⚡ **Faster**: 7 steps → 4 steps, all in one screen
- 🎨 **Prettier**: rebuilt with classic Material You
- 📱 **Android**: WebView auto-login + background wait + gallery save
- 🌐 **Web**: GitHub Pages live demo, manual token
- 📚 **Full interface research doc**: 69 services / 260 RPCs / 909 messages / 158 enums
- 🔒 **Privacy first**: data stays local

---

## 🌐 Live Demo (Web)

**No install needed**: [https://silverbullet-liang.github.io/KimiFreePainter/](https://silverbullet-liang.github.io/KimiFreePainter/)

> ⚠️ Web version requires manual token (Bookmarklet provided). Fewer features than Android.
> See [Web docs](docs/WEB.md).

---

## 📱 Android

### Install

1. Download the latest APK from [Releases](https://github.com/silverbullet-liang/KimiFreePainter/releases)
2. Install (allow "unknown sources")
3. Open app → Settings → Login (embedded WebView)
4. Paint screen → type prompt → Start

### Build from source

```bash
git clone https://github.com/silverbullet-liang/KimiFreePainter.git
cd KimiFreePainter
gradle assembleDebug
```

Requirements: Android Studio Hedgehog+ / JDK 17 / Android SDK 35.

Output: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📚 Interface Research Doc

Full doc: [docs/API_REVERSE_ENGINEERING.md](docs/API_REVERSE_ENGINEERING.md)

- Method: custom Python DEX parser, static analysis
- Scope: 69 services / 260 RPCs / 909 protobuf messages / 158 enums
- License: CC BY-NC-SA 4.0

---

## 🏗️ How It Works

```
User types prompt
  → Post mentioning @AI画师 (CreateMoment)
  → Kimi server free polish + image generation
  → New post published under user's account (with image + description)
  → Client polls and detects the result post (ListFeeds)
  → Download image → optional delete → show result
```

> Note: `@AI画师` is the **Chinese-only** bot name. Do not translate.

---

---

## ⚖️ Compliance & Disclaimer

- **Unofficial product**, not affiliated with Moonshot AI
- `@AI画师` is a free platform feature — **no paywall bypass, no credit consumption**
- May differ from Kimi ToS — **account risk is yours**
- No commercial use, no bulk creation, no repackaging for sale
- Full terms: [DISCLAIMER_EN.md](DISCLAIMER_EN.md)

---

## 📄 License

- Code: [GPL-3.0](LICENSE)
- Docs: [CC BY-NC-SA 4.0](docs/API_REVERSE_ENGINEERING.md)

---

## 🙏 Acknowledgements

- Thanks to [Kimi](https://kimi.com) for the free `@AI画师` image generation
- Thanks to all [Contributors](https://github.com/silverbullet-liang/KimiFreePainter/graphs/contributors)

---

## ⭐ Star History

[![Star History Chart](https://api.star-history.com/svg?repos=silverbullet-liang/KimiFreePainter&type=Date)](https://star-history.com/#silverbullet-liang/KimiFreePainter&Date)
