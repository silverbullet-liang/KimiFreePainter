
# KimiFreePainter 设计文档

> 本文件是项目的完整设计文档，涵盖产品、技术、UI、数据、异常处理。

---

## 1. 产品定位

更快捷、更美观、更方便地使用 Kimi 社区 `@AI画师` 免费生图。

- 不消耗任何额度（平台功能本身免费）
- 7 步 → 4 步
- 旧版 Material You 重做

---

## 2. 双端形态

| 维度 | Android 端 | Web 端 |
|---|---|---|
| 部署 | GitHub Release（APK） | GitHub Pages |
| 登录 | 扫码（App 内生成二维码，轮询 auth 网关） | 扫码（同一套网关，浏览器内轮询） |
| Token 存储 | EncryptedSharedPreferences | localStorage（默认临时会话） |
| 后台任务 | Foreground Service | 无 |
| 自动删帖 | ✅ 可配置 | ❌ 手动 |
| 相册保存 | ✅ MediaStore | 浏览器下载 |
| 历史存储 | Room + 本地文件 | IndexedDB + localStorage |
| 系统通知 | ✅ | ❌ |

---

## 3. Android 端设计

### 3.1 技术选型

| 项 | 选择 |
|---|---|
| 语言 | Kotlin 1.9+ |
| UI | Jetpack Compose，material3 1.3.1 |
| Compose BOM | 2024.10.00 |
| 主题入口 | MaterialTheme { } |
| 网络 | OkHttp 4.12 + Gson |
| 异步 | Coroutines + Flow |
| 本地 | Room 2.6.1 + DataStore 1.1.1 |
| Token | EncryptedSharedPreferences |
| 图片 | Coil Compose 2.7.0 |
| 后台 | Foreground Service |
| minSdk / targetSdk | 26 / 35 |

### 3.2 主题

| 项 | 决策 |
|---|---|
| 配色 | Dynamic Color（12+）+ Kimi 蓝 #1F71FF 兜底（11-） |
| 深色模式 | 跟随系统，卡片用 surfaceContainerLow |
| 形状 | 标准 4/8/12/16/28 dp |
| 动效 | 标准缓动 |
| 留白 | 页 16dp / 卡 12dp / 组 24dp |

### 3.3 架构分层

```
UI（Compose）→ ViewModel（StateFlow）→ Repo/Service → Api/DB/Storage
```

核心组件：AuthManager、KimiApi、PainterRepo、TaskQueue、PaintForegroundService、BackgroundWebView、TokenStorage。

### 3.4 Token 双凭证模型

| 维度 | accessToken | refreshToken |
|---|---|---|
| 角色 | 工作凭证 | 根凭证 |
| 有效期 | 15 分钟 | 90 天 |
| 存储 key | access_token | refresh_token |
| 失效处理 | 静默换新，不弹窗 | WebView 兜底 → 手动登录，弹窗 |
| 清除 | 单独清 | 登出时都清 |

### 3.5 请求前决策

```
ensureFreshToken()：
  accessToken 剩余 > 5min → 直接用
  accessToken 剩余 < 5min 或无 → 加锁 + 双检 → refreshToken 换新
  refreshToken 失效 → WebView 兜底 → 手动登录
```

触发时机：启动首次请求前 / 发帖前 / 轮询每轮前 / 展示账号前 / 401 后。

### 3.6 三级恢复

```
401 → A. 主动刷新 → 成功重放
      失败 → B. WebView reload → 成功重放
              失败 → C. 弹窗「登录态失效」→ 设置页登录 → 自动重试
```

### 3.7 后台 WebView 约束

| 约束 | 实现 |
|---|---|
| 可见但不可见 | 1dp×1dp，alpha=0.01，被覆盖。绝不用 GONE |
| 只允许 kimi.com | shouldOverrideUrlLoading 拦截 |
| 主动读 | onPageFinished 后主动 evaluateJavascript |
| 崩溃重建 | onRenderProcessGone → 重建 |
| 内存回收 | TRIM_MEMORY_UI_HIDDEN 不清；COMPLETE 允许重建 |
| Cookie 独立 | 与系统浏览器隔离 |

### 3.8 创作状态机

```
[CANCELLED] ← 用户取消
     ↑
[QUEUED] ← 用户点「开始创作」
     ↓
[POSTING] CreateMoment(text=提示词, mentions=[@AI画师])
     ↓
[GENERATING] 每 10 秒 ListFeeds(USER_PUBLISH)
     ↓
[DOWNLOADING] 下载 originUrl
     ↓
[CLEANING] 可选删帖
     ↓
[DONE] → 写历史 → 通知 UI

任意阶段 ──失败──→ [FAILED]（可重试）
```

