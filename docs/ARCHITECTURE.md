
# 架构说明

## 1. 整体分层

```
┌──────────────────────────────────────────────────────┐
│  UI 层（Compose / React）                              │
│  MainActivity（Android）                              │
│   ├─ 底层：1x1 后台 WebView（常驻，被 Content 覆盖）    │
│   └─ 上层：Scaffold + NavigationBar（3 tab）           │
│        ├─ 生图页                                       │
│        ├─ 历史页 + 详情                                 │
│        └─ 设置页（含全屏登录 Dialog）                   │
├──────────────────────────────────────────────────────┤
│  业务层                                                │
│  ViewModel（StateFlow）                                │
│  PainterRepo（发帖/轮询/识别/下载/删帖）                │
│  TaskQueue（串行调度 + 429 冷却）                       │
│  PaintForegroundService（保活）                        │
│  AuthManager（Token 双凭证 + 三级恢复）                 │
├──────────────────────────────────────────────────────┤
│  数据层                                                │
│  KimiApi（OkHttp + AuthInterceptor）                    │
│  TokenStorage（EncryptedSharedPreferences）             │
│  Room（历史）+ DataStore（设置）                         │
│  文件目录（图片）                                       │
└──────────────────────────────────────────────────────┘
```

## 2. 模块职责

| 模块 | 职责 | 依赖 |
|---|---|---|
| AuthManager | Token 生命周期 + 三级恢复调度 | TokenStorage, BackgroundWebView |
| KimiApi | OkHttp 封装 + 401 拦截 | AuthManager |
| PainterRepo | 发帖/轮询/识别/下载/删帖 | KimiApi, TaskQueue |
| TaskQueue | 串行调度 + 429 冷却 + 队列上限 | PainterRepo |
| PaintForegroundService | 保活 + 通知 + Room 恢复 | PainterRepo |
| BackgroundWebView | WebView 封装 + token 回调 | AuthManager |
| TokenStorage | EncryptedSharedPreferences 双 key | — |

## 3. 数据流

```
用户输入 → UI → ViewModel → PainterRepo → TaskQueue → KimiApi → 服务端
                                  ↓
                              Room（历史）
                                  ↓
                              StateFlow → UI 更新
```

## 4. 关键设计决策

| 决策 | 理由 |
|---|---|
| Token 双凭证分离 | accessToken 短命可换，refreshToken 长命是根凭证 |
| 主动刷新优先 | 避免长任务中途 401 |
| 严格串行队列 | 429 天然满足 + 作品帖识别不串 |
| 后台 WebView 1x1 常驻 | 保证 token 兜底随时可用，但不能 GONE |
| 进度用时间估算 | 平台无真实进度接口，假精确会焦虑 |
| 历史存本地图片 | CDN URL 会过期，历史需长期可回看 |
| Web 端降级模式 | CORS 不可控，保证 Web 端永远可用 |
