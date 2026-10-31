
# Disclaimer

**Last updated: 2026-10-30**

This document is the disclaimer for KimiFreePainter (including Android and Web versions, "the Project").
**Please read and understand this disclaimer completely before using the Project. Once you download, install, run, or use the Project in any way, you are deemed to have read, understood, and agreed to all terms below.**

---

## 1. Nature of the Project

1.1 The Project is an **open-source, unofficial client tool for personal learning and use only**, and **also a client interface research project**.

1.2 The Project is **not** an official Kimi (Moonshot AI) product and has **no affiliation, partnership, authorization, or endorsement** with Beijing Moonshot Technology Co., Ltd. or its affiliates.

1.3 **【Core Statement】** The interfaces used by the Project are **not Kimi's official OpenAPI**, but **internal RPC interfaces** obtained via **static analysis** (DEX parsing, protobuf descriptor reconstruction, bytecode instruction flow analysis) of the Kimi Android client (`com.moonshot.kimichat`). These interfaces:

- **are not publicly documented** in Kimi's official developer docs;
- **are not officially authorized** for third-party clients;
- **may change, break, or be blocked by the server at any time**;
- **may differ from Kimi's Terms of Service**.

1.4 The interface research document (`docs/API_REVERSE_ENGINEERING.md`) is the result of static analysis of a publicly downloadable APK. It **does not contain** any private data, private keys, user privacy information, or trade secrets obtained from Kimi's servers. The document is a **factual description of client code behavior**, falling under **interoperability research**.

1.5 **【Important Note on "Free"】** The `@AI画师` image generation feature used by the Project is **a platform feature free to all logged-in Kimi community users**, and **consumes no credits** (no billing, no quota, no token deduction, no membership required). The Project **does not bypass any paywall** and **does not "abuse free quota"** — because the feature itself is free.

1.6 The Project **does not provide, proxy, store, or cache** any AI models, compute, or content. All image generation is **executed entirely by Kimi's server under the user's own account**. The Project is only a **convenient trigger tool**.

1.7 The Project **does not store, proxy, or cache** any user-generated content to third-party servers. Android version: all data stays on device; Web version: all data stays in the user's browser. **The author does not operate any backend server.**

1.8 **Web-specific note**: The Web version is deployed on GitHub Pages (static, no backend). The token manually entered by the user is stored locally in the browser and **is not uploaded to any server** (the author has no server).

---

## 2. Legal Notes on Interface Research

2.1 **Legality of static analysis**: In most jurisdictions (including China, the US, and the EU), **static analysis** of **lawfully obtained software** to understand its **interface behavior** is generally considered **fair use** or **protected expression** for **interoperability, security research, and academic research** purposes. The Project publishes its research document on this basis.

2.2 **Non-infringement claims**: The Project:

- **does not copy** any source code, resource files, trademarks, or logos of the Kimi client;
- **does not distribute** modified Kimi clients;
- **does not bypass** any technical protection measures (DRM, encryption, signature verification);
- **does not obtain** any non-public user data or trade secrets;
- **only describes** the network communication protocol **that has already occurred** between client and server.

2.3 **Independence of the document**: The research document is an **independent technical research result**, consisting of **factual descriptions** and not containing Kimi's original expression (such as UI design, copy, or algorithm implementation).

2.4 **Risk notice**: Although the Project believes the publication of the research document has a reasonable legal basis, **it cannot be excluded** that Kimi or its affiliates may take legal action. **If the rights holder makes a reasonable request, the maintainers will cooperate** (including but not limited to modifying, taking down, or deleting relevant content).

---

## 3. Use at Your Own Risk

3.1 **Account risk**: Using the Project **may differ from Kimi's Terms of Service** (especially regarding "recommended use of official client" and "non-standard access"). **Any consequences such as account warnings, feature restrictions, temporary bans, permanent bans, device fingerprint blacklisting, or linked-account bans are solely the user's responsibility.**

3.2 **Legal risk**: Users must ensure their use complies with local laws and Kimi's Terms of Service.

3.3 **Data risk**: Android version stores login credentials (encrypted) and creation history on device; Web version stores credentials (possibly plaintext) and history in the browser. **The author and contributors are not responsible for any data leakage or loss caused by device loss, system failure, malware, browser extensions, XSS attacks, or misuse.**