状态与 UI 映射：

| 状态 | 文案 | 进度 |
|---|---|---|
| QUEUED | 排队中（前面还有 N 个） | 0% |
| POSTING | 正在提交… | 10% |
| GENERATING | 画师正在创作… 已等待 42s | 20–80% 时间估算 |
| DOWNLOADING | 正在下载… | 90% |
| CLEANING | 正在清理… | 95% |
| DONE | 完成 | 100% |
| FAILED | 失败（附原因） | — |

### 3.9 作品帖识别

1. 发帖前：拉一次 ListFeeds(USER_PUBLISH)，记录最新 createTime 作为水位线。
2. 发帖后：轮询只认满足全部条件的 moment：
   - createTime > 水位线
   - content.source == "MOMENT_SOURCE_AI"
   - author.type == "AUTHOR_TYPE_USER"
   - author.userId == 当前登录 userId
   - content.media.images 非空
   - momentId ∉ seenIds
3. 兜底：5 分钟没识别到 → 评论区反查。

### 3.10 任务队列

| 决策 | 选择 |
|---|---|
| 并发度 | 1（严格串行） |
| 429 处理 | 读 Retry-After，否则 120 秒 |
| 连续 3 次 429 | 10 分钟 + 提示 |
| 队列上限 | 10 |
| 单任务超时 | 5 分钟 |
| 轮询间隔 | 10 秒 |

### 3.11 删帖策略

| 模式 | 行为 | 默认 |
|---|---|---|
| 只删触发帖 | DeleteMoment(triggerMomentId) | ✅ |
| 不删 | 触发帖 + 作品帖都保留 | — |
| 全删 | 触发帖 + 作品帖 + 画师评论 | 延迟 30–60 秒 |

### 3.12 UI 页面

- 生图页：OutlinedTextField + Button + 任务卡片 + 作品卡片
- 历史页：LazyColumn + ListItem + SwipeToDismissBox
- 历史详情页：独立页面 + 全屏图 + 描述 + 保存按钮
- 设置页：ListItem + Switch + AlertDialog
- 全屏登录 Dialog：Dialog(usePlatformDefaultWidth=false) + 复用 WebView

### 3.13 数据模型

Room painter_task：id / prompt / status / triggerMomentId / resultMomentId / description / imageUrls / localPaths / errorMsg / createdAt / finishedAt / deletedFromCommunity。

文件：filesDir/history/<taskId>/{img_N.jpg, thumb_N.jpg}。

### 3.14 设置项

- 账号：头像+昵称 / 登录 / 退出 / 记住登录
- 创作：删帖模式 / 删帖延迟 / 轮询间隔 / 超时 / 画师 bot
- 历史：自动清理 / 清空历史
- 关于：版本 / 协议 / 隐私 / 免责

### 3.15 异常处理

| 异常 | UI | 恢复 |
|---|---|---|
| accessToken 过期 | 无感 | 主动刷新重放 |
| refreshToken 失效 | 弹窗 | 去设置登录 |
| 429 | 倒计时 | 冷却后继续 |
| 网络断 | 等待网络 | 恢复后继续 |
| 创作超时 | FAILED | 评论区反查兜底 |
| 下载失败 | 自动重试 3 次 | 手动重试 |
| 删帖失败 | 标记清理失败 | 手动重删 |
| Service 被杀 | 通知消失 | START_STICKY + Room 恢复 |

---

## 4. Web 端设计

### 4.1 技术栈

Vite 5 + React 18 + TypeScript + Zustand + HashRouter + 纯 CSS + IndexedDB/localStorage。

### 4.2 页面

3 tab：生图 / 历史 / 设置。无独立登录页。

### 4.3 Token 处理

- 获取：扫码登录（`auth.kimi.com` 网关，免鉴权申请登录码 → 轮询状态 → 服务端下发令牌）
- 存储：localStorage，仅本机
- 刷新：每 60 秒检查 access 是否过期，过期用 refreshToken 换新；401 时也会即时刷新并重放

### 4.4 创作流程

输入提示词 → 检查 token → 记录水位线 → CreateMoment → 轮询 → 识别 → 下载 → 存 IndexedDB → 展示 → 可选删帖。

### 4.5 CORS 降级

CORS 不开放 → 降级「快捷指令模式」，生成控制台快捷操作供用户在 kimi.com 执行。

### 4.6 数据存储

IndexedDB：tasks / images / thumbs。
localStorage：kfp.token / kfp.settings / kfp.pendingTask。

### 4.7 部署

web-deploy.yml 自动构建部署到 GitHub Pages，HashRouter，base=/KimiFreePainter/。
