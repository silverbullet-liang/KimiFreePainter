
# Changelog

所有重要更改都会记录在此文件。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [SemVer](https://semver.org/lang/zh-CN/)。

## [Unreleased]

## [1.0.0] - 2026-XX-XX

### Added

#### Android
- 首次发布
- WebView 自动读取登录态
- Token 双凭证分离（accessToken + refreshToken）
- 主动刷新 + 三级恢复机制
- 后台 WebView 1x1 常驻
- 严格串行任务队列
- 429 冷却 120 秒
- 作品帖识别（水位线 + 三重校验 + 评论区反查兜底）
- 可配置删帖模式（只删触发帖 / 不删 / 全删）
- 历史记录（Room + 本地图片）
- 图片保存到相册
- 旧版 Material You（material3 1.3.1）
- Dynamic Color（Android 12+）+ Kimi 蓝兜底
- 前台 Service 保活
- 任务状态通知

#### Web
- 首次发布
- 手动填写 token（Bookmarklet 辅助）
- 临时会话 / 持久化两种模式
- 3 tab 界面（生图 / 历史 / 设置）
- IndexedDB 存储图片
- 浏览器下载保存
- CORS 降级模式
- GitHub Pages 自动部署

#### 文档
- 完整接口研究文档（69 服务 / 260 RPC / 909 消息 / 158 枚举）
- 免责声明（中英双版）
- 隐私声明（中英双版）
- 设计文档
- 架构文档
- 使用说明（Android / Web）
- FAQ

#### 图标
- 主视觉（画笔 + 星芒，抽象几何）
- Adaptive Icon 三层（Background / Foreground / Monochrome）
- 全密度输出
- Web favicon / PWA icon
- GitHub 仓库 Avatar

### Security
- Token 加密存储（Android Keystore）
- Web 端默认临时会话
- 不向任何第三方域名发起请求
