
# 隐私声明 / Privacy Policy

**最后更新：2026-10-30**

KimiFreePainter（含 Android 端与 Web 端，以下简称“本项目”）**高度重视用户隐私**。本声明说明本项目如何处理您的数据。

---

## 1. 我们不收集任何数据

1.1 本项目**没有**任何后端服务器、数据库、分析服务、埋点 SDK、崩溃上报 SDK。

1.2 本项目**不收集**、**不上传**、**不共享**、**不出售**您的任何个人信息、使用数据、设备信息、创作内容。

1.3 本项目**不包含**任何第三方广告 SDK、统计 SDK、推送 SDK。

1.4 **Web 端特别说明**：Web 端部署在 GitHub Pages（静态托管），**作者无法访问**任何用户数据。所有数据均在用户浏览器本地。

---

## 2. 数据存储位置

### 2.1 Android 端

| 数据 | 存储位置 | 加密 |
|---|---|---|
| accessToken / refreshToken | EncryptedSharedPreferences（Android Keystore） | ✅ 加密 |
| 创作历史元数据 | Room 数据库（App 私有目录） | ❌ 明文（沙箱保护） |
| 图片 | `filesDir/history/` | ❌ 明文（沙箱保护） |
| 设置项 | DataStore | ❌ 明文（沙箱保护） |

App 私有目录受 Android 沙箱保护，其他 App 无法访问。

### 2.2 Web 端

| 数据 | 存储位置 | 加密 |
|---|---|---|
| accessToken / refreshToken | **临时会话**：内存（刷新即丢）<br>**记住 token**：localStorage | ❌ 明文 |
| 创作历史元数据 | localStorage | ❌ 明文 |
| 图片 | IndexedDB（Blob） | ❌ 明文 |
| 设置项 | localStorage | ❌ 明文 |

**Web 端安全警告**：localStorage 中的数据可被同源 JavaScript、浏览器扩展、XSS 攻击读取。默认使用「临时会话」模式，开启「记住 token」需用户主动确认风险。

---

## 3. 网络请求说明

3.1 本项目运行时会向以下域名发起 HTTPS 请求，**用于调用 Kimi 客户端内部 RPC 接口**（静态分析获得，非官方公开 API）：

- `www.kimi.com`（主 API，`/apiv2` 前缀）
- `auth.kimi.com`（鉴权，`/api` 前缀）
- `kimi-img.moonshot.cn`（图片 CDN，仅下载）

3.2 请求头中会携带设备标识（`x-msh-device-id` 等），**仅用于 Kimi 服务端识别设备**，本项目不另行收集或上传。

3.3 本项目**不向任何第三方域名**发起请求（GitHub Pages 静态资源除外）。

3.4 **Web 端 CORS 说明**：Web 端请求受浏览器 CORS 策略约束。若 Kimi 服务端未开放跨域，Web 端将降级为「快捷指令模式」（生成控制台快捷操作，用户在 kimi.com 页面手动执行）。

---

## 4. WebView 说明（仅 Android 端）

4.1 Android 端在后台持有 Kimi 官方 WebView，**仅用于读取登录态**。

4.2 WebView 加载的内容**完全来自 kimi.com**，本项目不注入任何第三方脚本、不修改页面内容、不拦截用户输入。

4.3 WebView 的 Cookie 与 localStorage 由 Android WebView 组件与 kimi.com 自行管理，本项目**不读取、不导出、不上传**除 token 外的任何 WebView 数据。

---

## 5. 权限说明（仅 Android 端）

| 权限 | 用途 | 必需 |
|---|---|---|
| `INTERNET` | 调用 API | ✅ |
| `WRITE_EXTERNAL_STORAGE`（仅 Android 9-） | 保存图片到相册 | 保存功能需要 |
| `FOREGROUND_SERVICE` | 创作任务保活 | 任务运行需要 |
| `POST_NOTIFICATIONS`（Android 13+） | 任务状态通知 | 可选 |

**不申请**：位置、通讯录、相机、麦克风、短信、通话记录、日历、传感器等任何敏感权限。

**Web 端不申请任何浏览器权限**（除剪贴板写入，需 HTTPS，GitHub Pages 已满足）。

---

## 6. 数据删除

- **Android**：设置页「清空历史」、「退出登录」；卸载 App 清除所有数据。
- **Web**：设置页「清空所有数据」（清 IndexedDB + localStorage + 内存）；清除浏览器站点数据也会清除。

---

## 7. 儿童隐私

本项目不面向 14 周岁以下儿童。如您是未成年人，请在监护人指导下使用。

---

## 8. 隐私声明的变更

维护者**有权随时修改**本声明，修改后将在仓库发布。继续使用即视为接受。

---

## 9. 联系方式

如对本隐私声明有疑问，请通过 GitHub Issues 联系。