3.4 **Service availability risk**: **Non-public interfaces are less stable than official public APIs.** Kimi's client version, API structure, risk control policies, and rate limits **may change at any time**. The Project **does not guarantee** availability at any time, version, or network environment. **Maintainers have no obligation to fix.**

3.5 **Web-specific risk**: The Web version relies on browser CORS policy. If Kimi's server does not allow cross-origin, the Web version will fall back to "quick command mode". **The fallback does not affect the Project's "no paywall bypass" nature** (the feature itself is free). Task interruptions caused by page closure, browser crash, or system sleep are **not considered project defects**.

3.6 **Content risk**: The Project is only a "convenient trigger tool". The compliance, copyright, and accuracy of generated content **are determined by Kimi's server**. Users are responsible for their generated content and **must not** use it for illegal, infringing, false information, or deepfake purposes.

---

## 4. No Warranty

4.1 The Project is provided **"AS IS"**, **without any express or implied warranty**, including but not limited to: merchantability, fitness for a particular purpose, non-infringement, error-free, or uninterrupted operation.

4.2 The author and contributors **do not guarantee** that the Project meets any specific need, and **do not guarantee** that the Project is free of defects, vulnerabilities, or security risks.

4.3 The author and contributors **do not promise** any technical support, bug fixes, updates, or maintenance. Development and maintenance are **entirely voluntary** and may stop at any time without notice.

---

## 5. Limitation of Liability

5.1 To the maximum extent permitted by applicable law, the author and contributors **shall not be liable** for any direct, indirect, incidental, special, punitive, or consequential damages arising from the use or inability to use the Project, including but not limited to: loss of profit, data loss, business interruption, account bans, device damage, or third-party claims.

5.2 This limitation applies even if the author and contributors have been advised of the possibility of such damages.

---

## 6. Third-Party Rights

6.1 "Kimi", "Moonshot", "月之暗面" and other names, logos, and trademarks belong to their respective owners. The Project **claims no rights** to them.

6.2 "`@AI画师`" is the name of a bot in Kimi community. The Project only **references** this name to describe the operation, and **claims no rights** to it.

6.3 The Project **does not use** Kimi's official logo as the project icon, **does not use** Kimi's official promotional materials as screenshots, and **does not claim** any partnership or authorization with Kimi.

6.4 If a rights holder believes the Project infringes their rights, please contact via GitHub Issues or email. Maintainers will **handle it promptly** after verification (including but not limited to modifying, taking down, or deleting relevant content).

---

## 7. User Obligations

By using the Project, you agree to:

7.1 **Use the Project only for personal learning, research, and technical exploration**, not for any commercial purposes, bulk creation, content farms, traffic manipulation, malicious marketing, or paid boosting.

7.2 **Comply with Kimi's Terms of Service and community guidelines**, and not use the Project to generate illegal, infringing, false, deepfake, harassing, or hateful content.

7.3 **Not repackage, rename, or publish the Project on app stores or any distribution platform** for profit.

7.4 **Not use the Project for any purposes that violate local laws or regulations**.

7.5 **Not use the Project for any form of "cracking", "paywall bypass", "account sharing", or "bulk registration"** — the Project **does not provide or support** such capabilities.

7.6 **Web users additionally agree**: understand that token storage in the browser is less secure than on Android; assess risks yourself; do not enable "remember token" on public computers; do not share your token.

7.7 **Bear all consequences** of violating the above obligations.

---

## 8. Changes to This Disclaimer

8.1 Maintainers **may modify** this disclaimer at any time. Modified versions will be published in the repository **without separate notice**.

8.2 Users should **periodically review** the latest version. Continued use is deemed acceptance.

---

## 9. Miscellaneous

9.1 If any provision is held invalid or unenforceable, the remaining provisions remain in effect.

9.2 This disclaimer is governed by the laws of the **People's Republic of China** (excluding Hong Kong, Macau, and Taiwan).

9.3 If you **do not agree** with any content, please **stop using and uninstall/close** the Project immediately.

---

**If you do not understand or agree with any part of this disclaimer, please do not use the Project.**
