
# Changelog

所有重要更改都会记录在此文件。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [SemVer](https://semver.org/lang/zh-CN/)。

## [Unreleased]

## [1.4.0] - 2026-11-01

### Fixed

#### 通用
- 带图发帖：`CreateMoment.images` 的元素是 `ImageInfo`，此前只传 `{fileId}` 导致被拒；
  现按文档补齐 `fileId` + `originUrl` + `thumbnailUrl` + `extra{width,height}`
- 预签名 PUT 不再强制带 Content-Type（签名含该头时会 403），失败后才带 `image/*` 重试
- 带图被拒时自动降级为纯文字重发，并在界面写明原因，不再出现「点了没反应」
- 任务失败与取消以前既不进进行中也不进已完成，直接消失；现单列「未完成」区并显示错误原因

#### Android
- 取消按钮对所有进行中状态生效（原来只对排队态有效），运行中也能停
- 设置页版本号改为读真实版本（原来硬编码 1.0.0），双击可检查更新：
  读 GitHub 最新 Release，有新版自动下载 APK 并拉起系统安装器

#### Web
- 等待生成时可「停止」，不再只能干等 5 分钟
- 版本号来自构建注入，双击检查更新，有新版本自动刷新页面并给出 APK 地址
- 发帖后回查动态是否存在、正文 @ 是否生效，失败给出红色提示

## [1.3.0] - 2026-11-01

### Changed

#### 通用
- 登录方式统一为扫码：App 内直接生成二维码并轮询登录码状态，
  令牌由服务端直接下发，不再从网页里读取，也不再提供手填令牌的入口。
  普通用户只需用 Kimi App 扫一下，不必懂什么是 access_token。

#### Android
- 移除内置 WebView 登录页（原方案要打开官网并抠 localStorage，受站点改版与白屏影响）
- 新增扫码登录对话框：本地生成二维码，1.2 秒轮询一次，支持过期刷新
- 登录态改为可订阅状态，扫码成功后首页与设置页立刻同步

#### Web
- 移除「登录态」手动填写区，只保留扫码登录
- 顶栏常驻显示当前账号与登录状态
- access 到期自动用刷新态续期，续不上视为掉线

## [1.2.0] - 2026-11-01

### Added

#### 通用
- 带图发帖（改图）：选一张底图随动态发出，画师以它为底图重绘。
  平台一条动态只接受一张，两端都按单张实现。
  上传链路：GeneratePresignedURL 申请直传地址 → PUT 到云存储 → CreateFile 登记 → 发帖时 images 带 fileId

#### Web
- 输入区「添加图片（改图）」，支持预览与移除

#### Android
- 输入区「选择底图（改图）」，走系统照片选择器，选完即复制到任务目录（相册临时授权在后台不可靠）

## [1.1.0] - 2026-11-01

### Fixed

#### 通用
- 结果只显示提示词、看不到简介：成品帖正文是「简介 + @画师 提示词 + —— 昵称」，
  现在按本次提示词逐行剥离，只留简介，不依赖固定的画师名和昵称
- 触发帖正文没带 @，机器人收不到（只有 mentions 字段不生效），改为正文前置 @画师

#### Web
- 成品帖识别：新增 refMomentId 配对判据，列表不带图时回查详情
- 机器人评论兜底：链接在富文本 links 字段里，不再从正文正则里找 URL
- 提示词取消 500 字上限，输入框随内容增高

#### Android
- 登录页白屏：去掉 WebView 的 "; wv" UA 标记与自定义后缀，补 WebChromeClient
- 微信等第三方登录跳转交给系统处理
- 加载失败时给出具体原因，不再空白
- 登录态读取改为扫描全部 JWT，官网改字段名也能读到
- 提示词取消 500 字上限
- 结果卡片与历史详情分别展示标题、简介、提示词

### Added

#### Web
- 扫码登录：Kimi App 扫二维码直取令牌，Worker 心跳避免后台标签页节流

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
