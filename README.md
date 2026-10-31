
# KimiFreePainter

> Kimi 免费画师 · 更快捷、更美观、更方便地使用 Kimi 社区 `@AI画师` 免费生图
> A cleaner, faster, prettier way to use Kimi's free `@AI画师` image generation.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Docs: CC BY-NC-SA 4.0](https://img.shields.io/badge/Docs-CC%20BY--NC--SA%204.0-lightgrey.svg)](docs/API_REVERSE_ENGINEERING.md)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-purple.svg)]()
[![Compose](https://img.shields.io/badge/Compose-MD3%201.3.x-4285F4.svg)]()
[![Web](https://img.shields.io/badge/Web-GitHub%20Pages-orange.svg)]()

---

## ⚠️ 重要提示（请务必先读）

- **本项目调用的是非公开接口，不是 Kimi 官方 OpenAPI。**
  通过对 Kimi Android 客户端进行静态分析获得内部 RPC 接口，
  未获得官方授权，可能随时失效，**使用可能与 Kimi 服务条款存在差异**。

- **本项目使用的 `@AI画师` 生图功能，是平台免费提供的，不消耗任何额度。**
  不绕过付费、不薅额度、不共享账号——因为该功能本身就是免费的。

- **本项目仅供个人学习、研究与技术探索。**
  请勿用于商业用途、连续创作、内容农场、破解绕过等场景。

- **账号风险自担。**
  因使用本项目导致的账号警告、限制、封禁等后果，作者与贡献者**不承担任何责任**。

- **不收集、不上传任何用户数据。**
  所有数据仅保存在本机 / 浏览器本地。

- **完整条款请阅读 [DISCLAIMER.md](DISCLAIMER.md) 与 [PRIVACY.md](PRIVACY.md)。**

---

## ✨ 特性

- 🆓 **彻底免费**：`@AI画师` 是平台免费功能，不消耗任何额度
- ⚡ **更快捷**：7 步 → 4 步，同一界面完成
- 🎨 **更美观**：旧版 Material You 重做，克制清爽
- 📱 **Android 端**：WebView 自动登录 + 后台等待 + 相册保存
- 🌐 **Web 端**：GitHub Pages 在线试用，手动填 token
- 📚 **完整接口研究文档**：69 服务 / 260 RPC / 909 消息 / 158 枚举
- 🔒 **隐私优先**：数据仅本地，不联网上传

---

## 🌐 在线试用（Web 版）

**无需安装，打开即用**：[https://silverbullet-liang.github.io/KimiFreePainter/](https://silverbullet-liang.github.io/KimiFreePainter/)

> ⚠️ Web 版需手动填写 token（附一键提取 Bookmarklet），功能较 Android 版精简。
> 详见 [Web 版说明](docs/WEB.md)。

---

## 📱 Android 版

### 安装

1. 从 [Releases](https://github.com/silverbullet-liang/KimiFreePainter/releases) 下载最新 APK
2. 安装（需允许“未知来源”）
3. 打开 App → 设置页 → 登录（内嵌 WebView）
4. 生图页输入提示词 → 开始创作

### 从源码构建

```bash
git clone https://github.com/silverbullet-liang/KimiFreePainter.git
cd KimiFreePainter
gradle assembleDebug
```

要求：Android Studio Hedgehog+ / JDK 17 / Android SDK 35。

产物：`app/build/outputs/apk/debug/app-debug.apk`。

---

## 📚 接口研究文档

完整文档：[docs/API_REVERSE_ENGINEERING.md](docs/API_REVERSE_ENGINEERING.md)

- 分析方法：自研纯 Python DEX 解析器，静态分析
- 覆盖范围：69 服务 / 260 RPC 方法 / 909 protobuf 消息 / 158 枚举
- 协议：CC BY-NC-SA 4.0（署名、非商用、相同方式共享）

---

## 🏗️ 工作原理

```
用户输入提示词
  → 发帖 @AI画师（CreateMoment）
  → Kimi 服务端免费润色 + 生图
  → 以用户身份发布新动态（含图片与描述）
  → 客户端轮询识别到作品帖（ListFeeds）
  → 下载图片 → 可选删帖 → 展示结果
```

> 注：`@AI画师` 是 Kimi 社区中该 bot 的**中文名称**，不可翻译。

---

---

## ⚖️ 合规与免责

- **非官方产品**，与月之暗面科技无关联
- `@AI画师` 是平台免费功能，本项目**不绕过付费、不消耗额度**
- 使用可能与 Kimi 服务条款存在差异，**账号风险自担**
- 禁止商业用途、连续创作、二次打包收费
- 完整条款见 [DISCLAIMER.md](DISCLAIMER.md)

---

## 📄 开源协议

- 代码：[GPL-3.0](LICENSE)
- 文档：[CC BY-NC-SA 4.0](docs/API_REVERSE_ENGINEERING.md)

代码防止二次打包收费；文档防止被抄去卖钱。两者授权范围不同，请分别遵守。

---

## 🙏 致谢

- 感谢 [Kimi](https://kimi.com) 提供优质的免费 `@AI画师` 生图能力
- 感谢所有 [Contributors](https://github.com/silverbullet-liang/KimiFreePainter/graphs/contributors)

---

## ⭐ Star History

[![Star History Chart](https://api.star-history.com/svg?repos=silverbullet-liang/KimiFreePainter&type=Date)](https://star-history.com/#silverbullet-liang/KimiFreePainter&Date)
